// Renders the three devices from one shared PactEngine and wires the real
// buttons back to it. The buttons here are the surface: clicking "Allow"
// calls engine.vote(...) and every device re-renders from the new state.
import { PactEngine, fmtClock } from "./engine.js";

const SEATS = [
  { id: "y", name: "Yousef", host: true,  joined: true  },
  { id: "m", name: "Maya",   host: false, joined: false },
  { id: "o", name: "Omar",   host: false, joined: false },
];
const REASONS = ["Expecting a call", "Need to pay", "Directions", "Work", "No reason"];

let engine;
const stage = document.getElementById("stage");
const clockEl = document.getElementById("clock");

function boot() {
  engine = new PactEngine({ code: "TBL-492", passMinutes: 5, stakes: "First break buys dessert" });
  for (const s of SEATS) engine.addSeat(s.id, s.name, { host: s.host, joined: s.joined });
  engine.subscribe(render);
  render();
}

const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
const chip = (s, cls = "") => `<span class="chip ${cls}">${esc(s.name[0])}</span>`;

function deviceHTML(seat) {
  const v = engine.viewFor(seat.id);
  return `<div class="device">
    <div class="device__name"><b>${esc(seat.name)}</b>${seat.host ? " · host" : ""}</div>
    <div class="phone"><div class="screen ${screenClass(v.screen)}">${screenHTML(v, seat)}</div></div>
  </div>`;
}

function screenClass(screen) {
  return screen === "join" || screen === "lobby-host" || screen === "lobby-wait" ? "light center" : "dark center";
}

function screenHTML(v, seat) {
  switch (v.screen) {
    case "join":
      return `<p class="lit-h">Tonight's pact</p><p class="lit-sub">Scan to join</p>
        <div class="qr" aria-hidden="true">${"<span></span>".repeat(9)}</div>
        <p class="code">${esc(v.code)}</p><div class="spacer"></div>
        <button class="btn lit-cta" data-act="join" data-seat="${seat.id}">Scan in</button>`;

    case "lobby-host":
      return `<p class="lit-h">Tonight's pact</p><p class="lit-sub">${v.members.length} of 3 stacked in</p>
        <div class="qr" aria-hidden="true">${"<span></span>".repeat(9)}</div>
        <p class="code">${esc(v.code)}</p>
        <div class="people">${v.members.map((m) => chip(m, "lit")).join("")}</div>
        <p class="foot">${esc(v.stakes)}</p><div class="spacer"></div>
        <button class="btn lit-cta" data-act="lock" data-seat="${seat.id}" ${v.canLock ? "" : "disabled"}>${v.canLock ? "Lock the table" : "Waiting for one more"}</button>`;

    case "lobby-wait":
      return `<p class="lit-h">You're in</p><p class="lit-sub">Waiting for the host to lock</p>
        <div class="spacer"></div><div class="people">${v.members.map((m) => chip(m, "lit")).join("")}</div>
        <p class="foot">First names only. No account.</p>`;

    case "night": {
      const cool = v.cooldownMs > 0 ? ` · ask again in ${fmtClock(v.cooldownMs)}` : "";
      return `<div class="candle"><span class="glow"></span><span class="flame"></span></div>
        <p class="timer">${fmtClock(v.presentMs)}</p><p class="sub">${v.members.length} still in</p>
        ${v.banner ? `<p class="banner">${esc(v.banner)}</p>` : ""}
        ${v.notice ? `<p class="notice">${esc(v.notice)}</p>` : ""}
        <div class="spacer"></div>
        <div class="people">${v.members.map((m) => chip(m)).join("")}</div>
        <button class="btn" data-act="ask" data-seat="${seat.id}" ${v.canAsk ? "" : "disabled"}>Ask the table${cool}</button>
        <div class="row" style="margin-top:8px">
          <button class="leave" data-act="emergency" data-seat="${seat.id}">Emergency</button>
          <button class="leave" data-act="leave" data-seat="${seat.id}" style="margin-left:auto">Leave the pact</button>
        </div>`;
    }

    case "ask":
      return `<div class="ask-head">${chip({ name: v.asker }, "lit")}<span><span class="ask-who">${esc(v.asker)} asks the table</span><br><span class="ask-time">${Math.ceil(v.remainMs / 1000)}s to answer</span></span></div>
        <p class="reason">${esc(v.reason)}</p>
        <div class="bar"><i style="width:${barPct(v.remainMs)}%"></i></div>
        ${tallyHTML(v.tally, seat)}
        <div class="spacer"></div>
        <div class="row"><button class="btn primary tiny" data-act="allow" data-seat="${seat.id}">Allow 5 min</button><button class="btn tiny" data-act="deny" data-seat="${seat.id}">Not now</button></div>
        <p class="foot">Needs everyone</p>`;

    case "waiting":
      return `<div class="candle"><span class="glow"></span><span class="flame"></span></div>
        <p class="sub" style="margin-top:14px">Your ask is on the table</p>
        <p class="reason" style="margin-top:10px">${esc(v.reason)}</p>
        <div class="bar"><i style="width:${barPct(v.remainMs)}%"></i></div>
        ${tallyHTML(v.tally, seat)}
        <div class="spacer"></div><p class="foot">${Math.ceil(v.remainMs / 1000)}s left · needs everyone</p>`;

    case "pass":
      return `<p class="timer" style="color:var(--ember-hi);margin-top:40px">${fmtClock(v.remainMs)}</p>
        <p class="sub">${v.emergency ? "Emergency pass" : "You're out. The table can see the clock."}</p>
        <div class="spacer"></div><p class="foot">Relocks automatically.</p>`;

    case "broken": {
      const r = v.recap;
      return `<div class="candle dead"><span class="glow"></span><span class="flame"></span></div>
        <div class="recap" style="margin-top:16px">
          <h3>Pact broken</h3>
          <p class="by">${r.brokenBy ? esc(r.brokenBy) + " left first." : "The pact ended."} ${esc(r.stakes)}</p>
          <dl>
            <dt>Time present</dt><dd>${fmtClock(r.presentMs)}</dd>
            <dt>Asks made</dt><dd>${r.asks}</dd>
            <dt>Granted</dt><dd>${r.granted}</dd>
            <dt>Refused</dt><dd>${r.denied}</dd>
          </dl>
        </div>`;
    }
    default:
      return `<p class="sub">—</p>`;
  }
}

