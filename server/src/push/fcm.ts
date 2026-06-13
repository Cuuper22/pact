import type { FcmConfig } from "../config.js";
import { signJwt } from "./jwt.js";

// Firebase Cloud Messaging over the HTTP v1 API. We mint an OAuth2 access
// token from the service account (a signed RS256 JWT exchanged at Google's
// token endpoint) and cache it until it nears expiry.

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

export interface FcmMessage {
  /** Data-only payload so the app fully controls presentation (the night
   * theme matters even in a notification). All values must be strings. */
  data?: Record<string, string>;
  notification?: { title: string; body: string };
  android?: Record<string, unknown>;
}

export class FcmProvider {
  private accessToken: { value: string; expiresAt: number } | null = null;

  constructor(private readonly cfg: FcmConfig) {}

  private async getAccessToken(): Promise<string | null> {
    const now = Date.now();
    if (this.accessToken && now < this.accessToken.expiresAt - 60_000) {
      return this.accessToken.value;
    }
    const iat = Math.floor(now / 1000);
    const assertion = signJwt(
      { alg: "RS256", typ: "JWT" },
      {
        iss: this.cfg.clientEmail,
        scope: SCOPE,
        aud: TOKEN_URL,
        iat,
        exp: iat + 3600,
      },
      this.cfg.privateKey,
      "RS256"
    );
    try {
      const res = await fetch(TOKEN_URL, {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
          assertion,
        }),
      });
      if (!res.ok) {
        console.warn(`[fcm] token exchange failed: ${res.status} ${await res.text()}`);
        return null;
      }
      const json = (await res.json()) as { access_token: string; expires_in: number };
      this.accessToken = {
        value: json.access_token,
        expiresAt: now + json.expires_in * 1000,
      };
      return json.access_token;
    } catch (e) {
      console.warn(`[fcm] token exchange error: ${(e as Error).message}`);
      return null;
    }
  }

  /** Send to one registration token. Resolves to true on success. */
  async send(registrationToken: string, message: FcmMessage): Promise<boolean> {
    const accessToken = await this.getAccessToken();
    if (!accessToken) return false;
    const url = `https://fcm.googleapis.com/v1/projects/${this.cfg.projectId}/messages:send`;
    try {
      const res = await fetch(url, {
        method: "POST",
        headers: {
          authorization: `Bearer ${accessToken}`,
          "content-type": "application/json",
        },
        body: JSON.stringify({ message: { token: registrationToken, ...message } }),
      });
      if (!res.ok) {
        console.warn(`[fcm] send failed: ${res.status} ${await res.text()}`);
        return false;
      }
      return true;
    } catch (e) {
      console.warn(`[fcm] send error: ${(e as Error).message}`);
      return false;
    }
  }
}
