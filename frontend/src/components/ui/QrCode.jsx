import { QRCodeSVG } from "qrcode.react";

/**
 * Renders a string (e.g. an `otpauth://` provisioning URI) as a scannable QR
 * code. Encoding happens entirely in the browser — nothing is sent to a
 * third-party QR service — so it is safe to pass a TOTP secret here.
 */
export default function QrCode({ value, size = 168, className = "" }) {
  if (!value) return null;
  return (
    <div
      className={`inline-flex rounded-lg border border-border bg-white p-3 ${className}`}
      aria-label="QR code"
    >
      <QRCodeSVG value={value} size={size} level="M" marginSize={2} />
    </div>
  );
}
