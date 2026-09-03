import { useEffect, useState } from "react";
import {
  Settings2, Percent, Plus, Trash2, X, CreditCard, Sparkles,
  Check, CheckCircle2, ShieldCheck, Layers, Award
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import Badge from "../../components/ui/Badge";
import Tabs from "../../components/ui/Tabs";
import { Input, Select } from "../../components/ui/FormField";
import { SUBSCRIPTION_PLANS, CURRENCY } from "../../utils/constants";
import { useToast } from "../../context/ToastContext";

export default function AdminPlans() {
  const [plans, setPlans] = useState([]);
  const [coupons, setCoupons] = useState([]);
  const [loading, setLoading] = useState(true);

  // Edit Plan Price modal
  const [editing, setEditing] = useState(null);
  const [price, setPrice] = useState("");
  const [saving, setSaving] = useState(false);

  // Add Plan modal
  const [isAddingPlan, setIsAddingPlan] = useState(false);
  const [newPlan, setNewPlan] = useState({ name: "", model: "", price: "", features: "" });

  // Add Coupon modal
  const [isAddingCoupon, setIsAddingCoupon] = useState(false);
  const [newCoupon, setNewCoupon] = useState({ code: "", discount: "20% off", status: "active", expiryDate: "2026-12-31", usageCount: 0, usageLimit: 100 });

  const { notify } = useToast();

  useEffect(() => {
    // Load plans
    const savedPlans = localStorage.getItem("msh_subscription_plans");
    if (savedPlans) {
      setPlans(JSON.parse(savedPlans));
    } else {
      localStorage.setItem("msh_subscription_plans", JSON.stringify(SUBSCRIPTION_PLANS));
      setPlans(SUBSCRIPTION_PLANS);
    }

    // Load coupons
    const initialCoupons = [
      { code: "EARLYBIRD25", discount: "25% off", status: "active", expiryDate: "2026-12-31", usageCount: 42, usageLimit: 100 },
      { code: "COLLEGE15", discount: "15% off (Institutional)", status: "active", expiryDate: "2026-12-31", usageCount: 18, usageLimit: 50 },
      { code: "FLAT5000", discount: "₹5,000 Flat Off", status: "active", expiryDate: "2026-10-31", usageCount: 9, usageLimit: 30 },
      { code: "SUMMER10", discount: "Expired", status: "expired", expiryDate: "2026-06-01", usageCount: 50, usageLimit: 50 }
    ];
    const savedCoupons = localStorage.getItem("msh_coupons");
    if (savedCoupons) {
      setCoupons(JSON.parse(savedCoupons));
    } else {
      localStorage.setItem("msh_coupons", JSON.stringify(initialCoupons));
      setCoupons(initialCoupons);
    }

    setLoading(false);
  }, []);

  const persistPlans = (data) => {
    setPlans(data);
    localStorage.setItem("msh_subscription_plans", JSON.stringify(data));
  };

  const persistCoupons = (data) => {
    setCoupons(data);
    localStorage.setItem("msh_coupons", JSON.stringify(data));
  };

  const openEdit = (plan) => {
    setEditing(plan);
    setPrice(String(plan.price));
  };

  const savePrice = async () => {
    setSaving(true);
    try {
      const nextPrice = Number(price);
      const updated = plans.map((p) => (p.code === editing.code ? { ...p, price: nextPrice } : p));
      persistPlans(updated);
      notify(`${editing.name} pricing updated to ${CURRENCY(nextPrice)}.`, { type: "success", title: "Plan Updated" });
      setEditing(null);
    } finally {
      setSaving(false);
    }
  };

  const handleAddPlan = () => {
    if (!newPlan.name || !newPlan.model || !newPlan.price) {
      notify("Please fill in all required fields.", { type: "error" });
      return;
    }

    const priceNum = Number(newPlan.price);
    const code = newPlan.name.toLowerCase().replace(/\s+/g, "_");
    const created = {
      code,
      name: newPlan.name,
      model: newPlan.model,
      price: priceNum,
      features: newPlan.features ? newPlan.features.split(",").map(s => s.trim()) : ["Core sprint simulation", "Code reviews", "Certificate"]
    };

    const updated = [...plans, created];
    persistPlans(updated);
    notify(`Subscription tier "${newPlan.name}" added successfully.`, { type: "success" });
    setIsAddingPlan(false);
    setNewPlan({ name: "", model: "", price: "", features: "" });
  };

  const handleDeletePlan = (planCode) => {
    const plan = plans.find((p) => p.code === planCode);
    const updated = plans.filter((p) => p.code !== planCode);
    persistPlans(updated);
    notify(`Plan tier "${plan?.name}" deleted.`, { type: "success" });
  };

  const handleAddCoupon = () => {
    if (!newCoupon.code || !newCoupon.discount) {
      notify("Please fill in all coupon fields.", { type: "error" });
      return;
    }

    const codeUpper = newCoupon.code.toUpperCase().replace(/\s+/g, "");
    const created = {
      code: codeUpper,
      discount: newCoupon.discount,
      status: newCoupon.status,
      expiryDate: newCoupon.expiryDate || "2026-12-31",
      usageCount: 0,
      usageLimit: Number(newCoupon.usageLimit) || 100
    };

    const updated = [created, ...coupons];
    persistCoupons(updated);
    notify(`Coupon "${codeUpper}" created successfully.`, { type: "success" });
    setIsAddingCoupon(false);
    setNewCoupon({ code: "", discount: "20% off", status: "active", expiryDate: "2026-12-31", usageCount: 0, usageLimit: 100 });
  };

  const handleDeleteCoupon = (code) => {
    const updated = coupons.filter((c) => c.code !== code);
    persistCoupons(updated);
    notify(`Coupon "${code}" deleted.`, { type: "success" });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Subscription & Pricing Engine"
        subtitle="Dynamic pricing configuration and early-bird coupons (MSH-FR-ADM-02)"
        breadcrumbs={[{ label: "Dashboard", to: "/admin/dashboard" }, { label: "Pricing Engine" }]}
      />

      <Card>
        <Tabs
          tabs={[
            { key: "tiers", label: "Subscription Tiers & Features" },
            { key: "coupons", label: `Active Coupons (${coupons.filter(c => c.status === "active").length})` }
          ]}
        >
          {(active) => {
            if (active === "tiers") {
              return (
                <div className="flex flex-col gap-4">
                  <div className="flex justify-between items-center">
                    <p className="text-xs text-ink-500 text-left">Dynamic tier pricing and feature entitlements configured across portals.</p>
                    <Button size="sm" icon={Plus} onClick={() => setIsAddingPlan(true)}>Add Tier</Button>
                  </div>

                  <Table
                    loading={loading}
                    data={plans}
                    columns={[
                      {
                        key: "name",
                        header: "Tier & Pedagogy",
                        className: "text-left font-medium text-ink-900",
                        render: (r) => (
                          <div>
                            <p className="font-semibold text-ink-900">{r.name}</p>
                            <p className="text-xs text-ink-500">{r.model}</p>
                          </div>
                        )
                      },
                      {
                        key: "price",
                        header: "Price (INR)",
                        className: "text-left",
                        render: (r) => <span className="font-bold text-ink-900 font-display text-sm">{CURRENCY(r.price)}</span>
                      },
                      {
                        key: "features",
                        header: "Feature Entitlements",
                        className: "text-left max-w-sm",
                        render: (r) => (
                          <div className="flex flex-wrap gap-1">
                            {(r.features || []).slice(0, 3).map((f, i) => (
                              <span key={i} className="text-[10px] bg-cream-100 text-ink-700 px-1.5 py-0.5 rounded">
                                {f}
                              </span>
                            ))}
                            {(r.features || []).length > 3 && (
                              <span className="text-[10px] text-ink-400 font-mono">+{r.features.length - 3} more</span>
                            )}
                          </div>
                        )
                      },
                      {
                        key: "action",
                        header: "",
                        className: "text-right",
                        render: (r) => (
                          <div className="flex gap-2 justify-end">
                            <Button size="sm" variant="secondary" icon={Settings2} onClick={() => openEdit(r)}>Edit Pricing</Button>
                            <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDeletePlan(r.code)}>Delete</Button>
                          </div>
                        )
                      }
                    ]}
                  />
                </div>
              );
            }

            if (active === "coupons") {
              return (
                <div className="flex flex-col gap-4">
                  <div className="flex justify-between items-center">
                    <p className="text-xs text-ink-500 text-left">Promotional vouchers, early-bird discounts, and usage limits.</p>
                    <Button size="sm" icon={Percent} onClick={() => setIsAddingCoupon(true)}>New Coupon Code</Button>
                  </div>

                  <Table
                    data={coupons}
                    columns={[
                      {
                        key: "code",
                        header: "Coupon Code",
                        className: "text-left font-mono font-bold text-ink-900",
                        render: (r) => <span className="bg-cream-100 text-primary-900 px-2 py-0.5 rounded">{r.code}</span>
                      },
                      { key: "discount", header: "Discount Value", className: "text-left font-medium text-ink-800" },
                      {
                        key: "usage",
                        header: "Usage / Quota Limit",
                        className: "text-left font-mono text-xs",
                        render: (r) => `${r.usageCount || 0} / ${r.usageLimit || 100} claimed`
                      },
                      { key: "expiryDate", header: "Valid Till", className: "text-left text-xs" },
                      {
                        key: "status",
                        header: "Status",
                        className: "text-left",
                        render: (r) => <Badge tone={r.status === "active" ? "success" : "neutral"}>{r.status}</Badge>
                      },
                      {
                        key: "action",
                        header: "",
                        className: "text-right",
                        render: (r) => (
                          <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDeleteCoupon(r.code)}>Delete</Button>
                        )
                      }
                    ]}
                  />
                </div>
              );
            }
          }}
        </Tabs>
      </Card>

      {/* Edit Plan Modal */}
      <Modal
        open={!!editing}
        onClose={() => setEditing(null)}
        title={`Edit ${editing?.name} Pricing`}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
            <Button loading={saving} onClick={savePrice}>Save Changes</Button>
          </>
        }
      >
        <Input label="Price in INR (₹)" type="number" value={price} onChange={(e) => setPrice(e.target.value)} />
      </Modal>

      {/* Add Plan Modal */}
      <Modal
        open={isAddingPlan}
        onClose={() => setIsAddingPlan(false)}
        title="Create New Subscription Tier (MSH-FR-ADM-02)"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsAddingPlan(false)}>Cancel</Button>
            <Button onClick={handleAddPlan}>Create Tier</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={(e) => { e.preventDefault(); handleAddPlan(); }}>
          <Input label="Plan Name" required placeholder="e.g. Masterclass Capstone Track" value={newPlan.name} onChange={(e) => setNewPlan((v) => ({ ...v, name: e.target.value }))} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Pedagogy Model" placeholder="e.g. Guided 1-on-1 Mentorship" value={newPlan.model} onChange={(e) => setNewPlan((v) => ({ ...v, model: e.target.value }))} />
            <Input label="Price (₹)" type="number" required placeholder="e.g. 24999" value={newPlan.price} onChange={(e) => setNewPlan((v) => ({ ...v, price: e.target.value }))} />
          </div>
          <Input label="Included Features (Comma-separated)" placeholder="Live agile simulation, 1-on-1 PR reviews, placement guarantee" value={newPlan.features} onChange={(e) => setNewPlan((v) => ({ ...v, features: e.target.value }))} />
        </form>
      </Modal>

      {/* Add Coupon Modal */}
      <Modal
        open={isAddingCoupon}
        onClose={() => setIsAddingCoupon(false)}
        title="Create Promotional Coupon Code"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsAddingCoupon(false)}>Cancel</Button>
            <Button onClick={handleAddCoupon}>Create Coupon</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={(e) => { e.preventDefault(); handleAddCoupon(); }}>
          <Input label="Coupon Code" required placeholder="e.g. MORIAH50" value={newCoupon.code} onChange={(e) => setNewCoupon((v) => ({ ...v, code: e.target.value }))} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Discount Text / Value" required placeholder="e.g. 25% off" value={newCoupon.discount} onChange={(e) => setNewCoupon((v) => ({ ...v, discount: e.target.value }))} />
            <Input label="Max Usage Limit" type="number" placeholder="e.g. 100" value={newCoupon.usageLimit} onChange={(e) => setNewCoupon((v) => ({ ...v, usageLimit: e.target.value }))} />
          </div>
          <Input label="Valid Until" type="date" value={newCoupon.expiryDate} onChange={(e) => setNewCoupon((v) => ({ ...v, expiryDate: e.target.value }))} />
        </form>
      </Modal>
    </div>
  );
}