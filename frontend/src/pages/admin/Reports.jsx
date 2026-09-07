import { useState } from "react";
import {
  FileSpreadsheet, FileText, FileJson, Download, Eye,
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import Badge from "../../components/ui/Badge";
import { useToast } from "../../context/ToastContext";
import { downloadPdf } from "../../utils/pdf";

const REPORTS = [
  {
    id: "r1",
    title: "Executive Revenue & MRR / ARR Financial Report",
    category: "Finance & Growth",
    format: "xlsx",
    icon: FileSpreadsheet,
    description: "Consolidated monthly recurring revenue, subscription tier distributions, and transaction ledger.",
    headers: ["Period", "Active Subscriptions", "MRR (₹)", "ARR Run-rate (₹)", "Payment Gateway Status"],
    rows: [
      ["April 2026", "280", "12,00,000", "1,44,00,000", "100% Success (Stripe / Razorpay)"],
      ["May 2026", "315", "13,50,000", "1,62,00,000", "100% Success"],
      ["June 2026", "350", "15,20,000", "1,82,40,000", "99.8% Success"],
      ["July 2026", "390", "16,90,000", "2,02,80,000", "100% Success"],
      ["August 2026", "430", "18,50,000", "2,22,00,000", "100% Success"]
    ]
  },
  {
    id: "r2",
    title: "Academic Batch Pass Rates & Sprint Analytics",
    category: "Academic & Training",
    format: "pdf",
    icon: FileText,
    description: "Assessment scores, code review pass rates, and graduation approval statistics per batch.",
    headers: ["Batch ID", "Cohort Track", "Enrolled Students", "Graduation Pass Rate", "PIP Ratio"],
    rows: [
      ["FS-Batch-12", "Full Stack Java Sprint Track", "32", "93.8%", "3.1%"],
      ["FS-Batch-13", "MERN Agile Sprint Track", "28", "92.4%", "4.2%"],
      ["FS-Batch-14", "AI & Cloud Sprint Track", "24", "95.0%", "2.0%"]
    ]
  },
  {
    id: "r3",
    title: "CRM Sales Pipeline & Conversion Velocity Report",
    category: "Growth & Sales",
    format: "csv",
    icon: FileJson,
    description: "Multi-channel lead ingestion sources, conversion rates, and executive quota performance.",
    headers: ["Ingestion Channel", "Total Leads", "Demo Completed", "Enrolled / Won", "Conversion %", "Revenue Generated"],
    rows: [
      ["Landing Page (Direct B2C)", "140", "65", "28", "20.0%", "₹4,19,972"],
      ["College Tie-ups (B2B2C)", "45", "30", "12", "26.6%", "₹14,40,000"],
      ["Corporate Inquiries (B2B)", "25", "18", "8", "32.0%", "₹20,00,000"],
      ["Social Media & Referral", "60", "25", "10", "16.6%", "₹1,49,990"]
    ]
  },
  {
    id: "r4",
    title: "HR Payroll & Attendance Compliance Audit",
    category: "Human Resources",
    format: "xlsx",
    icon: FileSpreadsheet,
    description: "Hourly trainer session disbursements, monthly base salaries, and biometric clock-in rates.",
    headers: ["Department", "Headcount", "Attendance Avg.", "Monthly Payroll (₹)", "Compliance Status"],
    rows: [
      ["Engineering & Trainers", "6", "95.4%", "₹4,10,000", "Audited & Verified"],
      ["Growth & Sales", "3", "97.2%", "₹1,45,000", "Audited & Verified"],
      ["Product & Operations", "4", "94.0%", "₹2,20,000", "Audited & Verified"]
    ]
  },
  {
    id: "r5",
    title: "System Security & Tamper-Proof Audit Trail Export",
    category: "Security & Compliance",
    format: "pdf",
    icon: FileText,
    description: "Authentication logs, RBAC permission edits, and financial change histories.",
    headers: ["Event Category", "Logged Events", "Anomalies / Blocked", "2FA Compliance", "System Status"],
    rows: [
      ["Authentication & Logins", "1,420", "2 blocked attempts", "100%", "Secure"],
      ["Role Permission Edits", "14", "0 unauthorized", "100%", "Logged"],
      ["Financial Transactions", "52", "0 discrepancies", "100%", "Reconciled"]
    ]
  }
];

const FORMATS = [
  { value: "csv", label: "CSV spreadsheet (.csv)" },
  { value: "pdf", label: "PDF document (.pdf)" },
  { value: "txt", label: "Plain text (.txt)" },
];

const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "");
const stamp = () => new Date().toISOString().slice(0, 10);

