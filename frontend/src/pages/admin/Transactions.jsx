import { useEffect, useState } from "react";
import { RotateCcw, FileText, Download } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import ConfirmDialog from "../../components/ui/ConfirmDialog";
import { Input, Select } from "../../components/ui/FormField";
import { getTransactions, getInvoicePdfUrl, refundTransaction } from "../../services/adminService";
import { openPdfUrl } from "../../utils/pdf";
import { CURRENCY } from "../../utils/constants";
import { useToast } from "../../context/ToastContext";
import { buildInvoiceHTML, invoiceNumberFor, downloadInvoice } from "../../utils/invoiceTemplate";

// Real payment-status vocabulary from the backend (payment.entity.PaymentStatus /
// the chk_payments_status CHECK constraint). This page used to invent its own
// "Success / Refunded / Failed" labels for a localStorage mock; it now reads the
// live /api/v1/admin/payments ledger, so the filters, badges and actions all key
// off the actual enum.
const STATUS_OPTIONS = [
  { value: "CREATED", label: "Created" },
  { value: "PENDING", label: "Pending" },
  { value: "CAPTURED", label: "Captured" },
  { value: "FAILED", label: "Failed" },
  { value: "REFUNDED", label: "Refunded" },
];

const STATUS_TONE = {
  CAPTURED: "success",
  REFUNDED: "neutral",
  FAILED: "error",
  CREATED: "warning",
  PENDING: "warning",
};

