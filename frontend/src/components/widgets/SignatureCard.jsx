import { useEffect, useState } from "react";
import { PenTool } from "lucide-react";
import Card from "../ui/Card";
import { useToast } from "../../context/ToastContext";
import { getMySignatureUrl, saveSignature } from "../../services/userService";

/**
 * "Digital Signature" section shared by every role's profile page. Uploads a
 * PNG/JPEG of the user's signature (POST /users/me/signature) so the offer-letter
 * e-sign flow can stamp it instead of asking them to redraw it.
 */
export default function SignatureCard({ className = "" }) {
  const { notify } = useToast();
  const [signatureUrl, setSignatureUrl] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    getMySignatureUrl().then(setSignatureUrl).catch(() => setSignatureUrl(null));
  }, []);

  const handleChange = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    const okType =
      ["image/png", "image/jpeg", "image/jpg"].includes(file.type) || /\.(png|jpe?g)$/i.test(file.name);
    if (!okType) {
      setError("The signature must be a PNG or JPEG image.");
      return;
    }
    if (file.size > 1024 * 1024) {
      setError("Keep the signature image under 1MB.");
      return;
    }
    setError("");
    setBusy(true);
    try {
      const url = await saveSignature(file);
      setSignatureUrl(url || (await getMySignatureUrl().catch(() => null)));
      notify("Signature saved.", { type: "success", title: "Signature updated" });
    } catch (err) {
      notify(err?.message || "Could not save your signature.", { type: "error" });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className={className}>
      <h3 className="font-display font-bold text-ink-900 text-base mb-1 flex items-center gap-2">
        <PenTool size={17} className="text-primary-600" /> Digital Signature
      </h3>
      <p className="text-xs text-ink-500 mb-4">
        Upload a picture of your signature (PNG or JPEG — a transparent PNG works best). It's applied when
        you e-sign an offer letter or agreement, so you don't redraw it each time.
      </p>
      <div className="flex flex-col sm:flex-row sm:items-center gap-4">
        <div className="h-24 w-56 rounded-lg border border-border/80 bg-cream-50/50 flex items-center justify-center overflow-hidden shrink-0">
          {signatureUrl ? (
            <img src={signatureUrl} alt="Your signature" className="max-h-full max-w-full object-contain p-2" />
          ) : (
            <span className="text-xs text-ink-400">No signature saved</span>
          )}
        </div>
        <div>
          <label className="inline-flex items-center gap-1.5 text-xs font-semibold text-primary-600 hover:text-primary-700 bg-primary-50 hover:bg-primary-100 px-3 py-1.5 rounded-lg border border-primary-200 transition-colors cursor-pointer">
            <PenTool size={13} />
            <span>{busy ? "Uploading…" : signatureUrl ? "Replace signature" : "Upload signature"}</span>
            <input type="file" accept="image/png, image/jpeg" className="hidden" disabled={busy} onChange={handleChange} />
          </label>
          {error && <p className="mt-2 text-xs text-error-500 font-semibold">{error}</p>}
        </div>
      </div>
    </Card>
  );
}
