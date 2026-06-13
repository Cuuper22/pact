import http from "node:http";
import { URL } from "node:url";
import { WebSocketServer, type WebSocket } from "ws";
import type { Config } from "./config.js";
import { Notifier } from "./push/index.js";
import { PactStore } from "./store.js";
import type { Pact, Connection } from "./pact.js";
import type {
  ActionBody,
  ClientFrame,
  CreatePactBody,
  JoinPactBody,
  SeatCredentials,
  ServerFrame,
} from "./protocol.js";

const MAX_NAME = 24;
const VALID_PASS = new Set([2, 5, 10]);

export interface PactServer {
  http: http.Server;
  store: PactStore;
  close: () => Promise<void>;
}

export function createServer(cfg: Config): PactServer {
  const notifier = new Notifier(cfg);
  const store = new PactStore(cfg, notifier);
  store.start();

  const server = http.createServer((req, res) => {
    handleHttp(req, res, cfg, store).catch((err) => {
      console.error("[http] unhandled", err);
      json(res, 500, { error: "internal" });
    });
  });

  // ---- WebSocket: a live screen watching one seat's view ----
  const wss = new WebSocketServer({ noServer: true });
  server.on("upgrade", (req, socket, head) => {
    const url = new URL(req.url ?? "/", "http://x");
    if (url.pathname !== "/ws") {
      socket.destroy();
      return;
    }
    const pactId = url.searchParams.get("pactId") ?? "";
    const seatId = url.searchParams.get("seatId") ?? "";
    const token = url.searchParams.get("token") ?? "";
    const pact = store.get(pactId);
    if (!pact || !pact.authorize(seatId, token)) {
      socket.write("HTTP/1.1 401 Unauthorized\r\n\r\n");
      socket.destroy();
      return;
    }
    wss.handleUpgrade(req, socket, head, (ws) => {
      attachSocket(ws, pact, seatId);
    });
  });

  return {
    http: server,
    store,
    close: () =>
      new Promise<void>((resolve) => {
        store.stop();
        wss.close();
        server.close(() => resolve());
      }),
  };
}

function attachSocket(ws: WebSocket, pact: Pact, seatId: string): void {
  const conn: Connection = {
    send: (frame: ServerFrame) => {
      if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(frame));
    },
  };
  const detach = pact.attach(seatId, conn);

  ws.on("message", (raw) => {
    let frame: ClientFrame;
    try {
      frame = JSON.parse(raw.toString());
    } catch {
      conn.send({ type: "error", code: "bad_json", message: "malformed frame" });
      return;
    }
    if (frame.type === "ping") {
      conn.send({ type: "pong", serverTime: Date.now() });
      return;
    }
    if (frame.type === "hello") {
      // Already authorized on upgrade; a hello just re-syncs the view.
      conn.send({ type: "state", serverTime: Date.now(), view: pact.engine.viewFor(seatId) });
      return;
    }
    // Everything else is an action authored by this seat.
    pact.applyAction(seatId, frame);
  });

  ws.on("close", () => detach());
  ws.on("error", () => detach());
}

// ---------- REST ----------

async function handleHttp(
  req: http.IncomingMessage,
  res: http.ServerResponse,
  cfg: Config,
  store: PactStore
): Promise<void> {
  const url = new URL(req.url ?? "/", "http://x");
  const path = url.pathname;
  const method = req.method ?? "GET";

  cors(res);
  if (method === "OPTIONS") {
    res.writeHead(204).end();
    return;
  }

  if (method === "GET" && path === "/health") {
    json(res, 200, { ok: true, pacts: store.size, push: notifierStatus(cfg) });
    return;
  }

  if (method === "POST" && path === "/pacts") {
    const body = (await readJson(req)) as CreatePactBody;
    const name = cleanName(body?.hostName);
    if (!name) return json(res, 400, { error: "hostName required" });
    const passMinutes = VALID_PASS.has(body?.passMinutes as number) ? body.passMinutes : 5;
    const stakes = typeof body?.stakes === "string" ? body.stakes.slice(0, 120) : undefined;
    const pact = store.create({ passMinutes, stakes });
    const { seatId, token } = pact.addSeat(name, true);
    if (body?.pushToken && body?.platform) {
      pact.applyAction(seatId, { type: "setPush", pushToken: body.pushToken, platform: body.platform });
    }
    json(res, 201, credentials(cfg, pact, seatId, token));
    return;
  }

  // POST /pacts/:code/join
  let m = path.match(/^\/pacts\/([^/]+)\/join$/);
  if (method === "POST" && m) {
    const code = decodeURIComponent(m[1]!);
    const pact = store.byJoinCode(code);
    if (!pact) return json(res, 404, { error: "no such table" });
    if (pact.engine.status !== "lobby") return json(res, 409, { error: "table already locked" });
    const body = (await readJson(req)) as JoinPactBody;
    const name = cleanName(body?.name);
    if (!name) return json(res, 400, { error: "name required" });
    const { seatId, token } = pact.addSeat(name, false);
    if (body?.pushToken && body?.platform) {
      pact.applyAction(seatId, { type: "setPush", pushToken: body.pushToken, platform: body.platform });
    }
    json(res, 201, credentials(cfg, pact, seatId, token));
    return;
  }

  // POST /pacts/:pactId/actions  — act from a push, without a live socket
  m = path.match(/^\/pacts\/([^/]+)\/actions$/);
  if (method === "POST" && m) {
    const pact = store.get(m[1]!);
    if (!pact) return json(res, 404, { error: "no such pact" });
    const body = (await readJson(req)) as ActionBody;
    if (!body?.seatId || !body?.token || !pact.authorize(body.seatId, body.token)) {
      return json(res, 401, { error: "unauthorized" });
    }
    const { ok } = pact.applyAction(body.seatId, body.action);
    json(res, ok ? 200 : 409, { ok, view: pact.engine.viewFor(body.seatId) });
    return;
  }

  json(res, 404, { error: "not found" });
}

// ---------- helpers ----------

function credentials(cfg: Config, pact: Pact, seatId: string, token: string): SeatCredentials {
  const wsBase = cfg.publicUrl.replace(/^http/, "ws");
  const code = pact.engine.code;
  return {
    pactId: pact.id,
    code,
    seatId,
    token,
    wsUrl: `${wsBase}/ws?pactId=${pact.id}&seatId=${seatId}&token=${encodeURIComponent(token)}`,
    joinLink: `${cfg.appScheme}://join?code=${encodeURIComponent(code)}`,
  };
}

function cleanName(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const name = raw.trim().slice(0, MAX_NAME);
  return name.length > 0 ? name : null;
}

function notifierStatus(cfg: Config) {
  return { ios: !!cfg.apns, android: !!cfg.fcm };
}

function cors(res: http.ServerResponse): void {
  res.setHeader("access-control-allow-origin", "*");
  res.setHeader("access-control-allow-methods", "GET,POST,OPTIONS");
  res.setHeader("access-control-allow-headers", "content-type");
}

function json(res: http.ServerResponse, status: number, body: unknown): void {
  const payload = JSON.stringify(body);
  res.writeHead(status, { "content-type": "application/json" });
  res.end(payload);
}

async function readJson(req: http.IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  let size = 0;
  for await (const chunk of req) {
    size += (chunk as Buffer).length;
    if (size > 64 * 1024) throw new Error("body too large");
    chunks.push(chunk as Buffer);
  }
  if (chunks.length === 0) return {};
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    return {};
  }
}
