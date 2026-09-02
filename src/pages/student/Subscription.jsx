import { useEffect, useState } from "react";
import { Check, CreditCard, Download } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import Badge from "../../components/ui/Badge";
import Table from "../../components/ui/Table";
import Modal from "../../components/ui/Modal";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getPlans, subscribeToPlan } from "../../services/studentService";
import { useToast } from "../../context/ToastContext";
import { CURRENCY } from "../../utils/constants";
import { useAuth } from "../../context/AuthContext";
import RazorpayMockModal from "../../components/ui/RazorpayMockModal";


const INVOICES = [];

const loadRazorpayScript = () => {
  return new Promise((resolve) => {
    if (window.Razorpay) {
      resolve(true);
      return;
    }
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve(true);
    script.onerror = () => resolve(false);
    document.body.appendChild(script);
  });
};

export default function StudentSubscription() {
  const { user } = useAuth();
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);
  const [processing, setProcessing] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [gateway, setGateway] = useState("Razorpay");
  const [invoiceList, setInvoiceList] = useState([]);
  const { notify } = useToast();

  const currentPlanCode = user?.subscription?.planCode || "project_based";

  useEffect(() => {
    if (user) {
      try {
        const rawTx = localStorage.getItem("msh_transactions");
        const txs = rawTx ? JSON.parse(rawTx) : [];
        const studentInvoices = txs
          .filter((t) => t.student === user.name)
          .map((t) => ({
            id: t.id.replace("tx", "INV-"),
            plan: t.plan,
            amount: t.amount,
            date: t.date,
            status: t.status,
          }));
        setInvoiceList(studentInvoices);
      } catch (e) {
        setInvoiceList([]);
      }
    }
  }, [user]);

  useEffect(() => {
    getPlans().then((data) => {
      setPlans(data);
      setLoading(false);
    });
  }, []);

  const confirmUpgrade = () => {
    setProcessing(true);
    setShowPaymentModal(true);
  };

  const viewInvoicePDF = (invoice) => {
    const printWindow = window.open("", "_blank");
    if (!printWindow) {
      notify("Please allow popups to view the invoice PDF.", { type: "warning" });
      return;
    }
    
    const htmlContent = `
      <html>
        <head>
          <title>Invoice - ${invoice.id}</title>
          <style>
            body { font-family: system-ui, sans-serif; color: #1e293b; padding: 40px; max-width: 800px; margin: 0 auto; }
            .header { display: flex; justify-content: space-between; border-bottom: 2px solid #e2e8f0; padding-bottom: 20px; margin-bottom: 30px; }
            .logo { font-size: 24px; font-weight: bold; color: #0f172a; }
            .logo span { color: #b45309; }
            .title { font-size: 28px; font-weight: bold; text-align: right; }
            .details { display: flex; justify-content: space-between; margin-bottom: 40px; line-height: 1.6; }
            .table { border-collapse: collapse; width: 100%; margin-bottom: 40px; }
            .table th { background: #f8fafc; border-bottom: 2px solid #e2e8f0; padding: 12px; text-align: left; font-weight: 600; }
            .table td { border-bottom: 1px solid #e2e8f0; padding: 12px; }
            .totals { display: flex; flex-direction: column; align-items: flex-end; font-size: 16px; line-height: 2; }
            .total-row { display: flex; justify-content: space-between; width: 250px; }
            .grand-total { font-size: 20px; font-weight: bold; border-top: 2px solid #e2e8f0; padding-top: 10px; margin-top: 10px; }
            .footer { border-top: 1px solid #e2e8f0; padding-top: 20px; margin-top: 60px; font-size: 12px; color: #64748b; text-align: center; }
            .badge { background: #dcfce7; color: #15803d; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: 600; display: inline-block; }
            @media print {
              .no-print { display: none; }
            }
            .btn { background: #0f172a; color: white; border: none; padding: 8px 16px; border-radius: 6px; font-size: 14px; font-weight: 500; cursor: pointer; }
          </style>
        </head>
        <body>
          <div class="no-print" style="margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center;">
            <span style="color: #64748b; font-size: 14px;">Print or Save as PDF using your browser's print menu.</span>
            <button class="btn" onclick="window.print()">Print / Save PDF</button>
          </div>
          <div class="header">
            <div>
              <div class="logo">Moriah<span>SkillHub</span></div>
              <p style="margin-top: 5px; color: #64748b; font-size: 14px;">Premium Developer Training Operations</p>
            </div>
            <div>
              <div class="title">INVOICE</div>
              <p style="color: #64748b; font-size: 14px; text-align: right; margin-top: 5px;">#${invoice.id}</p>
            </div>
          </div>
          
          <div class="details">
            <div>
              <strong style="color: #475569;">Billed To:</strong>
              <p style="margin-top: 5px; font-size: 16px; font-weight: 600; color: #0f172a;">${user?.name}</p>
              <p style="color: #64748b; font-size: 14px; margin: 2px 0;">Email: ${user?.email}</p>
              <p style="color: #64748b; font-size: 14px; margin: 2px 0;">Phone: ${user?.phone || 'Not provided'}</p>
            </div>
            <div style="text-align: right;">
              <p style="margin: 2px 0;"><strong>Date:</strong> ${invoice.date}</p>
              <p style="margin: 2px 0;"><strong>Payment Status:</strong> <span class="badge">${invoice.status}</span></p>
              <p style="margin: 2px 0;"><strong>Payment Method:</strong> Simulated Gateway</p>
            </div>
          </div>

          <table class="table">
            <thead>
              <tr>
                <th>Description</th>
                <th style="text-align: right;">Cycle</th>
                <th style="text-align: right;">Price</th>
                <th style="text-align: right;">Total</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><strong>Moriah subscription - ${invoice.plan}</strong><br/><span style="font-size: 12px; color: #64748b;">Full curriculum access and sprint reviews</span></td>
                <td style="text-align: right;">1 Month</td>
                <td style="text-align: right;">₹${invoice.amount}</td>
                <td style="text-align: right;">₹${invoice.amount}</td>
              </tr>
            </tbody>
          </table>

          <div class="totals">
            <div class="total-row">
              <span>Subtotal:</span>
              <span>₹${invoice.amount}</span>
            </div>
            <div class="total-row">
              <span>Tax (GST 18%):</span>
              <span>₹0 (Inclusive)</span>
            </div>
            <div class="total-row grand-total">
              <span>Grand Total:</span>
              <span>₹${invoice.amount}</span>
            </div>
          </div>

          <div class="footer">
            <p>Thank you for choosing Moriah Skill Hub! For support, email billing@moriah.io</p>
            <p style="margin-top: 5px;">Moriah Skill Hub Ltd. · Bangalore, India</p>
          </div>
        </body>
      </html>
    `;
    
    printWindow.document.write(htmlContent);
    printWindow.document.close();
  };

  const handlePaymentSuccess = async (response) => {
    setShowPaymentModal(false);
    setProcessing(true);
    try {
      await subscribeToPlan(selected.code, "razorpay");
      
      if (user) {
        const updatedUser = {
          ...user,
          subscription: {
            planCode: selected.code,
            planName: selected.name,
            price: selected.price,
            model: selected.model,
            paidAt: new Date().toISOString(),
            paymentId: response.razorpay_payment_id || `rzp_${Date.now()}`,
            gateway: gateway,
          }
        };
        localStorage.setItem("msh_user", JSON.stringify(updatedUser));

        const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
        if (rawList) {
          const list = JSON.parse(rawList);
          const idx = list.findIndex((u) => u.id === user.id);
          if (idx > -1) {
            list[idx] = updatedUser;
            localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(list));
          }
        }

        // Record transaction
        try {
          const rawTx = localStorage.getItem("msh_transactions");
          const txList = rawTx ? JSON.parse(rawTx) : [];
          const txId = `tx${Date.now()}`;
          txList.unshift({
            id: txId,
            student: user.name,
            plan: selected.name,
            amount: selected.price,
            gateway: gateway,
            status: "Success",
            date: new Date().toISOString().slice(0, 10),
          });
          localStorage.setItem("msh_transactions", JSON.stringify(txList));

          // Record receipt confirmation notification
          const rawNotif = localStorage.getItem("msh_notifications");
          const notifList = rawNotif ? JSON.parse(rawNotif) : [];
          notifList.unshift({
            id: `notif-${Date.now()}`,
            title: "Subscription Upgraded",
            body: `Successfully upgraded to the ${selected.name} plan. Paid ₹${selected.price} via ${gateway}. Receipt code: ${response.razorpay_payment_id || txId.replace("tx", "INV-")}.`,
            time: new Date().toISOString(),
            read: false,
          });
          localStorage.setItem("msh_notifications", JSON.stringify(notifList));
        } catch (err) {
          console.warn("[Subscription] Could not record transaction:", err.message);
        }
      }

      notify(`You are now successfully subscribed to ${selected.name}.`, { type: "success", title: "Payment Completed" });
      setSelected(null);

      setTimeout(() => {
        window.location.reload();
      }, 800);
    } catch (e) {
      notify("Failed to link subscription after payment confirmation.", { type: "error", title: "Sync failed" });
    } finally {
      setProcessing(false);
    }
  };

  const handlePaymentClose = () => {
    setShowPaymentModal(false);
    setProcessing(false);
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
            { key: "status", header: "Status", render: (r) => <Badge tone="success">{r.status}</Badge> },
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
            <Button icon={CreditCard} onClick={confirmUpgrade} loading={processing}>Pay {selected ? CURRENCY(selected.price) : ""}</Button>
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
          </div>
        )}
      </Modal>

      <RazorpayMockModal
        isOpen={showPaymentModal}
        onClose={handlePaymentClose}
        onSuccess={handlePaymentSuccess}
        amount={selected?.price || 0}
        planName={selected?.name}
        userName={user?.name}
        userEmail={user?.email}
        userPhone={user?.phone}
        gateway={gateway}
      />
    </div>
  );
}
