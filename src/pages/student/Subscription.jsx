import { useEffect, useState } from "react";
import { Check, CreditCard, Download } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import Badge from "../../components/ui/Badge";
import Table from "../../components/ui/Table";
import Modal from "../../components/ui/Modal";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getPlans, subscribeToPlan, getMySubscription, getMyInvoices, previewCheckout } from "../../services/studentService";
import { downloadPdf } from "../../utils/pdf";
import { useToast } from "../../context/ToastContext";
import { CURRENCY } from "../../utils/constants";
import { useAuth } from "../../context/AuthContext";
import { useNavigate } from "react-router-dom";

const TRACK_CODES = [
  { value: "FULL_STACK", label: "Full-Stack Development" },
  { value: "DATA_ANALYTICS", label: "Data Analytics" },
  { value: "PRODUCT_DESIGN", label: "Product Design" },
  { value: "BACKEND_ENGINEERING", label: "Backend Engineering" },
];

const loadRazorpayScript = () =>
  new Promise((resolve) => {
    if (window.Razorpay) return resolve(true);
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });

// The subscription is activated by the (signed) gateway webhook, not by the
// browser — after the widget closes, poll /subscriptions/me a few times.
async function waitForActivation(tries = 5) {
  for (let i = 0; i < tries; i += 1) {
    // eslint-disable-next-line no-await-in-loop
    const sub = await getMySubscription().catch(() => null);
    if (sub && sub.status === "ACTIVE") return sub;
    // eslint-disable-next-line no-await-in-loop
    await new Promise((r) => setTimeout(r, 2000));
  }
  return null;
}

