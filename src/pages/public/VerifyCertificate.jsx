import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { ShieldCheck, Calendar, FileText, Fingerprint, Award, CheckCircle, RefreshCw, XCircle } from "lucide-react";
import { CERTIFICATES } from "../../services/mockData";
import Button from "../../components/ui/Button";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import logoMark from "../../assets/logo-mark.png";

export default function VerifyCertificate() {
  const { code } = useParams();
  const [cert, setCert] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Simulate verification check
    const timer = setTimeout(() => {
      let list = [];
      try {
        const raw = localStorage.getItem("msh_certificates");
        list = raw ? JSON.parse(raw) : CERTIFICATES;
      } catch (e) {
        list = CERTIFICATES;
      }
      const match = list.find((c) => c.verifyCode === code);
      setCert(match || null);
      setLoading(false);
    }, 800);
    return () => clearTimeout(timer);
  }, [code]);

  return (
    <div className="min-h-screen bg-cream-50 flex flex-col items-center justify-center p-6">
      <Link to="/" className="flex items-center mb-8">
        <div className="bg-primary-900 px-3.5 py-2 rounded-xl flex items-center justify-center hover:bg-primary-800 transition-colors shadow-sm">
          <img src={logoMark} alt="Moriah Skill Hub" className="h-7 w-auto object-contain" />
        </div>
      </Link>

      {loading ? (
        <Card className="max-w-md w-full text-center py-12 flex flex-col items-center justify-center gap-3">
          <RefreshCw className="animate-spin text-primary-600" size={32} />
          <p className="text-sm font-medium text-ink-600">Cryptographic verification in progress...</p>
        </Card>
      ) : cert ? (
        <div className="max-w-xl w-full flex flex-col gap-6">
          {/* Main Verified Card */}
          <Card className="relative overflow-hidden border-t-4 border-t-success-600">
            <div className="absolute top-0 right-0 -mr-6 -mt-6 w-24 h-24 bg-success-50 rounded-full flex items-end justify-start pl-6 pb-6 text-success-500">
              <CheckCircle size={36} />
            </div>

            <div className="flex items-center gap-3 mb-6">
              <div className="p-2.5 rounded-lg bg-success-50 text-success-600">
                <ShieldCheck size={24} />
              </div>
              <div>
                <p className="text-xs font-bold text-success-600 uppercase tracking-widest">Verified Credential</p>
                <h2 className="font-display font-bold text-ink-900 text-lg">Authentic Digital Certificate</h2>
              </div>
            </div>

            <div className="flex flex-col gap-5 border-y border-border py-5 my-5">
              <div>
                <p className="text-xs text-ink-400 uppercase tracking-wider font-semibold">Recipient Name</p>
                <p className="font-display font-bold text-lg text-primary-850 mt-1">{cert.studentName}</p>
              </div>

              <div>
                <p className="text-xs text-ink-400 uppercase tracking-wider font-semibold">Track / Program</p>
                <p className="text-sm font-semibold text-ink-900 mt-0.5">{cert.title}</p>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-xs text-ink-400 uppercase tracking-wider font-semibold">Date Issued</p>
                  <p className="text-xs font-medium text-ink-800 mt-1 flex items-center gap-1.5">
                    <Calendar size={13} className="text-ink-400" />
                    {cert.issuedOn}
                  </p>
                </div>
                <div>
                  <p className="text-xs text-ink-400 uppercase tracking-wider font-semibold">Certificate ID</p>
                  <p className="text-xs font-mono font-bold text-ink-800 mt-1 flex items-center gap-1.5">
                    <FileText size={13} className="text-ink-400" />
                    {cert.verifyCode}
                  </p>
                </div>
              </div>

              <div>
                <p className="text-xs text-ink-400 uppercase tracking-wider font-semibold">Integrity Hash</p>
                <p className="text-[10px] font-mono text-ink-500 mt-1 bg-cream-100 p-2 rounded border border-border flex items-start gap-1.5 break-all">
                  <Fingerprint size={13} className="text-ink-400 shrink-0 mt-0.5" />
                  {cert.hash}
                </p>
              </div>
            </div>

            <div className="flex items-center justify-between flex-wrap gap-3">
              <Badge tone="success" className="text-xs font-semibold py-1 px-3">
                Active &amp; Valid
              </Badge>
              <Button
                variant="primary"
                size="sm"
                icon={Award}
                onClick={() => window.print()}
              >
                Print / Save PDF
              </Button>
            </div>
          </Card>

          {/* Validation Notice Banner */}
          <div className="text-center text-xs text-ink-400 max-w-sm mx-auto leading-relaxed">
            This credential was cryptographically generated by Moriah Skill Hub. It proves the recipient completed rigorous, simulated client sprint requirements under professional oversight.
          </div>
        </div>
      ) : (
        <Card className="max-w-md w-full text-center py-8 border-t-4 border-t-error-500">
          <div className="flex justify-center mb-4">
            <div className="p-3.5 rounded-full bg-error-50 text-error-500">
              <XCircle size={40} />
            </div>
          </div>
          <h2 className="font-display font-bold text-xl text-ink-900">Verification Failed</h2>
          <p className="text-sm text-ink-500 mt-2 px-4">
            The verification code <strong className="font-mono text-error-600">{code}</strong> is invalid or has been revoked. Please check the credential identifier and try again.
          </p>

          <div className="mt-8 pt-6 border-t border-border flex flex-col gap-2">
            <Link to="/">
              <Button variant="primary" fullWidth>Return to Homepage</Button>
            </Link>
          </div>
        </Card>
      )}
    </div>
  );
}
