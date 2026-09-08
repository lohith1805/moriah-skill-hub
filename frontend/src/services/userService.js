// Per-user account assets that aren't role-specific.
//   POST /api/v1/users/me/signature   (multipart, field `file` — PNG or JPEG)
//   GET  /api/v1/users/me/signature   -> { downloadUrl, expiresAt } | 404

import { apiClient } from "./apiClient";

// Upload (or replace) the caller's saved signature image. Returns a fresh
// presigned URL for it.
export async function saveSignature(file) {
  if (!file) throw new Error("No file selected.");
  const res = await apiClient.requestMultipart("/users/me/signature", { method: "POST", files: { file } });
  return res?.downloadUrl || res?.url || null;
}

// A presigned URL for the caller's saved signature, or null when none is on file.
export async function getMySignatureUrl() {
  try {
    const res = await apiClient.get("/users/me/signature");
    return res?.downloadUrl || res?.url || (typeof res === "string" ? res : null);
  } catch (e) {
    if (e?.status === 404) return null;
    throw e;
  }
}
