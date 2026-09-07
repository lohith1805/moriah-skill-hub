import { useEffect } from "react";
import { X, FileText, ShieldCheck } from "lucide-react";

/* ------------------------------------------------------------------------
   LegalModal
   Opens over the auth card to show Terms of Service / Privacy Policy text
   inline, so users don't get bounced out of the registration flow.

   Usage:
     const [legalDoc, setLegalDoc] = useState(null); // "terms" | "privacy" | null
     <LegalModal doc={legalDoc} onClose={() => setLegalDoc(null)} />

   NOTE: The copy below is placeholder text. Swap CONTENT.terms.body and
   CONTENT.privacy.body for your real, legally-reviewed policy text before
   shipping this to production.
------------------------------------------------------------------------- */

const CONTENT = {
  terms: {
    icon: FileText,
    title: "Terms of Service",
    updated: "Last updated: January 2026",
    body: [
      "These Terms of Service (\"Terms\") govern your access to and use of Moriah Skill Hub. By creating an account you agree to be bound by these Terms.",
      "1. Accounts — You must provide accurate information when registering and are responsible for maintaining the confidentiality of your login credentials.",
      "2. Acceptable use — You agree not to misuse the platform, attempt to access other users' data, or interfere with the normal operation of the service.",
      "3. Content — Coursework, submissions, and mentor feedback exchanged on the platform remain subject to applicable academic and intellectual property policies.",
      "4. Subscriptions — Paid plans, where applicable, renew automatically unless cancelled prior to the renewal date, as described at checkout.",
      "5. Termination — We may suspend or terminate accounts that violate these Terms or applicable law.",
      "Replace this placeholder with your organization's reviewed Terms of Service before launch.",
    ],
  },
  privacy: {
    icon: ShieldCheck,
    title: "Privacy Policy",
    updated: "Last updated: January 2026",
    body: [
      "This Privacy Policy explains how Moriah Skill Hub collects, uses, and protects your information.",
      "1. Information we collect — Account details (name, email, phone), role/persona selected at signup, and usage data such as course progress and mentor interactions.",
      "2. How we use it — To provide and improve the platform, personalize your dashboard by role, communicate service updates, and evaluate performance.",
      "3. Sharing — We do not sell personal data. Limited data may be shared with mentors or reviewers strictly to deliver coursework feedback.",
      "4. Security — Reasonable technical and organizational measures are used to protect your data, though no system is 100% secure.",
      "5. Your choices — You may request access to, correction of, or deletion of your account data by contacting support.",
      "Replace this placeholder with your organization's reviewed Privacy Policy before launch.",
    ],
  },
};

export default function LegalModal({ doc, onClose }) {
  const entry = doc ? CONTENT[doc] : null;

  useEffect(() => {
    if (!entry) return;
    const onKey = (e) => e.key === "Escape" && onClose();
    document.addEventListener("keydown", onKey);
    document.body.style.overflow = "hidden";
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = "";
    };
  }, [entry, onClose]);

  if (!entry) return null;
  const Icon = entry.icon;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-ink-900/50 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="legal-modal-title"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div className="relative w-full max-w-lg max-h-[80vh] flex flex-col rounded-2xl bg-white shadow-xl border border-border overflow-hidden">
        <div className="flex items-center justify-between gap-3 border-b border-border px-6 py-4">
          <div className="flex items-center gap-3">
            <span className="h-9 w-9 rounded-md bg-primary-800 text-white flex items-center justify-center shrink-0">
              <Icon size={16} />
            </span>
            <div>
              <h3 id="legal-modal-title" className="font-display text-base font-bold text-ink-900">
                {entry.title}
              </h3>
              <p className="text-xs text-ink-400">{entry.updated}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="h-8 w-8 rounded-md flex items-center justify-center text-ink-400 hover:text-ink-700 hover:bg-cream-100 transition-colors"
          >
            <X size={16} />
          </button>
        </div>

        <div className="overflow-y-auto px-6 py-5 flex flex-col gap-3">
          {entry.body.map((para, i) => (
            <p key={i} className="text-sm text-ink-600 leading-relaxed">
              {para}
            </p>
          ))}
        </div>

        <div className="border-t border-border px-6 py-4 flex justify-end">
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg bg-primary-800 text-white text-sm font-medium px-4 py-2.5 hover:bg-primary-900 transition-colors"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}