function tallyHTML(tally, _seat) {
  if (!tally || !tally.length) return "";
  return `<ul class="tally">${tally.map((t) => {
    const cls = t.vote === true ? "yes" : t.vote === false ? "no" : "pending";
    const label = t.vote === true ? "Allowed" : t.vote === false ? "Not now" : "Deciding";
    return `<li><span>${esc(t.name)}</span><span class="${cls}">${label}</span></li>`;
  }).join("")}</ul>`;
}

const barPct = (remainMs) => Math.max(0, Math.min(100, (remainMs / 60000) * 100));

function render() {
  stage.innerHTML = SEATS.map(deviceHTML).join("");
  clockEl.textContent = "t+" + fmtClock(engine.t);
}

// one delegated handler: the buttons are the surface
stage.addEventListener("click", (e) => {
  const b = e.target.closest("[data-act]");
  if (!b) return;
  const id = b.dataset.seat;
  switch (b.dataset.act) {
    case "join": engine.join(id); break;
    case "lock": engine.lock(); break;
    case "ask": engine.ask(id, REASONS[Math.floor((engine.t / 1000) % REASONS.length)] || "No reason"); break;
    case "allow": engine.vote(id, true); break;
    case "deny": engine.vote(id, false); break;
    case "leave": engine.leave(id); break;
    case "emergency": engine.emergency(id); break;
  }
});

document.querySelector(".rail").addEventListener("click", (e) => {
  if (e.target.dataset.tick) engine.tick(Number(e.target.dataset.tick));
  if (e.target.hasAttribute("data-reset")) boot();
});

// Time is fully manual via the rail, so the session is deterministic to drive
// and inspect. The candle still flickers (CSS); the present-clock moves only
// when you advance it.

boot();
