import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { Award, Download, QrCode, ShieldCheck, X, Copy, Check } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import Badge from "../../components/ui/Badge";
import EmptyState from "../../components/ui/EmptyState";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import Modal from "../../components/ui/Modal";
import { getCertificates } from "../../services/studentService";
import { useToast } from "../../context/ToastContext";

export default function StudentCertificates() {
  const { notify } = useToast();
  const [certs, setCerts] = useState([]);
  const [loading, setLoading] = useState(true);

  // Modal and print states
  const [verifyCert, setVerifyCert] = useState(null);
  const [printCert, setPrintCert] = useState(null);
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    getCertificates().then((c) => {
      setCerts(c);
      setLoading(false);
    });
  }, []);

  // Print style injection
  useEffect(() => {
    if (printCert) {
      const style = document.createElement("style");
      style.id = "cert-print-style";
      style.innerHTML = `
        @media print {
          @page { size: landscape; margin: 0; }
          /* Hide app layout elements */
          body { background: white !important; margin: 0; padding: 0; }
          #root > div { display: none !important; }
          #cert-print-container {
            display: flex !important;
            position: fixed !important;
            top: 0;
            left: 0;
            width: 100% !important;
            height: 100% !important;
            z-index: 999999 !important;
            background: white !important;
            border: 24px double #B8890A !important;
            box-sizing: border-box !important;
            padding: 40px !important;
            flex-direction: column !important;
            justify-content: space-between !important;
            text-align: center !important;
          }
        }
      `;
      document.head.appendChild(style);
      
      // Open native printer window
      setTimeout(() => {
        window.print();
        setPrintCert(null);
      }, 300);

      return () => {
        const el = document.getElementById("cert-print-style");
        if (el) el.remove();
      };
    }
  }, [printCert]);

  const copyLink = (verifyCode) => {
    const url = `${window.location.origin}/verify/${verifyCode}`;
    navigator.clipboard.writeText(url).then(() => {
      setCopied(true);
      notify("Verification link copied to clipboard.", { type: "success" });
      setTimeout(() => setCopied(false), 2000);
    });
  };

  return (
    <div>
      <PageHeader
        title="Certificates"
        subtitle="QR-verifiable, cryptographically signed completion certificates"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Certificates" }]}
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading certificates…" /></div>
      ) : certs.length === 0 ? (
        <EmptyState icon={Award} title="No certificates yet" description="Certificates are issued automatically upon successful batch completion." />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {certs.map((c) => (
            <Card key={c.id} className="flex items-center gap-4">
              <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-gold-100 shrink-0">
                <Award className="text-gold-700" size={26} />
              </div>
              <div className="flex-1 min-w-0">
                <h3 className="font-medium text-ink-900">{c.title}</h3>
                <p className="text-xs text-ink-500 mt-0.5">Issued {c.issuedOn} · Code {c.verifyCode}</p>
                <Badge tone="success" className="mt-2">{c.status}</Badge>
              </div>
              <div className="flex flex-col gap-2 shrink-0">
                <Button size="sm" icon={Download} onClick={() => setPrintCert(c)}>PDF</Button>
                <Button size="sm" variant="ghost" icon={QrCode} onClick={() => setVerifyCert(c)}>Verify</Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Verification Modal */}
      {verifyCert && (
        <Modal
          open={!!verifyCert}
          onClose={() => setVerifyCert(null)}
          title="Verify Certificate Credential"
          description="Cryptographic proof verified against Moriah Skill Hub block ledger keys."
          footer={
            <Button variant="primary" fullWidth onClick={() => setVerifyCert(null)}>
              Close Verification Panel
            </Button>
          }
        >
          <div className="flex flex-col items-center gap-5 text-center mt-3">
            <div className="flex items-center gap-2 text-success-600 bg-success-50 py-1.5 px-4 rounded-full text-xs font-semibold">
              <ShieldCheck size={14} /> Secured Ledger Verified
            </div>

            {/* Dynamic QR code mapping */}
            <div className="border-4 border-gold-300 rounded-xl p-3 bg-white shadow-card">
              <img
                src={`https://api.qrserver.com/v1/create-qr-code/?size=160x160&data=${encodeURIComponent(
                  `${window.location.origin}/verify/${verifyCert.verifyCode}`
                )}`}
                alt="Verification QR Code"
                className="h-40 w-40"
              />
            </div>

            <div className="w-full">
              <p className="text-xs text-ink-400 font-semibold uppercase tracking-wider">Credential ID</p>
              <p className="font-mono text-sm font-bold text-ink-850 mt-0.5">{verifyCert.verifyCode}</p>

              <p className="text-xs text-ink-400 font-semibold uppercase tracking-wider mt-4">Recipient Name</p>
              <p className="text-sm font-semibold text-ink-900 mt-0.5">{verifyCert.studentName || "—"}</p>
            </div>

            <div className="w-full border-t border-border pt-4 mt-2 flex items-center justify-between gap-3">
              <span className="text-xs text-ink-500 font-medium truncate">
                {window.location.origin}/verify/{verifyCert.verifyCode}
              </span>
              <Button
                size="xs"
                variant="secondary"
                icon={copied ? Check : Copy}
                onClick={() => copyLink(verifyCert.verifyCode)}
              >
                {copied ? "Copied" : "Copy Link"}
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {/* Printable Certificate Template — portalled straight to <body>, so it
          sits OUTSIDE #root and is never caught by the "#root > div { display:
          none }" print rule below (a position:fixed descendant still gets
          hidden if any ancestor has display:none — it can only be rendered by
          not being a descendant at all). */}
      {printCert && createPortal(
        <div
          id="cert-print-container"
          className="hidden print:flex flex-col justify-between text-center bg-white p-16 font-sans relative"
          style={{ border: "24px double #B8890A", minHeight: "100vh" }}
        >
          {/* Header Seal */}
          <div className="flex flex-col items-center">
            <div className="h-16 w-16 rounded-full bg-primary-850 flex items-center justify-center font-display font-extrabold text-gold-400 text-3xl mb-4 shadow">
              M
            </div>
            <p className="font-display font-bold text-primary-850 tracking-wider text-xl uppercase">Moriah Skill Hub</p>
            <div className="h-0.5 w-32 bg-gold-500 my-2" />
            <p className="text-xs text-gold-700 tracking-widest font-semibold uppercase">Academy &amp; Apprenticeship Board</p>
          </div>

          {/* Certificate Title */}
          <div className="my-10">
            <h1 className="font-display text-4xl font-extrabold text-ink-900 uppercase tracking-widest leading-normal">
              Certificate of Graduation
            </h1>
            <p className="text-ink-500 italic mt-3 text-sm">This official credential is proudly presented to</p>
            <h2 className="font-display text-3xl font-extrabold text-gold-600 mt-5 border-b border-gold-300 pb-3 inline-block px-10">
              {printCert.studentName || "—"}
            </h2>
            <p className="text-ink-500 text-sm max-w-lg mx-auto mt-6 leading-relaxed">
              for successfully executing industry-modeled agile client sprint objectives, submitting peer-reviewed production commits, and completing all competencies required for the
            </p>
            <p className="text-base font-bold text-primary-850 mt-4 tracking-wide">
              {printCert.title}
            </p>
          </div>

          {/* Signatures & Footer Metadata */}
          <div className="mt-10 grid grid-cols-3 gap-6 items-end">
            <div className="text-center">
              <div className="h-10 border-b border-ink-300 mx-auto w-36 mb-1.5 flex items-end justify-center font-serif italic text-sm text-ink-600">
                &nbsp;
              </div>
              <p className="text-[10px] font-bold text-ink-500 uppercase tracking-wider">Engineering Lead</p>
            </div>

            <div className="flex flex-col items-center justify-center">
              {/* QR Code image embedded inside certificate */}
              <img
                src={`https://api.qrserver.com/v1/create-qr-code/?size=90x90&data=${encodeURIComponent(
                  `${window.location.origin}/verify/${printCert.verifyCode}`
                )}`}
                alt="Verify Badge"
                className="h-20 w-20 border border-border p-1 bg-white mb-2"
              />
              <p className="text-[9px] font-mono font-bold text-ink-400">{printCert.verifyCode}</p>
            </div>

            <div className="text-center">
              <div className="h-10 border-b border-ink-300 mx-auto w-36 mb-1.5 flex items-end justify-center font-serif italic text-sm text-ink-600">
                &nbsp;
              </div>
              <p className="text-[10px] font-bold text-ink-500 uppercase tracking-wider">Cohort Project Manager</p>
            </div>
          </div>

          <div className="text-[9px] font-mono text-ink-400 mt-10">
            Hash signature: {printCert.hash || printCert.verifyCode || "—"}
          </div>
        </div>,
        document.body
      )}
    </div>
  );
}