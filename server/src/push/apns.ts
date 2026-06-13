import http2 from "node:http2";
import type { ApnsConfig } from "../config.js";
import { signJwt } from "./jwt.js";

// A minimal APNs HTTP/2 provider. Tokens (the JWT) are reusable for ~1 hour;
// we cache and refresh. One long-lived h2 session to the gateway is reused.

const HOST_PROD = "https://api.push.apple.com";
const HOST_SANDBOX = "https://api.sandbox.push.apple.com";

export interface ApnsPayload {
  /** aps dictionary — alert, sound, category, content-available, etc. */
  aps: Record<string, unknown>;
  /** App-specific keys carried alongside aps. */
  [key: string]: unknown;
}

export class ApnsProvider {
  private session: http2.ClientHttp2Session | null = null;
  private cachedToken: { value: string; mintedAt: number } | null = null;

  constructor(private readonly cfg: ApnsConfig) {}

  private authToken(): string {
    const now = Date.now();
    if (this.cachedToken && now - this.cachedToken.mintedAt < 50 * 60 * 1000) {
      return this.cachedToken.value;
    }
    const value = signJwt(
      { alg: "ES256", kid: this.cfg.keyId },
      { iss: this.cfg.teamId, iat: Math.floor(now / 1000) },
      this.cfg.key,
      "ES256"
    );
    this.cachedToken = { value, mintedAt: now };
    return value;
  }

  private connect(): http2.ClientHttp2Session {
    if (this.session && !this.session.closed && !this.session.destroyed) {
      return this.session;
    }
    const host = this.cfg.sandbox ? HOST_SANDBOX : HOST_PROD;
    this.session = http2.connect(host);
    this.session.on("error", () => {
      this.session = null; // drop a broken session; next send reconnects
    });
    return this.session;
  }

  /** Send to one device token. Resolves to true on a 200 from APNs. */
  async send(
    deviceToken: string,
    payload: ApnsPayload,
    opts: { priority?: 5 | 10; pushType?: "alert" | "background"; collapseId?: string } = {}
  ): Promise<boolean> {
    const client = this.connect();
    const headers: http2.OutgoingHttpHeaders = {
      ":method": "POST",
      ":path": `/3/device/${deviceToken}`,
      authorization: `bearer ${this.authToken()}`,
      "apns-topic": this.cfg.topic,
      "apns-push-type": opts.pushType ?? "alert",
      "apns-priority": String(opts.priority ?? 10),
    };
    if (opts.collapseId) headers["apns-collapse-id"] = opts.collapseId;

    return new Promise<boolean>((resolve) => {
      const req = client.request(headers);
      let status = 0;
      let body = "";
      req.on("response", (h) => {
        status = Number(h[":status"] ?? 0);
      });
      req.setEncoding("utf8");
      req.on("data", (c) => (body += c));
      req.on("end", () => {
        if (status !== 200) {
          console.warn(`[apns] ${status} for ${deviceToken.slice(0, 8)}… ${body}`);
        }
        resolve(status === 200);
      });
      req.on("error", (e) => {
        console.warn(`[apns] request error: ${(e as Error).message}`);
        resolve(false);
      });
      req.end(JSON.stringify(payload));
    });
  }

  close(): void {
    this.session?.close();
    this.session = null;
  }
}