export default function AdminTransactions() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [confirmId, setConfirmId] = useState(null);
  const [refunding, setRefunding] = useState(false);

  // Search & Filters state
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [gatewayFilter, setGatewayFilter] = useState("");

  const { notify } = useToast();

  // Invoice preview modal state
  const [invoiceOpen, setInvoiceOpen] = useState(false);
  const [invoiceTxn, setInvoiceTxn] = useState(null);

  const load = () => {
    setLoading(true);
    getTransactions()
      .then((t) => setRows(t))
      .catch(() => notify("Couldn't load transactions.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // A refund is a real gateway operation (POST /admin/payments/{id}/refund) — only
  // valid for a CAPTURED payment, once. The gateway's refund webhook reconciles
  // the final state; we optimistically reflect the returned row here.
  const refund = async () => {
    if (!confirmId) return;
    setRefunding(true);
    try {
      const updated = await refundTransaction(confirmId);
      setRows((list) => list.map((r) => (r.id === confirmId ? { ...r, ...updated } : r)));
      notify("Refund issued through the original payment gateway.", { type: "success" });
      setConfirmId(null);
    } catch (err) {
      notify(err?.message || "The gateway rejected this refund.", { type: "error" });
    } finally {
      setRefunding(false);
    }
  };

  // Previews the transaction's GST-style tax invoice (or credit note for a
  // refund) in-app. The admin can then download it — the real server-generated
  // PDF when one has been issued, otherwise a client-rendered copy.
  const handleInvoice = (txn) => {
    setInvoiceTxn(txn);
    setInvoiceOpen(true);
  };

  const handleInvoiceDownload = async () => {
    if (!invoiceTxn) return;
    if (invoiceTxn.invoiceStatus === "ISSUED") {
      try {
        const url = await getInvoicePdfUrl(invoiceTxn.gatewayOrderId || invoiceTxn.id);
        if (url) {
          openPdfUrl(url, () => notify("Allow popups to open the invoice PDF.", { type: "warning" }));
          return;
        }
      } catch {
        /* fall through to the client-rendered copy */
      }
    }
    downloadInvoice(invoiceTxn);
    notify(`Tax invoice downloaded for ${invoiceTxn.id}.`, { type: "success" });
  };

  const filtered = rows.filter((r) => {
    const q = search.toLowerCase();
    const matchesSearch =
      (r.student || "").toLowerCase().includes(q) || (r.id || "").toLowerCase().includes(q);
    const matchesStatus = !statusFilter || r.status === statusFilter;
    const matchesGateway = !gatewayFilter || r.gateway === gatewayFilter;
    return matchesSearch && matchesStatus && matchesGateway;
  });

  return (
    <div>
      <PageHeader
        title="Transactions & Refunds"
        subtitle="Monitor Stripe / Razorpay payments, issue refunds, and generate tax invoices"
        breadcrumbs={[{ label: "Dashboard", to: "/admin/dashboard" }, { label: "Transactions" }]}
      />

      <Card>
        <div className="flex flex-col sm:flex-row gap-3 mb-4">
          <Input placeholder="Search student name or order id…" value={search} onChange={(e) => setSearch(e.target.value)} className="sm:max-w-xs" />
          <Select
            placeholder="All statuses"
            options={STATUS_OPTIONS}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="sm:max-w-xs"
          />
          <Select
            placeholder="All gateways"
            options={[{ value: "RAZORPAY", label: "Razorpay" }, { value: "STRIPE", label: "Stripe" }]}
            value={gatewayFilter}
            onChange={(e) => setGatewayFilter(e.target.value)}
            className="sm:max-w-xs"
          />
        </div>
        <Table
          loading={loading}
          data={filtered}
          columns={[
            { key: "id", header: "Order", className: "text-left font-mono text-xs" },
            { key: "student", header: "Student", className: "text-left" },
            { key: "plan", header: "Plan", className: "text-left" },
            { key: "amount", header: "Amount", className: "text-left", render: (r) => CURRENCY(r.amount) },
            { key: "gateway", header: "Gateway", className: "text-left", render: (r) => <span className="capitalize">{(r.gateway || "").toLowerCase()}</span> },
            { key: "date", header: "Date", className: "text-left" },
            { key: "status", header: "Status", className: "text-left", render: (r) => <Badge tone={STATUS_TONE[r.status] || "neutral"}>{r.status}</Badge> },
            { key: "invoice", header: "Invoice", className: "text-left", render: (r) => {
              if (r.invoiceNumber) return <span className="text-xs font-mono text-ink-600">{r.invoiceNumber}</span>;
              if (r.invoiceStatus === "PROCESSING") return <Badge tone="warning">generating…</Badge>;
              if (r.invoiceStatus === "FAILED") return <Badge tone="error">failed</Badge>;
              return <span className="text-xs text-ink-400">—</span>;
            } },
            { key: "action", header: "", className: "text-right", render: (r) => (
              <div className="flex gap-2 justify-end">
                {(r.status === "CAPTURED" || r.status === "REFUNDED") && (
                  <Button size="sm" variant="secondary" icon={FileText} onClick={() => handleInvoice(r)}>
                    {r.status === "REFUNDED" ? "Credit Note" : "Tax Invoice"}
                  </Button>
                )}
                {r.status === "CAPTURED" && (
                  <Button size="sm" variant="secondary" icon={RotateCcw} onClick={() => setConfirmId(r.id)}>Refund</Button>
                )}
              </div>
            ) },
          ]}
        />
      </Card>

      {/* Tax Invoice / Credit Note Preview Modal */}
      <Modal
        open={invoiceOpen}
        onClose={() => setInvoiceOpen(false)}
        title={invoiceTxn ? `${invoiceTxn.status === "REFUNDED" ? "Credit Note" : "Tax Invoice"} — ${invoiceNumberFor(invoiceTxn)}` : "Invoice"}
        size="xl"
        footer={<>
          <Button variant="secondary" onClick={() => setInvoiceOpen(false)}>Close</Button>
          <Button icon={Download} onClick={handleInvoiceDownload}>Download</Button>
        </>}
      >
        {invoiceTxn && (
          <iframe
            title={`invoice-${invoiceTxn.id}`}
            srcDoc={buildInvoiceHTML(invoiceTxn)}
            className="w-full h-[65vh] rounded-lg border border-border bg-white"
          />
        )}
      </Modal>

      <ConfirmDialog
        open={!!confirmId}
        onClose={() => setConfirmId(null)}
        onConfirm={refund}
        loading={refunding}
        title="Process refund?"
        description="This issues a full refund through the original payment gateway and cannot be undone."
        confirmLabel="Process Refund"
        tone="danger"
      />
    </div>
  );
}
