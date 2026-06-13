import { test } from "node:test";
import assert from "node:assert/strict";
import {
  PactEngine,
  REQUEST_TIMEOUT_MS,
  COOLDOWN_MS,
  fmtClock,
} from "../src/index.ts";

// A three-seat table, host joined, two guests pending — the canonical setup.
function table() {
  const e = new PactEngine({ code: "TBL-001", passMinutes: 5, stakes: "Loser buys dessert" });
  e.addSeat("y", "Yousef", { host: true, joined: true });
  e.addSeat("m", "Maya");
  e.addSeat("o", "Omar");
  return e;
}

function lockedTable() {
  const e = table();
  e.join("m");
  e.join("o");
  e.lock();
  return e;
}

test("lobby: host sees the code and the roster", () => {
  const e = table();
  const v = e.viewFor("y");
  assert.equal(v.screen, "lobby-host");
  if (v.screen !== "lobby-host") return;
  assert.equal(v.code, "TBL-001");
  assert.equal(v.canLock, false); // only host joined so far
  assert.equal(v.members.length, 1);
});

test("lobby: a guest who hasn't joined sees the join screen", () => {
  const e = table();
  const v = e.viewFor("m");
  assert.equal(v.screen, "join");
});

test("join: a guest who joins waits for the host", () => {
  const e = table();
  assert.equal(e.join("m"), true);
  const v = e.viewFor("m");
  assert.equal(v.screen, "lobby-wait");
});

test("lock: needs at least two present", () => {
  const e = table();
  assert.equal(e.lock(), false, "cannot lock with only the host");
  e.join("m");
  assert.equal(e.lock(), true, "two present is a table");
  assert.equal(e.status, "locked");
});

test("lock: host's canLock flips once a second person is in", () => {
  const e = table();
  e.join("m");
  const v = e.viewFor("y");
  assert.equal(v.screen, "lobby-host");
  if (v.screen === "lobby-host") assert.equal(v.canLock, true);
});

test("locked: everyone sees the night screen", () => {
  const e = lockedTable();
  for (const id of ["y", "m", "o"]) {
    const v = e.viewFor(id);
    assert.equal(v.screen, "night");
  }
});

test("ask: an asker waits and the others get the ask", () => {
  const e = lockedTable();
  assert.equal(e.ask("y", "Need to pay"), true);
  assert.equal(e.viewFor("y").screen, "waiting");
  assert.equal(e.viewFor("m").screen, "ask");
  assert.equal(e.viewFor("o").screen, "ask");
});

test("ask: the asker cannot vote on their own ask", () => {
  const e = lockedTable();
  e.ask("y", "Directions");
  assert.equal(e.vote("y", true), false);
});

test("ask: unanimous yes grants a pass to the asker only", () => {
  const e = lockedTable();
  e.ask("y", "Expecting a call");
  assert.equal(e.vote("m", true), true);
  // still waiting on Omar
  assert.equal(e.viewFor("y").screen, "waiting");
  assert.equal(e.vote("o", true), true);
  const v = e.viewFor("y");
  assert.equal(v.screen, "pass");
  if (v.screen === "pass") {
    assert.equal(v.emergency, false);
    assert.ok(v.remainMs > 0 && v.remainMs <= 5 * 60 * 1000);
  }
  // the others see a banner, not a pass
  const o = e.viewFor("o");
  assert.equal(o.screen, "night");
  if (o.screen === "night") assert.match(String(o.banner), /Yousef is out/);
});

test("ask: a single no denies immediately and starts a cooldown", () => {
  const e = lockedTable();
  e.ask("y", "Work");
  assert.equal(e.vote("m", false), true);
  assert.equal(e.request, null, "request ends on the first no");
  assert.equal(e.pass, null);
  // asker is cooling down and told why
  const v = e.viewFor("y");
  assert.equal(v.screen, "night");
  if (v.screen === "night") {
    assert.equal(v.canAsk, false);
    assert.ok((v.cooldownMs ?? 0) > 0);
    assert.match(String(v.notice), /wait/i);
  }
});

test("ask: silence past the deadline is a no", () => {
  const e = lockedTable();
  e.ask("y", "No reason");
  e.tick(REQUEST_TIMEOUT_MS + 1);
  assert.equal(e.request, null);
  assert.equal(e.pass, null);
  const v = e.viewFor("y");
  assert.equal(v.screen, "night");
  if (v.screen === "night") assert.equal(v.canAsk, false); // cooling down
});

