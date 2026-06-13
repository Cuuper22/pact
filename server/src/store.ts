import type { Config } from "./config.js";
import type { Notifier } from "./push/index.js";
import { Pact } from "./pact.js";
import { id as makeId, tableCode } from "./ids.js";

// In-memory only, by design: "nothing to sign into, nothing to breach, nothing
// to sell." Pacts purge themselves after the configured TTL. A single timer
// ticks every live pact (firing deadline/expiry transitions) and reaps the
// expired ones.

const TICK_MS = 1000;

export class PactStore {
  private readonly pacts = new Map<string, Pact>();
  private readonly byCode = new Map<string, string>(); // code -> pactId
  private timer: NodeJS.Timeout | null = null;

  constructor(
    private readonly cfg: Config,
    private readonly notifier: Notifier
  ) {}

  start(): void {
    if (this.timer) return;
    this.timer = setInterval(() => this.tickAll(), TICK_MS);
    // Don't keep the process alive solely for the tick loop.
    this.timer.unref?.();
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  create(opts: { code?: string; passMinutes?: number; stakes?: string }): Pact {
    let code = opts.code ?? tableCode();
    // Vanishingly unlikely, but guarantee code uniqueness among live pacts.
    while (this.byCode.has(code)) code = tableCode();
    const pactId = makeId();
    const pact = new Pact(pactId, { ...opts, code }, this.notifier);
    this.pacts.set(pactId, pact);
    this.byCode.set(code, pactId);
    return pact;
  }

  get(pactId: string): Pact | undefined {
    return this.pacts.get(pactId);
  }

  byJoinCode(code: string): Pact | undefined {
    const pactId = this.byCode.get(code.toUpperCase().trim());
    return pactId ? this.pacts.get(pactId) : undefined;
  }

  get size(): number {
    return this.pacts.size;
  }

  private tickAll(): void {
    const now = Date.now();
    for (const [pactId, pact] of this.pacts) {
      if (now - pact.createdAt >= this.cfg.pactTtlMs) {
        this.pacts.delete(pactId);
        this.byCode.delete(pact.engine.code);
        continue;
      }
      pact.tick();
    }
  }
}
