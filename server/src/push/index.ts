import type { Config } from "../config.js";
import type { Seat } from "@pact/engine";
import { ApnsProvider } from "./apns.js";
import { FcmProvider } from "./fcm.js";

// One façade over both platforms. The pact orchestration calls these without
// caring which store a seat is on. If a platform is unconfigured, sends for
// that platform become logged no-ops so the relay still runs end-to-end.

export type RefreshKind =
  | "lock"
  | "grant"
  | "deny"
  | "relock"
  | "leave"
  | "emergency";

export class Notifier {
  private readonly apns: ApnsProvider | null;
  private readonly fcm: FcmProvider | null;

  constructor(cfg: Config) {
    this.apns = cfg.apns ? new ApnsProvider(cfg.apns) : null;
    this.fcm = cfg.fcm ? new FcmProvider(cfg.fcm) : null;
  }

  get configured(): { ios: boolean; android: boolean } {
    return { ios: !!this.apns, android: !!this.fcm };
  }

  /**
   * An actionable "someone asks the table" notification, delivered to a voter.
   * Carries allow / not-now actions so a locked phone can vote in one tap.
   */
  async ask(seat: Seat, pactId: string, askerName: string, reason: string): Promise<void> {
    if (!seat.pushToken) return;
    const title = `${askerName} asks the table`;
    const body = reason;
    if (seat.platform === "ios" && this.apns) {
      await this.apns.send(
        seat.pushToken,
        {
          aps: {
            alert: { title, body },
            category: "PACT_ASK",
            sound: "default",
            "interruption-level": "time-sensitive",
            "mutable-content": 1,
          },
          pactId,
          kind: "ask",
        },
        { priority: 10, pushType: "alert", collapseId: `${pactId}-ask` }
      );
    } else if (seat.platform === "android" && this.fcm) {
      await this.fcm.send(seat.pushToken, {
        data: { pactId, kind: "ask", title, body },
        android: { priority: "high" },
      });
    } else {
      this.logSkip(seat, "ask");
    }
  }

  /**
   * A state-change nudge so a backgrounded app updates its shield without a
   * live socket: lock shields everyone, grant unshields the grantee, relock
   * reshields, leave/broken unshields everyone. Sent as a high-priority
   * background/data push.
   */
  async refresh(seat: Seat, pactId: string, kind: RefreshKind): Promise<void> {
    if (!seat.pushToken) return;
    if (seat.platform === "ios" && this.apns) {
      await this.apns.send(
        seat.pushToken,
        { aps: { "content-available": 1 }, pactId, kind },
        { priority: 5, pushType: "background", collapseId: `${pactId}-${kind}` }
      );
    } else if (seat.platform === "android" && this.fcm) {
      await this.fcm.send(seat.pushToken, {
        data: { pactId, kind },
        android: { priority: "high" },
      });
    } else {
      this.logSkip(seat, kind);
    }
  }

  private logSkip(seat: Seat, kind: string): void {
    console.info(
      `[push] (no provider for ${seat.platform ?? "unknown"}) would send "${kind}" to ${seat.name}`
    );
  }
}
