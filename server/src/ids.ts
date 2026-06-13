import { randomBytes, randomInt } from "node:crypto";

// Human-typable table codes avoid ambiguous characters (no O/0, I/1, etc.) so
// a guest can read one off a host's screen in low light without mistakes.
const CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

/** A short, speakable table code like "TBL-K7QP". */
export function tableCode(): string {
  let body = "";
  for (let i = 0; i < 4; i++) {
    body += CODE_ALPHABET[randomInt(CODE_ALPHABET.length)];
  }
  return `TBL-${body}`;
}

/** An opaque url-safe id. */
export function id(bytes = 9): string {
  return randomBytes(bytes).toString("base64url");
}

/** A bearer token a seat uses to authenticate its socket and actions. */
export function token(): string {
  return randomBytes(24).toString("base64url");
}
