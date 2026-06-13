import { loadConfig } from "./config.js";
import { createServer } from "./server.js";

const cfg = loadConfig();
const { http } = createServer(cfg);

http.listen(cfg.port, () => {
  const push = [cfg.apns ? "APNs" : null, cfg.fcm ? "FCM" : null].filter(Boolean);
  console.log(`pact relay listening on :${cfg.port}`);
  console.log(`  public url   ${cfg.publicUrl}`);
  console.log(`  app scheme   ${cfg.appScheme}://`);
  console.log(`  pact ttl     ${Math.round(cfg.pactTtlMs / 3600000)}h`);
  console.log(`  push         ${push.length ? push.join(" + ") : "none configured (logging only)"}`);
});

for (const sig of ["SIGINT", "SIGTERM"] as const) {
  process.on(sig, () => {
    console.log(`\n${sig} — shutting down`);
    http.close(() => process.exit(0));
  });
}
