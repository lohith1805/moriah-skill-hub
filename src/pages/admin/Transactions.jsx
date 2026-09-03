import { useEffect, useState } from "react";
import { RotateCcw, Plus, Edit, Trash2, FileText, Download } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import ConfirmDialog from "../../components/ui/ConfirmDialog";
import { Input, Select } from "../../components/ui/FormField";
import { getTransactions, getInvoicePdfUrl } from "../../services/adminService";
import { openPdfUrl } from "../../utils/pdf";
import { CURRENCY } from "../../utils/constants";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import { buildInvoiceHTML, invoiceNumberFor, downloadInvoice } from "../../utils/invoiceTemplate";

export default function AdminTransactions() {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [confirmId, setConfirmId] = useState(null);
  
  // Search & Filters state
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [gatewayFilter, setGatewayFilter] = useState("");

  // Create states
  const [createOpen, setCreateOpen] = useState(false);
  const [values, setValues] = useState({ student: "", plan: "", amount: "", gateway: "", date: "", status: "Success" });

  // Edit states
  const [editOpen, setEditOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState({ student: "", plan: "", amount: "", gateway: "", date: "", status: "" });

  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  // Invoice preview modal state
  const [invoiceOpen, setInvoiceOpen] = useState(false);
  const [invoiceTxn, setInvoiceTxn] = useState(null);

  // Bug fix: this page used to snapshot transactions into a separate
  // "msh_admin_transactions" key on first load, then only ever read that
  // frozen snapshot again — so any transaction written later (e.g. a real
  // student registration payment, which writes to "msh_transactions") never
  // showed up here. Now this page always reads/writes the same canonical
  // "msh_transactions" key that authService/adminService use, so it's
  // always in sync with real activity.
  useEffect(() => {
    getTransactions().then((t) => {
      setRows(t);
      setLoading(false);
    });
  }, []);

  const persist = (updated) => {
    setRows(updated);
    localStorage.setItem("msh_transactions", JSON.stringify(updated));
  };

  const refund = () => {
    const updated = rows.map((r) => (r.id === confirmId ? { ...r, status: "Refunded" } : r));
    persist(updated);
    notify("Refund processed via original payment gateway.", { type: "success" });
    setConfirmId(null);
  };

  const onCreate = (e) => {
    e.preventDefault();
    const validation = validateForm(values, { student: [required], plan: [required], amount: [required], gateway: [required], date: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const created = {
      id: `tx_${Date.now()}`,
      student: values.student,
      plan: values.plan,
      amount: Number(values.amount) || 0,
      gateway: values.gateway,
      date: values.date,
      status: values.status
    };

    const updated = [created, ...rows];
    persist(updated);
    notify("Transaction recorded successfully.", { type: "success" });
    setCreateOpen(false);
    setValues({ student: "", plan: "", amount: "", gateway: "", date: "", status: "Success" });
  };

  const openEdit = (item) => {
    setEditingId(item.id);
    setEditValues({
      student: item.student,
      plan: item.plan,
      amount: String(item.amount),
      gateway: item.gateway,
      date: item.date,
      status: item.status
    });
    setErrors({});
    setEditOpen(true);
  };

  const onEditSave = (e) => {
    e.preventDefault();
    const validation = validateForm(editValues, { student: [required], plan: [required], amount: [required], gateway: [required], date: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const updated = rows.map((item) => {
      if (item.id === editingId) {
        return {
          ...item,
          student: editValues.student,
          plan: editValues.plan,
          amount: Number(editValues.amount) || 0,
          gateway: editValues.gateway,
          date: editValues.date,
          status: editValues.status
        };
      }
      return item;
    });

    persist(updated);
    notify("Transaction details updated.", { type: "success" });
    setEditOpen(false);
    setEditingId(null);
  };

  const handleDelete = (id) => {
    const target = rows.find(r => r.id === id);
    const updated = rows.filter((r) => r.id !== id);
    persist(updated);
    notify(`Transaction ${target?.id} deleted successfully.`, { type: "success" });
  };

  // Generates a GST-style tax invoice (or credit note for refunds) for the
  // transaction and previews it in-app in a modal, instead of opening a new
  // browser tab. The admin can then download that single transaction's
  // invoice as its own .html file straight from the modal.
  const handleInvoice = (txn) => {
    setInvoiceTxn(txn);
    setInvoiceOpen(true);
  };

  const handleInvoiceDownload = async () => {
    if (!invoiceTxn) return;
    // Prefer the real server-generated invoice PDF; fall back to a client-built
    // one for payments whose async invoice hasn't been issued (older rows, or
    // still PROCESSING).
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
    const matchesSearch = r.student.toLowerCase().includes(search.toLowerCase()) || r.id.toLowerCase().includes(search.toLowerCase());
    const matchesStatus = !statusFilter || r.status === statusFilter;
    const matchesGateway = !gatewayFilter || r.gateway === gatewayFilter;
    return matchesSearch && matchesStatus && matchesGateway;
  });

  return (
    <div>
      <PageHeader
        title="Transactions & Refunds"
        subtitle="Monitor Stripe/Razorpay transactions, manage refunds, and generate tax invoices"
        breadcrumbs={[{ label: "Dashboard", to: "/admin/dashboard" }, { label: "Transactions" }]}
        action={<Button icon={Plus} onClick={() => { setErrors({}); setValues({ student: "", plan: "", amount: "", gateway: "", date: "", status: "Success" }); setCreateOpen(true); }}>Record Transaction</Button>}
      />

      <Card>
        <div className="flex flex-col sm:flex-row gap-3 mb-4">
          <Input placeholder="Search student name or txn id…" value={search} onChange={(e) => setSearch(e.target.value)} className="sm:max-w-xs" />
          <Select
            placeholder="All statuses"
            options={[{ value: "Success", label: "Success" }, { value: "Refunded", label: "Refunded" }, { value: "Failed", label: "Failed" }]}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="sm:max-w-xs"
          />
          <Select
            placeholder="All gateways"
            options={[{ value: "Stripe", label: "Stripe" }, { value: "Razorpay", label: "Razorpay" }, { value: "Offline", label: "Offline" }]}
            value={gatewayFilter}
            onChange={(e) => setGatewayFilter(e.target.value)}
            className="sm:max-w-xs"
          />
        </div>
        <Table
          loading={loading}
          data={filtered}
          columns={[
            { key: "id", header: "Transaction", className: "text-left font-mono text-xs" },
            { key: "student", header: "Student", className: "text-left" },
            { key: "plan", header: "Plan", className: "text-left" },
            { key: "amount", header: "Amount", className: "text-left", render: (r) => CURRENCY(r.amount) },
            { key: "gateway", header: "Gateway", className: "text-left" },
            { key: "date", header: "Date", className: "text-left" },
            { key: "status", header: "Status", className: "text-left", render: (r) => <Badge tone={r.status === "Success" ? "success" : r.status === "Refunded" ? "neutral" : "error"}>{r.status}</Badge> },
            { key: "invoice", header: "Invoice", className: "text-left", render: (r) => {
              if (r.invoiceNumber) return <span className="text-xs font-mono text-ink-600">{r.invoiceNumber}</span>;
              if (r.invoiceStatus === "PROCESSING") return <Badge tone="warning">generating…</Badge>;
              if (r.invoiceStatus === "FAILED") return <Badge tone="error">failed</Badge>;
              return <span className="text-xs text-ink-400">—</span>;
            } },
            { key: "action", header: "", className: "text-right", render: (r) => (
              <div className="flex gap-2 justify-end">
                {(r.status === "Success" || r.status === "Refunded") && (
                  <Button size="sm" variant="secondary" icon={FileText} onClick={() => handleInvoice(r)}>
                    {r.status === "Refunded" ? "Credit Note" : "Tax Invoice"}
                  </Button>
                )}
                {r.status === "Success" && (
                  <Button size="sm" variant="secondary" icon={RotateCcw} onClick={() => setConfirmId(r.id)}>Refund</Button>
                )}
                <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(r)}>Edit</Button>
                <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r.id)}>Delete</Button>
              </div>
            ) },
          ]}
        />
      </Card>

      {/* Record Transaction Modal */}
      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Record Transaction Details"
        footer={<>
          <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button icon={Plus} onClick={onCreate}>Record Transaction</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={onCreate}>
          <Input label="Student Name" required value={values.student} onChange={(e) => setValues((v) => ({ ...v, student: e.target.value }))} error={errors.student} />
          <Input label="Plan" required placeholder="e.g. Internship, Professional" value={values.plan} onChange={(e) => setValues((v) => ({ ...v, plan: e.target.value }))} error={errors.plan} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Amount (₹)" type="number" min="0" required value={values.amount} onChange={(e) => setValues((v) => ({ ...v, amount: e.target.value }))} error={errors.amount} />
            <Input label="Date" type="date" required value={values.date} onChange={(e) => setValues((v) => ({ ...v, date: e.target.value }))} error={errors.date} />
          </div>
          <div className="grid sm:grid-cols-2 gap-4">
            <Select label="Gateway" required placeholder="Select gateway" options={[{ value: "Stripe", label: "Stripe" }, { value: "Razorpay", label: "Razorpay" }, { value: "Offline", label: "Offline Transfer" }]} value={values.gateway} onChange={(e) => setValues((v) => ({ ...v, gateway: e.target.value }))} error={errors.gateway} />
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-ink-900">Transaction Status</span>
              <select
                value={values.status}
                onChange={(e) => setValues((v) => ({ ...v, status: e.target.value }))}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 bg-white"
              >
                <option value="Success">Success</option>
                <option value="Refunded">Refunded</option>
                <option value="Failed">Failed</option>
              </select>
            </div>
          </div>
        </form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        title="Edit Transaction Record"
        footer={<>
          <Button variant="secondary" onClick={() => setEditOpen(false)}>Cancel</Button>
          <Button onClick={onEditSave}>Save Changes</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={onEditSave}>
          <Input label="Student Name" required value={editValues.student} onChange={(e) => setEditValues((v) => ({ ...v, student: e.target.value }))} error={errors.student} />
          <Input label="Plan" required value={editValues.plan} onChange={(e) => setEditValues((v) => ({ ...v, plan: e.target.value }))} error={errors.plan} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Amount (₹)" type="number" min="0" required value={editValues.amount} onChange={(e) => setEditValues((v) => ({ ...v, amount: e.target.value }))} error={errors.amount} />
            <Input label="Date" type="date" required value={editValues.date} onChange={(e) => setEditValues((v) => ({ ...v, date: e.target.value }))} error={errors.date} />
          </div>
          <div className="grid sm:grid-cols-2 gap-4">
            <Select label="Gateway" required placeholder="Select gateway" options={[{ value: "Stripe", label: "Stripe" }, { value: "Razorpay", label: "Razorpay" }, { value: "Offline", label: "Offline Transfer" }]} value={editValues.gateway} onChange={(e) => setEditValues((v) => ({ ...v, gateway: e.target.value }))} error={errors.gateway} />
            <div className="flex flex-col gap-1.5">
              <span className="text-sm font-medium text-ink-900">Transaction Status</span>
              <select
                value={editValues.status}
                onChange={(e) => setEditValues((v) => ({ ...v, status: e.target.value }))}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 bg-white"
              >
                <option value="Success">Success</option>
                <option value="Refunded">Refunded</option>
                <option value="Failed">Failed</option>
              </select>
            </div>
          </div>
        </form>
      </Modal>

      {/* Tax Invoice / Credit Note Preview Modal */}
      <Modal
        open={invoiceOpen}
        onClose={() => setInvoiceOpen(false)}
        title={invoiceTxn ? `${invoiceTxn.status === "Refunded" ? "Credit Note" : "Tax Invoice"} — ${invoiceNumberFor(invoiceTxn)}` : "Invoice"}
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
        title="Process refund?"
        description="This issues a full refund via the original payment gateway and cannot be undone."
        confirmLabel="Process Refund"
        tone="danger"
      />
    </div>
  );
}