test("cooldown: cannot re-ask until it elapses, then can", () => {
  const e = lockedTable();
  e.ask("y", "Work");
  e.vote("m", false);
  assert.equal(e.canAsk("y"), false);
  e.tick(COOLDOWN_MS - 1000);
  assert.equal(e.canAsk("y"), false);
  e.tick(2000);
  assert.equal(e.canAsk("y"), true);
});

test("one thing at a time: cannot ask while a request or pass is live", () => {
  const e = lockedTable();
  e.ask("y", "A");
  assert.equal(e.canAsk("m"), false, "no second ask while one is open");
  e.vote("m", true);
  e.vote("o", true); // pass now live
  assert.equal(e.canAsk("m"), false, "no ask while a pass is live");
});

test("pass: relocks automatically when it expires", () => {
  const e = lockedTable();
  e.ask("y", "Pay");
  e.vote("m", true);
  e.vote("o", true);
  assert.equal(e.viewFor("y").screen, "pass");
  e.tick(5 * 60 * 1000 + 1);
  assert.equal(e.pass, null);
  assert.equal(e.viewFor("y").screen, "night");
});

test("emergency: bypasses consent and is marked as such", () => {
  const e = lockedTable();
  assert.equal(e.emergency("m"), true);
  const v = e.viewFor("m");
  assert.equal(v.screen, "pass");
  if (v.screen === "pass") assert.equal(v.emergency, true);
  const other = e.viewFor("y");
  if (other.screen === "night") assert.match(String(other.banner), /emergency/);
});

test("leave: breaks the pact for everyone and names who", () => {
  const e = lockedTable();
  assert.equal(e.leave("m"), true);
  assert.equal(e.status, "broken");
  for (const id of ["y", "m", "o"]) {
    const v = e.viewFor(id);
    assert.equal(v.screen, "broken");
    if (v.screen === "broken") assert.equal(v.recap.brokenBy, "Maya");
  }
});

test("leave: is always allowed, even mid-request", () => {
  const e = lockedTable();
  e.ask("y", "Pay");
  assert.equal(e.leave("o"), true);
  assert.equal(e.status, "broken");
});

test("recap: counts asks, grants, denials and time present", () => {
  const e = lockedTable();
  e.tick(10 * 60 * 1000); // ten minutes present
  e.ask("y", "Pay");
  e.vote("m", true);
  e.vote("o", true); // grant
  e.tick(5 * 60 * 1000 + 1); // pass expires, relock
  e.ask("m", "Call");
  e.vote("y", false); // deny
  e.tick(COOLDOWN_MS + 1);
  e.leave("o"); // break
  const r = e.recap();
  assert.equal(r.asks, 2);
  assert.equal(r.granted, 1);
  assert.equal(r.denied, 1);
  assert.equal(r.brokenBy, "Omar");
  assert.ok(r.presentMs > 0);
});

test("guards: cannot ask or vote before the table is locked", () => {
  const e = table();
  e.join("m");
  assert.equal(e.canAsk("y"), false);
  assert.equal(e.ask("y", "x"), false);
  assert.equal(e.vote("m", true), false);
});

test("real clock: tick() is rejected on an authoritative engine", () => {
  let now = 1000;
  const e = new PactEngine({ now: () => now });
  assert.throws(() => e.tick(1000), /manual-clock/);
  // but sync() drives transitions against the injected clock
  e.addSeat("y", "Yousef", { host: true, joined: true });
  e.addSeat("m", "Maya", { joined: true });
  e.lock();
  e.ask("y", "Pay");
  now += REQUEST_TIMEOUT_MS + 1;
  assert.equal(e.sync(), true, "deadline transition fires on sync");
  assert.equal(e.request, null);
});

test("fmtClock: formats minutes and hours", () => {
  assert.equal(fmtClock(0), "0:00");
  assert.equal(fmtClock(5000), "0:05");
  assert.equal(fmtClock(65 * 1000), "1:05");
  assert.equal(fmtClock(3661 * 1000), "1:01:01");
  assert.equal(fmtClock(-100), "0:00");
});

test("subscribe: listeners fire on state change and can unsubscribe", () => {
  const e = lockedTable();
  let calls = 0;
  const off = e.subscribe(() => calls++);
  e.ask("y", "Pay");
  assert.ok(calls >= 1);
  off();
  const before = calls;
  e.vote("m", true);
  assert.equal(calls, before, "no calls after unsubscribe");
});
