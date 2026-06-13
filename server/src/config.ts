// All configuration comes from the environment so the relay can run anywhere
// with no code changes. Push credentials are optional: without them the relay
// runs fully (WebSocket sync still works), it just logs instead of sending.

export interface ApnsConfig {
  keyId: string;
  teamId: string;
  /** PEM contents of the AuthKey .p8 (ES256). */
  key: string;
  /** App bundle id, used as the apns-topic. */
  topic: string;
  /** Use the sandbox gateway during development. */
  sandbox: boolean;
}

export interface FcmConfig {
  projectId: string;
  clientEmail: string;
  /** PEM contents of the service-account private key (RS256). */
  privateKey: string;
}

export interface Config {
  port: number;
  /** Public origin clients reach, used to build QR deep links. */
  publicUrl: string;
  /** Custom URL scheme for deep links into the native apps. */
  appScheme: string;
  /** How long a pact lives before it purges itself. */
  pactTtlMs: number;
  apns: ApnsConfig | null;
  fcm: FcmConfig | null;
}

function env(name: string): string | undefined {
  const v = process.env[name];
  return v && v.trim() ? v.trim() : undefined;
}

// Private keys are passed as env vars with literal "\n" sequences (the usual
// shape when a PEM is stuffed into a single env var); normalise them back.
function pem(name: string): string | undefined {
  const v = env(name);
  return v ? v.replace(/\\n/g, "\n") : undefined;
}

function loadApns(): ApnsConfig | null {
  const keyId = env("APNS_KEY_ID");
  const teamId = env("APNS_TEAM_ID");
  const key = pem("APNS_KEY");
  const topic = env("APNS_TOPIC");
  if (keyId && teamId && key && topic) {
    return { keyId, teamId, key, topic, sandbox: env("APNS_SANDBOX") === "true" };
  }
  return null;
}

function loadFcm(): FcmConfig | null {
  const projectId = env("FCM_PROJECT_ID");
  const clientEmail = env("FCM_CLIENT_EMAIL");
  const privateKey = pem("FCM_PRIVATE_KEY");
  if (projectId && clientEmail && privateKey) {
    return { projectId, clientEmail, privateKey };
  }
  return null;
}

export function loadConfig(): Config {
  const port = Number(env("PORT") ?? 8787);
  const publicUrl = env("PUBLIC_URL") ?? `http://localhost:${port}`;
  return {
    port,
    publicUrl,
    appScheme: env("APP_SCHEME") ?? "pact",
    pactTtlMs: Number(env("PACT_TTL_MS") ?? 24 * 60 * 60 * 1000),
    apns: loadApns(),
    fcm: loadFcm(),
  };
}
