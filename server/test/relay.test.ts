import { test, before, after } from "node:test";
import assert from "node:assert/strict";
import { AddressInfo } from "node:net";
import { WebSocket } from "ws";
import { createServer, type PactServer } from "../src/server.ts";
import { loadConfig } from "../src/config.ts";
import type { SeatCredentials, ServerFrame } from "../src/protocol.ts";

let srv: PactServer;
let base: string;

before(async () => {
  const cfg = { ...loadConfig(), port: 0, publicUrl: "http://127.0.0.1:0" };
  srv = createServer(cfg);
  await new Promise<void>((resolve) => srv.http.listen(0, "127.0.0.1", resolve));
  const port = (srv.http.address() as AddressInfo).port;
  base = `http://127.0.0.1:${port}`;
});

after(async () => {
  await srv.close();
});

async function post(path: string, body: unknown): Promise<{ status: number; json: any }> {
  const res = await fetch(base + path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  return { status: res.status, json: await res.json() };
}

// Open a socket for a seat; expose a way to await the next frame of a type.
function connect(creds: SeatCredentials): {
  ws: WebSocket;
  next: (type: ServerFrame["type"]) => Promise<any>;
  ready: Promise<void>;
} {
  // The wsUrl is built from the (placeholder) publicUrl; rewrite to our port.
  const url = creds.wsUrl.replace(/^ws:\/\/[^/]+/, base.replace(/^http/, "ws"));
  const ws = new WebSocket(url);
  const queue: ServerFrame[] = [];
  const waiters: Array<{ type: string; resolve: (f: any) => void }> = [];
  ws.on("message", (raw) => {
    const frame = JSON.parse(raw.toString()) as ServerFrame;
    const i = waiters.findIndex((w) => w.type === frame.type);
    if (i >= 0) waiters.splice(i, 1)[0]!.resolve(frame);
    else queue.push(frame);
  });
  const next = (type: ServerFrame["type"]) =>
    new Promise<any>((resolve) => {
      const i = queue.findIndex((f) => f.type === type);
      if (i >= 0) resolve(queue.splice(i, 1)[0]);
      else waiters.push({ type, resolve });
    });
  const ready = new Promise<void>((resolve, reject) => {
    ws.on("open", () => resolve());
    ws.on("error", reject);
  });
  return { ws, next, ready };
}

test("health reports liveness and push status", async () => {
  const res = await fetch(base + "/health");
  assert.equal(res.status, 200);
  const body = await res.json();
  assert.equal(body.ok, true);
  assert.deepEqual(Object.keys(body.push).sort(), ["android", "ios"]);
});

test("create requires a host name", async () => {
  const { status } = await post("/pacts", {});
  assert.equal(status, 400);
});

test("full flow: create, join, lock, ask, unanimous grant", async () => {
  const create = await post("/pacts", { hostName: "Yousef", passMinutes: 5, stakes: "Dessert" });
  assert.equal(create.status, 201);
  const host = create.json as SeatCredentials;
  assert.match(host.code, /^TBL-/);
  assert.match(host.joinLink, /^pact:\/\/join\?code=/);

  // Guests join by code.
  const j1 = await post(`/pacts/${encodeURIComponent(host.code)}/join`, { name: "Maya" });
  const j2 = await post(`/pacts/${encodeURIComponent(host.code)}/join`, { name: "Omar" });
  assert.equal(j1.status, 201);
  assert.equal(j2.status, 201);
  const maya = j1.json as SeatCredentials;
  const omar = j2.json as SeatCredentials;
  assert.equal(maya.pactId, host.pactId);

  // All three open live screens.
  const cy = connect(host), cm = connect(maya), co = connect(omar);
  await Promise.all([cy.ready, cm.ready, co.ready]);
  // Each gets a welcome frame.
  const wy = await cy.next("welcome");
  assert.equal(wy.view.screen, "lobby-host");

  // Host locks (over the socket).
  cy.ws.send(JSON.stringify({ type: "lock" }));
  const lockState = await cy.next("state");
  assert.equal(lockState.view.screen, "night");

  // Host asks the table.
  cy.ws.send(JSON.stringify({ type: "ask", reason: "Need to pay" }));
  // Maya should receive an "ask" view.
  let mv = await cm.next("state");
  while (mv.view.screen !== "ask") mv = await cm.next("state");
  assert.equal(mv.view.asker, "Yousef");
  assert.equal(mv.view.reason, "Need to pay");

  // Maya votes yes via the socket, Omar votes yes via REST (the push path).
  cm.ws.send(JSON.stringify({ type: "vote", allow: true }));
  const omarAct = await post(`/pacts/${omar.pactId}/actions`, {
    seatId: omar.seatId,
    token: omar.token,
    action: { type: "vote", allow: true },
  });
  assert.equal(omarAct.status, 200);
  assert.equal(omarAct.json.ok, true);

  // Host should land on the pass screen.
  let hv = await cy.next("state");
  while (hv.view.screen !== "pass") hv = await cy.next("state");
  assert.equal(hv.view.emergency, false);

  for (const c of [cy, cm, co]) c.ws.close();
});

test("a single no denies and starts the asker's cooldown", async () => {
  const create = await post("/pacts", { hostName: "Ana" });
  const host = create.json as SeatCredentials;
  const join = await post(`/pacts/${encodeURIComponent(host.code)}/join`, { name: "Beni" });
  const beni = join.json as SeatCredentials;
  const act = (c: SeatCredentials, action: unknown) =>
    post(`/pacts/${c.pactId}/actions`, { seatId: c.seatId, token: c.token, action });

  await act(host, { type: "lock" });
  await act(host, { type: "ask", reason: "Work" });
  const deny = await act(beni, { type: "vote", allow: false });
  assert.equal(deny.json.ok, true);
  // The host is now cooling down and cannot re-ask immediately.
  const reask = await act(host, { type: "ask", reason: "Again" });
  assert.equal(reask.json.ok, false);
  assert.equal(reask.json.view.screen, "night");
  assert.equal(reask.json.view.canAsk, false);
});

test("joining a locked table is refused", async () => {
  const create = await post("/pacts", { hostName: "Sam" });
  const host = create.json as SeatCredentials;
  await post(`/pacts/${encodeURIComponent(host.code)}/join`, { name: "Lee" });
  await post(`/pacts/${host.pactId}/actions`, { seatId: host.seatId, token: host.token, action: { type: "lock" } });
  const late = await post(`/pacts/${encodeURIComponent(host.code)}/join`, { name: "Late" });
  assert.equal(late.status, 409);
});

test("unauthorized actions are rejected", async () => {
  const create = await post("/pacts", { hostName: "Kai" });
  const host = create.json as SeatCredentials;
  const bad = await post(`/pacts/${host.pactId}/actions`, {
    seatId: host.seatId,
    token: "wrong",
    action: { type: "lock" },
  });
  assert.equal(bad.status, 401);
});

test("websocket upgrade is rejected without a valid token", async () => {
  const create = await post("/pacts", { hostName: "Noa" });
  const host = create.json as SeatCredentials;
  const badUrl = host.wsUrl.replace(/token=[^&]+/, "token=nope").replace(/^ws:\/\/[^/]+/, base.replace(/^http/, "ws"));
  const ws = new WebSocket(badUrl);
  await new Promise<void>((resolve) => {
    ws.on("error", () => resolve()); // 401 surfaces as an error on the client
    ws.on("open", () => {
      ws.close();
      resolve();
    });
  });
  assert.equal(ws.readyState === WebSocket.OPEN, false);
});