export default function StudentSubscription() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [processing, setProcessing] = useState(false);
  const [gateway, setGateway] = useState("Razorpay");
  const [trackCode, setTrackCode] = useState("FULL_STACK");
  const [invoiceList, setInvoiceList] = useState([]);
  const [coupon, setCoupon] = useState("");
  const [couponPreview, setCouponPreview] = useState(null); // { payableAmount, couponApplied, couponMessage }
  const [couponBusy, setCouponBusy] = useState(false);
  const { notify } = useToast();

  const [mySub, setMySub] = useState(null);
  const currentPlanCode = mySub?.planCode || null;

  const refreshInvoices = () =>
    getMyInvoices()
      .then(setInvoiceList)
      .catch(() => setInvoiceList([]));

  useEffect(() => {
    if (user) refreshInvoices();
  }, [user]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    Promise.all([getPlans().catch(() => []), getMySubscription().catch(() => null)])
      .then(([data, sub]) => {
        setPlans(data);
        setMySub(sub);
      })
      .finally(() => setLoading(false));
  }, []);

  // Reset the coupon whenever a different plan is picked (or the modal closes).
  useEffect(() => {
    setCoupon("");
    setCouponPreview(null);
    setCouponBusy(false);
  }, [selected?.code]);

  const planCodeForApi = () => selected?.backendCode || selected?.code;

  const applyCoupon = async () => {
    if (!selected) return;
    const code = coupon.trim();
    setCouponBusy(true);
    try {
      const res = await previewCheckout(planCodeForApi(), code || null);
      setCouponPreview(res);
      if (code && res.couponApplied) {
        notify(`Coupon applied — you pay ${CURRENCY(res.payableAmount)}.`, { type: "success" });
      } else if (code) {
        notify(res.couponMessage || "That coupon code isn't valid.", { type: "error" });
      }
    } catch (err) {
      notify(err.message || "Couldn't check that coupon. Try again.", { type: "error" });
    } finally {
      setCouponBusy(false);
    }
  };

  const payableAmount = () =>
    couponPreview?.couponApplied ? couponPreview.payableAmount : selected?.price ?? 0;

  const confirmUpgrade = async () => {
    if (!selected) return;
    setProcessing(true);
    try {
      const checkout = await subscribeToPlan(selected.backendCode || selected.code, gateway, {
        trackCode,
        couponCode: couponPreview?.couponApplied ? coupon.trim() : null,
      });

      if (checkout.stripeCheckoutUrl) {
        window.location.href = checkout.stripeCheckoutUrl;
        return;
      }

      const ok = await loadRazorpayScript();
      if (!ok || !window.Razorpay) {
        notify("Couldn't load the Razorpay checkout script.", { type: "error" });
        setProcessing(false);
        return;
      }

      const rzp = new window.Razorpay({
        key: checkout.razorpayKeyId,
        order_id: checkout.razorpayOrderId,
        amount: Math.round((checkout.amount || payableAmount()) * 100),
        currency: checkout.currency || "INR",
        name: "Moriah Skill Hub",
        description: `${selected.name} plan`,
        prefill: { name: user?.name, email: user?.email, contact: user?.phone },
        theme: { color: "#1E4A78" },
        handler: async () => {
          notify("Payment captured — activating your subscription…", { type: "success" });
          const sub = await waitForActivation();
          setProcessing(false);
          setSelected(null);
          refreshInvoices();
          if (sub) {
            notify(`You're on the ${sub.planName || selected.name} plan.`, { type: "success", title: "Subscription active" });
            navigate("/student/dashboard", { replace: true });
          } else {
            notify("Payment received. Your subscription will activate shortly — refresh in a moment.", { type: "info" });
          }
        },
        modal: { ondismiss: () => setProcessing(false) },
      });
      rzp.on("payment.failed", (resp) => {
        notify(resp?.error?.description || "Payment failed.", { type: "error" });
        setProcessing(false);
      });
      rzp.open();
    } catch (err) {
      notify(
        err.message ||
          "Checkout failed. In local dev this needs real Razorpay/Stripe test-mode keys (see required-integrations.md).",
        { type: "error", title: "Checkout error" }
      );
      setProcessing(false);
    }
  };

  const viewInvoicePDF = (invoice) => {
    // The real invoice PDF is rendered server-side and handed back as a short-lived
    // pre-signed link. Until the async job has produced it, pdfUrl is null.
    if (invoice.pdfUrl) {
      const w = window.open(invoice.pdfUrl, "_blank", "noopener");
      if (!w) notify("Please allow popups to open the invoice PDF.", { type: "warning" });
      return;
    }
    if (invoice.status === "PROCESSING") {
      notify("Your invoice PDF is still being generated — try again in a minute.", { type: "info" });
      return;
    }
    if (invoice.status === "INVOICE FAILED") {
      notify("This invoice couldn't be generated. Contact billing@moriah.io with your payment reference.", { type: "error" });
      return;
    }

    // Fallback: build a PDF from the row data we already have.
    downloadPdf(invoice.id || "invoice", "Invoice", [
      {
        keyValues: [
          ["Invoice", invoice.id || "—"],
          ["Billed to", `${user?.name || ""}${user?.email ? " · " + user.email : ""}`],
          ["Date", invoice.date || "—"],
          ["Status", invoice.status || "—"],
          ["Plan", invoice.plan || "Subscription"],
        ],
      },
      {
        heading: "Amount",
        keyValues: [
          ["Subtotal", CURRENCY(invoice.amount)],
          ["Tax (GST 18%, inclusive)", CURRENCY(0)],
          ["Total paid", CURRENCY(invoice.amount)],
        ],
      },
      "Thank you for choosing Moriah Skill Hub. For billing queries, contact billing@moriah.io.",
    ]);
  };

  return (
    <div>
      <PageHeader title="Subscription & Billing" subtitle="Manage your plan, upgrade tiers, and view invoices" breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Subscription" }]} />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading plans…" /></div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-4">
          {plans.map((plan) => {
            const isCurrent = plan.code === currentPlanCode;
            return (
              <Card key={plan.code} className={"flex flex-col h-full " + (isCurrent ? "ring-2 ring-primary-600" : "")}>
                {isCurrent && <Badge tone="primary" className="mb-2 self-start">Current Plan</Badge>}
                <h3 className="font-display font-semibold text-ink-900">{plan.name}</h3>
                <p className="text-xs text-ink-500 mt-0.5">{plan.model}</p>
                <p className="text-2xl font-bold text-ink-900 font-display mt-3">{CURRENCY(plan.price)}</p>

                <ul className="flex flex-col gap-2 mt-4 flex-1 text-xs text-ink-700">
                  {(plan.features || []).map((feature) => (
                    <li key={feature} className="flex items-start gap-1.5">
                      <Check size={14} className="text-primary-600 mt-0.5 shrink-0" />
                      <span>{feature}</span>
                    </li>
                  ))}
                </ul>

                <Button
                  variant={isCurrent ? "secondary" : "primary"}
                  size="sm"
                  fullWidth
                  className="mt-6"
                  disabled={isCurrent}
                  onClick={() => setSelected(plan)}
                >
                  {isCurrent ? "Active" : "Switch to this plan"}
                </Button>
              </Card>
            );
          })}
        </div>
      )}

      <Card className="mt-4">
        <CardHeader title="Invoice History" subtitle="Download receipts for your records" />
        <Table
          columns={[
            { key: "id", header: "Invoice" },
            { key: "plan", header: "Plan" },
            { key: "amount", header: "Amount", render: (r) => CURRENCY(r.amount) },
            { key: "date", header: "Date" },
            {
              key: "status",
              header: "Status",
              render: (r) => (
                <Badge
                  tone={
                    r.status === "CAPTURED"
                      ? "success"
                      : r.status === "PROCESSING"
                      ? "warning"
                      : r.status === "REFUNDED"
                      ? "neutral"
                      : "error"
                  }
                >
                  {r.status}
                </Badge>
              ),
            },
            { key: "action", header: "", render: (row) => <Button variant="ghost" size="sm" icon={Download} onClick={(e) => { e.stopPropagation(); viewInvoicePDF(row); }}>PDF</Button> },
          ]}
          data={invoiceList}
        />
      </Card>

      <Modal
        open={!!selected}
        onClose={() => !processing && setSelected(null)}
        title={`Switch to ${selected?.name}`}
        description="You'll be redirected to a secure Razorpay/Stripe checkout."
        footer={
          <>
            <Button variant="secondary" onClick={() => setSelected(null)} disabled={processing}>Cancel</Button>
            <Button icon={CreditCard} onClick={confirmUpgrade} loading={processing}>Pay {selected ? CURRENCY(payableAmount()) : ""}</Button>
          </>
        }
      >
        {selected && (
          <div className="flex flex-col gap-4">
            <ul className="flex flex-col gap-2">
              {["Full plan curriculum & deliverables", "Prorated billing for remaining cycle", "Instant access on payment confirmation"].map((f) => (
                <li key={f} className="flex items-center gap-2 text-sm text-ink-600">
                  <Check size={15} className="text-success-600" /> {f}
                </li>
              ))}
            </ul>

            <div className="border-t border-border pt-4 text-left">
              <p className="text-xs font-semibold text-ink-800 mb-2">Select Payment Gateway</p>
              <div className="grid grid-cols-2 gap-3">
                <button
                  type="button"
                  onClick={() => setGateway("Razorpay")}
                  className={`flex items-center justify-between p-3 rounded-lg border text-left transition-all ${
                    gateway === "Razorpay"
                      ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100 font-semibold"
                      : "border-border hover:border-primary-300"
                  }`}
                >
                  <span className="text-xs text-primary-900 font-display">Razorpay</span>
                  <span className={"flex h-4 w-4 items-center justify-center rounded-full border " + (gateway === "Razorpay" ? "border-primary-700 bg-primary-700" : "border-border")}>
                    {gateway === "Razorpay" && <Check size={10} className="text-white" />}
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => setGateway("Stripe")}
                  className={`flex items-center justify-between p-3 rounded-lg border text-left transition-all ${
                    gateway === "Stripe"
                      ? "border-primary-600 bg-primary-50 ring-2 ring-primary-100 font-semibold"
                      : "border-border hover:border-primary-300"
                  }`}
                >
                  <span className="text-xs text-primary-950 font-display">Stripe</span>
                  <span className={"flex h-4 w-4 items-center justify-center rounded-full border " + (gateway === "Stripe" ? "border-primary-700 bg-primary-700" : "border-border")}>
                    {gateway === "Stripe" && <Check size={10} className="text-white" />}
                  </span>
                </button>
              </div>
            </div>

            <div className="border-t border-border pt-4 text-left">
              <p className="text-xs font-semibold text-ink-800 mb-2">Coupon code</p>
              <div className="flex gap-2">
                <input
                  value={coupon}
                  onChange={(e) => {
                    setCoupon(e.target.value.toUpperCase());
                    setCouponPreview(null);
                  }}
                  placeholder="Have a code?"
                  className="flex-1 rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-primary-500 bg-white uppercase"
                />
                <Button
                  variant="secondary"
                  onClick={applyCoupon}
                  loading={couponBusy}
                  disabled={!coupon.trim() || couponBusy}
                >
                  Apply
                </Button>
              </div>
              {couponPreview && coupon.trim() && (
                <p className={`mt-2 text-xs ${couponPreview.couponApplied ? "text-success-700" : "text-error-600"}`}>
                  {couponPreview.couponApplied
                    ? `Coupon applied — ${CURRENCY(couponPreview.originalAmount ?? selected.price)} → ${CURRENCY(couponPreview.payableAmount)}`
                    : couponPreview.couponMessage || "That coupon code isn't valid."}
                </p>
              )}
            </div>

            <div className="border-t border-border pt-4 text-left">
              <p className="text-xs font-semibold text-ink-800 mb-2">Learning Track</p>
              <select
                value={trackCode}
                onChange={(e) => setTrackCode(e.target.value)}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-primary-500 bg-white"
              >
                {TRACK_CODES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
