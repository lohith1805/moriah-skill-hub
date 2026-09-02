import { useState } from "react";
import {
  FileSpreadsheet, FileText, FileJson, Download, Eye,
  Building, IndianRupee, Users, ShieldCheck, CheckCircle2, Sparkles
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import Badge from "../../components/ui/Badge";
import { exportReport } from "../../services/adminService";
import { useToast } from "../../context/ToastContext";

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

export default function AdminReports() {
  const [exportingId, setExportingId] = useState(null);
  const [viewingReport, setViewingReport] = useState(null);
  const { notify } = useToast();

  const downloadReport = (report) => {
    let content = `MORIAH SKILL HUB — OFFICIAL CORPORATE REPORT\n`;
    content += `Title: ${report.title}\n`;
    content += `Category: ${report.category}\n`;
    content += `Generated At: ${new Date().toLocaleString()}\n`;
    content += `Format: .${report.format}\n\n`;

    content += report.headers.join(",") + "\n";
    report.rows.forEach((row) => {
      content += row.map((cell) => `"${cell}"`).join(",") + "\n";
    });

    const honestExt = report.format === "pdf" ? "preview.txt" : "csv";
    const blob = new Blob([content], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${report.title.toLowerCase().replace(/[^a-z0-9]/g, "_")}_${new Date().toISOString().slice(0, 10)}.${honestExt}`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  };

  const runExport = async (report) => {
    setExportingId(report.id);
    try {
      await exportReport(report.format);
      downloadReport(report);
      notify(`"${report.title}" exported successfully.`, { type: "success", title: "Report Exported" });
    } finally {
      setExportingId(null);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Automated Reporting Engine"
        subtitle="Export platform metrics to Excel (.xlsx), PDF, and CSV for corporate stakeholders and compliance (MSH-FR-ADM-05)"
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
                  .{r.format.toUpperCase()}
                </Badge>
              </div>

              <h3 className="font-semibold text-ink-900 text-sm">{r.title}</h3>
              <p className="text-xs text-ink-500 mt-1 leading-relaxed">{r.description}</p>
              <p className="text-[11px] text-primary-800 font-medium mt-2">Category: {r.category}</p>
            </div>

            <div className="flex gap-2 justify-end mt-4 pt-3 border-t border-border">
              <Button size="sm" variant="secondary" icon={Eye} onClick={() => setViewingReport(r)}>
                Preview Data
              </Button>
              <Button size="sm" icon={Download} loading={exportingId === r.id} onClick={() => runExport(r)}>
                Export File
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
            <span className="text-xs text-ink-400 font-mono">Format: .{viewingReport?.format.toUpperCase()}</span>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setViewingReport(null)}>Close</Button>
              <Button icon={Download} onClick={() => { downloadReport(viewingReport); setViewingReport(null); }}>
                Download Full Report
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
    </div>
  );
}
