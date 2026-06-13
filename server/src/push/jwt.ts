import { createSign } from "node:crypto";

function b64url(input: Buffer | string): string {
  return Buffer.from(input).toString("base64url");
}

/**
 * Sign a JWT with the given PEM private key. APNs uses ES256, Google service
 * accounts use RS256 — both are supported by node's crypto signer with the
 * right algorithm string.
 */
export function signJwt(
  header: Record<string, unknown>,
  payload: Record<string, unknown>,
  privateKeyPem: string,
  algorithm: "ES256" | "RS256"
): string {
  const h = b64url(JSON.stringify(header));
  const p = b64url(JSON.stringify(payload));
  const signingInput = `${h}.${p}`;
  const nodeAlg = algorithm === "ES256" ? "sha256" : "RSA-SHA256";
  const signer = createSign(nodeAlg);
  signer.update(signingInput);
  signer.end();
  // ES256 must be encoded as a raw r||s pair (IEEE P1363), which node exposes
  // via dsaEncoding; RS256 is a plain RSA signature.
  const signature = signer.sign(
    algorithm === "ES256"
      ? { key: privateKeyPem, dsaEncoding: "ieee-p1363" }
      : privateKeyPem
  );
  return `${signingInput}.${b64url(signature)}`;
}