function saveBlob(filename, text, mime) {
  const blob = new Blob([text], { type: `${mime};charset=utf-8;` });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

function toCsv(report) {
  const esc = (c) => `"${String(c).replace(/"/g, '""')}"`;
  const lines = [report.headers.map(esc).join(",")];
  report.rows.forEach((row) => lines.push(row.map(esc).join(",")));
  return lines.join("\r\n");
}

function toText(report) {
  const cols = report.headers.map((h, i) =>
    Math.max(h.length, ...report.rows.map((r) => String(r[i] ?? "").length)));
  const fmtRow = (cells) => cells.map((c, i) => String(c ?? "").padEnd(cols[i])).join("  |  ");
  const head = fmtRow(report.headers);
  return [
    "MORIAH SKILL HUB — OFFICIAL CORPORATE REPORT",
    `Title:        ${report.title}`,
    `Category:     ${report.category}`,
    `Generated at: ${new Date().toLocaleString()}`,
    "",
    head,
    "-".repeat(head.length),
    ...report.rows.map((r) => fmtRow(r)),
  ].join("\n");
}

function download(report, format) {
  const base = `${slug(report.title)}_${stamp()}`;
  if (format === "csv") return saveBlob(`${base}.csv`, toCsv(report), "text/csv");
  if (format === "txt") return saveBlob(`${base}.txt`, toText(report), "text/plain");
  // pdf
  const colHeader = report.headers.join("  •  ");
  const lines = [colHeader, ...report.rows.map((r) => r.join("  •  "))];
  downloadPdf(`${base}.pdf`, report.title, [
    { keyValues: [["Category", report.category], ["Generated", new Date().toLocaleString()]] },
    { heading: "Data", lines },
  ]);
}

export default function AdminReports() {
  const { notify } = useToast();
  const [viewingReport, setViewingReport] = useState(null);
  const [exportFor, setExportFor] = useState(null); // report awaiting a format choice
  const [format, setFormat] = useState("csv");

  const openExport = (report) => { setFormat(report.format === "csv" ? "csv" : report.format === "pdf" ? "pdf" : "csv"); setExportFor(report); };

  const confirmExport = () => {
    try {
      download(exportFor, format);
      notify(`"${exportFor.title}" downloaded as .${format}.`, { type: "success", title: "Report exported" });
    } catch (e) {
      notify(e.message || "Could not generate the file.", { type: "error" });
    } finally {
      setExportFor(null);
      setViewingReport(null);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Automated Reporting Engine"
        subtitle="Export platform metrics as CSV, PDF, or plain text for corporate stakeholders and compliance (MSH-FR-ADM-05)"
        breadcrumbs={[{ label: "Dashboard", to: "/admin/dashboard" }, { label: "Reporting Engine" }]}
      />

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {REPORTS.map((r) => (
          <Card key={r.id} className="text-left flex flex-col justify-between p-5 hover:border-primary-300 transition-colors">
            <div>
              <div className="flex items-start justify-between gap-3 mb-3">
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary-50 text-primary-700">
                  <r.icon size={22} />
                </div>
                <Badge tone={r.format === "xlsx" ? "success" : r.format === "pdf" ? "primary" : "gold"}>
                  {r.category}
                </Badge>
              </div>

              <h3 className="font-semibold text-ink-900 text-sm">{r.title}</h3>
              <p className="text-xs text-ink-500 mt-1 leading-relaxed">{r.description}</p>
            </div>

            <div className="flex gap-2 justify-end mt-4 pt-3 border-t border-border">
              <Button size="sm" variant="secondary" icon={Eye} onClick={() => setViewingReport(r)}>
                Preview Data
              </Button>
              <Button size="sm" icon={Download} onClick={() => openExport(r)}>
                Export
              </Button>
            </div>
          </Card>
        ))}
      </div>

      {/* View Report Data Modal */}
      <Modal
        open={!!viewingReport}
        onClose={() => setViewingReport(null)}
        title={viewingReport?.title || "Report Preview"}
        size="lg"
        footer={
          <div className="flex justify-between w-full items-center">
            <span className="text-xs text-ink-400">{viewingReport?.category}</span>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setViewingReport(null)}>Close</Button>
              <Button icon={Download} onClick={() => openExport(viewingReport)}>
                Download…
              </Button>
            </div>
          </div>
        }
      >
        {viewingReport && (
          <div className="flex flex-col gap-4 font-sans text-left">
            <div className="p-3 bg-cream-50 rounded-lg border border-border flex justify-between items-center text-xs">
              <div>
                <span className="text-ink-500">Report Category: </span>
                <strong className="text-ink-900">{viewingReport.category}</strong>
              </div>
              <span className="text-ink-400">Generated: {new Date().toLocaleDateString()}</span>
            </div>

            <div className="overflow-x-auto rounded-xl border border-border">
              <table className="w-full border-collapse text-xs text-left">
                <thead>
                  <tr className="bg-cream-100/90 border-b border-border">
                    {viewingReport.headers.map((h, i) => (
                      <th key={i} className="px-3.5 py-2.5 font-semibold text-ink-800">{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {viewingReport.rows.map((row, rIdx) => (
                    <tr key={rIdx} className="hover:bg-cream-50/50">
                      {row.map((cell, cIdx) => (
                        <td key={cIdx} className="px-3.5 py-2.5 text-ink-700">{cell}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </Modal>

      {/* Format picker */}
      <Modal
        open={!!exportFor}
        onClose={() => setExportFor(null)}
        title="Choose a download format"
        description={exportFor?.title}
        footer={
          <>
            <Button variant="secondary" onClick={() => setExportFor(null)}>Cancel</Button>
            <Button icon={Download} onClick={confirmExport}>Download</Button>
          </>
        }
      >
        <div className="flex flex-col gap-2 text-left">
          {FORMATS.map((f) => (
            <label key={f.value} className={`flex items-center gap-3 rounded-lg border p-3 cursor-pointer text-sm ${format === f.value ? "border-primary-500 bg-primary-50/50" : "border-border"}`}>
              <input type="radio" name="report-format" value={f.value} checked={format === f.value} onChange={() => setFormat(f.value)} />
              {f.label}
            </label>
          ))}
        </div>
      </Modal>
    </div>
  );
}
