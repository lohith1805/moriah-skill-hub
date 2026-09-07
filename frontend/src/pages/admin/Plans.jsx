import { useEffect, useState } from "react";
import { Settings2, Percent, Plus, Trash2, RotateCcw } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import Badge from "../../components/ui/Badge";
import Tabs from "../../components/ui/Tabs";
import { Input, Select } from "../../components/ui/FormField";
import { CURRENCY } from "../../utils/constants";
import {
  getAdminPlans, createPlan, updatePlan, deletePlan,
  getCoupons, createCoupon, deleteCoupon,
} from "../../services/adminService";
import { useToast } from "../../context/ToastContext";

const todayIso = () => new Date().toISOString().slice(0, 10);

// Backend CouponResponse -> the row shape the coupons table renders.
const toCouponRow = (c) => ({
  code: c.code,
  discountType: c.discountType,
  discountValue: Number(c.discountValue ?? 0),
  discountLabel:
    c.discountType === "PERCENTAGE"
      ? `${Number(c.discountValue)}% off`
      : `${CURRENCY(Number(c.discountValue))} flat off`,
  validFrom: c.validFrom,
  validUntil: c.validUntil,
  maxRedemptions: c.maxRedemptions ?? null,
  timesRedeemed: c.timesRedeemed ?? 0,
  active: !!c.active,
});

const PLAN_FLAGS = [
  ["mentorSupport", "Mentor support"],
  ["allowsBatch", "Batch enrolment"],
  ["allowsSprints", "Sprint board & reviews"],
  ["allowsPip", "PIP tracking"],
  ["allowsInternshipLetter", "Internship letter"],
  ["allowsClientProject", "Client projects"],
];

// The backend UpdatePlanRequest is a full-field replace — build it from the
// loaded row so an "edit price" doesn't wipe the feature flags.
const planUpdatePayload = (p, overrides = {}) => ({
  name: p.name,
  priceInr: p.price,
  durationDays: p.durationDays,
  maxProjects: p.maxProjects,
  mentorSupport: p.mentorSupport,
  allowsBatch: p.allowsBatch,
  allowsSprints: p.allowsSprints,
  allowsPip: p.allowsPip,
  allowsInternshipLetter: p.allowsInternshipLetter,
  allowsClientProject: p.allowsClientProject,
  active: p.active,
  ...overrides,
});

const EMPTY_NEW_PLAN = {
  code: "", name: "", price: "", tierRank: "", durationDays: "90", maxProjects: "",
  mentorSupport: false, allowsBatch: false, allowsSprints: false,
  allowsPip: false, allowsInternshipLetter: false, allowsClientProject: false,
};

export default function AdminPlans() {
  const { notify } = useToast();

  const [plans, setPlans] = useState([]);
  const [plansLoading, setPlansLoading] = useState(true);
  const [coupons, setCoupons] = useState([]);
  const [couponsLoading, setCouponsLoading] = useState(true);

  // Edit plan price
  const [editing, setEditing] = useState(null);
  const [price, setPrice] = useState("");
  const [savingPlan, setSavingPlan] = useState(false);

  // Add plan
  const [isAddingPlan, setIsAddingPlan] = useState(false);
  const [newPlan, setNewPlan] = useState(EMPTY_NEW_PLAN);

  // Add coupon
  const [isAddingCoupon, setIsAddingCoupon] = useState(false);
  const [savingCoupon, setSavingCoupon] = useState(false);
  const [newCoupon, setNewCoupon] = useState({
    code: "", discountType: "PERCENTAGE", discountValue: "20",
    validFrom: todayIso(), validUntil: "2026-12-31", maxRedemptions: "",
  });

  const loadPlans = () => {
    setPlansLoading(true);
    getAdminPlans()
      .then(setPlans)
      .catch(() => notify("Couldn't load plans.", { type: "error" }))
      .finally(() => setPlansLoading(false));
  };

  const loadCoupons = () => {
    setCouponsLoading(true);
    getCoupons()
      .then((list) => setCoupons(list.map(toCouponRow)))
      .catch(() => notify("Couldn't load coupons.", { type: "error" }))
      .finally(() => setCouponsLoading(false));
  };

  useEffect(() => {
    loadPlans();
    loadCoupons();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ---- plans -------------------------------------------------------------

  const openEdit = (plan) => {
    setEditing(plan);
    setPrice(String(plan.price));
  };

  const savePrice = async () => {
    if (!editing) return;
    const nextPrice = Number(price);
    if (!(nextPrice >= 0)) {
      notify("Enter a valid price.", { type: "error" });
      return;
    }
    setSavingPlan(true);
    try {
      await updatePlan(editing.id, planUpdatePayload(editing, { priceInr: nextPrice }));
      notify(`${editing.name} pricing updated to ${CURRENCY(nextPrice)}.`, { type: "success", title: "Plan updated" });
      setEditing(null);
      loadPlans();
    } catch (err) {
      notify(err?.message || "Couldn't update the plan.", { type: "error" });
    } finally {
      setSavingPlan(false);
    }
  };

  const setPlanActive = async (plan, active) => {
    try {
      if (active) {
        await updatePlan(plan.id, planUpdatePayload(plan, { active: true }));
      } else {
        await deletePlan(plan.id); // deactivates — never row-deletes
      }
      notify(`${plan.name} ${active ? "reactivated" : "deactivated"}.`, { type: "success" });
      loadPlans();
    } catch (err) {
      notify(err?.message || "Couldn't update the plan.", { type: "error" });
    }
  };

  const handleAddPlan = async () => {
    const code = newPlan.code.toUpperCase().replace(/\s+/g, "_");
    if (!/^[A-Z][A-Z0-9_]*$/.test(code)) {
      notify("Code must start with a letter and use only A–Z, digits and underscore.", { type: "error" });
      return;
    }
    if (!newPlan.name.trim()) return notify("Plan name is required.", { type: "error" });
    if (!(Number(newPlan.price) >= 0)) return notify("Enter a valid price.", { type: "error" });
    if (!(Number(newPlan.tierRank) >= 1)) return notify("Tier rank must be 1 or higher.", { type: "error" });
    if (!(Number(newPlan.durationDays) >= 1)) return notify("Duration must be at least 1 day.", { type: "error" });

    setSavingPlan(true);
    try {
      await createPlan({
        code,
        name: newPlan.name.trim(),
        priceInr: Number(newPlan.price),
        tierRank: Number(newPlan.tierRank),
        durationDays: Number(newPlan.durationDays),
        maxProjects: newPlan.maxProjects === "" ? null : Number(newPlan.maxProjects),
        mentorSupport: newPlan.mentorSupport,
        allowsBatch: newPlan.allowsBatch,
        allowsSprints: newPlan.allowsSprints,
        allowsPip: newPlan.allowsPip,
        allowsInternshipLetter: newPlan.allowsInternshipLetter,
        allowsClientProject: newPlan.allowsClientProject,
        active: true,
      });
      notify(`Plan "${code}" created.`, { type: "success" });
      setIsAddingPlan(false);
      setNewPlan(EMPTY_NEW_PLAN);
      loadPlans();
    } catch (err) {
      notify(err?.message || "Couldn't create the plan.", { type: "error" });
    } finally {
      setSavingPlan(false);
    }
  };

  // ---- coupons ---------------------------------------------------------

  const handleAddCoupon = async () => {
    const codeUpper = newCoupon.code.toUpperCase().replace(/\s+/g, "");
    if (!/^[A-Z0-9][A-Z0-9_-]*$/.test(codeUpper)) {
      notify("Code must be letters, digits, hyphen or underscore.", { type: "error" });
      return;
    }
    const value = Number(newCoupon.discountValue);
    if (!value || value <= 0) {
      notify("Enter a discount value greater than zero.", { type: "error" });
      return;
    }
    if (!newCoupon.validUntil || newCoupon.validUntil < newCoupon.validFrom) {
      notify("The 'valid until' date must be on or after 'valid from'.", { type: "error" });
      return;
    }
    setSavingCoupon(true);
    try {
      await createCoupon({
        code: codeUpper,
        discountType: newCoupon.discountType,
        discountValue: value,
        validFrom: newCoupon.validFrom,
        validUntil: newCoupon.validUntil,
        maxRedemptions: newCoupon.maxRedemptions === "" ? null : Number(newCoupon.maxRedemptions),
        active: true,
      });
      notify(`Coupon "${codeUpper}" created.`, { type: "success" });
      setIsAddingCoupon(false);
      setNewCoupon({ code: "", discountType: "PERCENTAGE", discountValue: "20", validFrom: todayIso(), validUntil: "2026-12-31", maxRedemptions: "" });
      loadCoupons();
    } catch (err) {
      notify(err?.message || "Couldn't create the coupon.", { type: "error" });
    } finally {
      setSavingCoupon(false);
    }
  };

  const handleDeleteCoupon = async (code) => {
    try {
      await deleteCoupon(code);
      notify(`Coupon "${code}" deactivated.`, { type: "success" });
      loadCoupons();
    } catch (err) {
      notify(err?.message || "Couldn't deactivate the coupon.", { type: "error" });
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Subscription & Pricing Engine"
        subtitle="Plan pricing, feature entitlements, and promotional coupons (MSH-FR-ADM-02)"
        breadcrumbs={[{ label: "Dashboard", to: "/admin/dashboard" }, { label: "Pricing Engine" }]}
      />

      <Card>
        <Tabs
          tabs={[
            { key: "tiers", label: `Subscription Tiers (${plans.filter((p) => p.active).length})` },
            { key: "coupons", label: `Active Coupons (${coupons.filter((c) => c.active).length})` },
          ]}
        >
          {(active) => {
            if (active === "tiers") {
              return (
                <div className="flex flex-col gap-4">
                  <div className="flex justify-between items-center">
                    <p className="text-xs text-ink-500 text-left">Live plan catalogue — pricing and feature flags are read from and written to the backend.</p>
                    <Button size="sm" icon={Plus} onClick={() => { setNewPlan(EMPTY_NEW_PLAN); setIsAddingPlan(true); }}>Add Tier</Button>
                  </div>

                  <Table
                    loading={plansLoading}
                    data={plans}
                    emptyTitle="No plans configured"
                    columns={[
                      {
                        key: "name",
                        header: "Tier",
                        className: "text-left font-medium text-ink-900",
                        render: (r) => (
                          <div>
                            <p className="font-semibold text-ink-900">{r.name}</p>
                            <p className="text-xs text-ink-400 font-mono">{r.code} · tier {r.tierRank} · {r.durationDays}d</p>
                          </div>
                        ),
                      },
                      {
                        key: "price",
                        header: "Price (INR)",
                        className: "text-left",
                        render: (r) => <span className="font-bold text-ink-900 font-display text-sm">{CURRENCY(r.price)}</span>,
                      },
                      {
                        key: "features",
                        header: "Entitlements",
                        className: "text-left max-w-sm",
                        render: (r) => (
                          <div className="flex flex-wrap gap-1">
                            {r.features.length === 0 && <span className="text-[10px] text-ink-400">—</span>}
                            {r.features.slice(0, 3).map((f, i) => (
                              <span key={i} className="text-[10px] bg-cream-100 text-ink-700 px-1.5 py-0.5 rounded">{f}</span>
                            ))}
                            {r.features.length > 3 && <span className="text-[10px] text-ink-400 font-mono">+{r.features.length - 3}</span>}
                          </div>
                        ),
                      },
                      {
                        key: "status",
                        header: "Status",
                        className: "text-left",
                        render: (r) => <Badge tone={r.active ? "success" : "neutral"}>{r.active ? "active" : "inactive"}</Badge>,
                      },
                      {
                        key: "action",
                        header: "",
                        className: "text-right",
                        render: (r) => (
                          <div className="flex gap-2 justify-end">
                            <Button size="sm" variant="secondary" icon={Settings2} onClick={() => openEdit(r)}>Edit Pricing</Button>
                            {r.active ? (
                              <Button size="sm" variant="danger" icon={Trash2} onClick={() => setPlanActive(r, false)}>Deactivate</Button>
                            ) : (
                              <Button size="sm" variant="secondary" icon={RotateCcw} onClick={() => setPlanActive(r, true)}>Reactivate</Button>
                            )}
                          </div>
                        ),
                      },
                    ]}
                  />
                </div>
              );
            }

            if (active === "coupons") {
              return (
                <div className="flex flex-col gap-4">
                  <div className="flex justify-between items-center">
                    <p className="text-xs text-ink-500 text-left">Promotional vouchers, discounts, and redemption limits.</p>
                    <Button size="sm" icon={Percent} onClick={() => setIsAddingCoupon(true)}>New Coupon Code</Button>
                  </div>

                  <Table
                    loading={couponsLoading}
                    data={coupons}
                    emptyTitle="No coupons yet"
                    columns={[
                      {
                        key: "code",
                        header: "Coupon Code",
                        className: "text-left font-mono font-bold text-ink-900",
                        render: (r) => <span className="bg-cream-100 text-primary-900 px-2 py-0.5 rounded">{r.code}</span>,
                      },
                      { key: "discount", header: "Discount", className: "text-left font-medium text-ink-800", render: (r) => r.discountLabel },
                      {
                        key: "usage",
                        header: "Redeemed / Limit",
                        className: "text-left font-mono text-xs",
                        render: (r) => `${r.timesRedeemed} / ${r.maxRedemptions ?? "∞"}`,
                      },
                      { key: "validUntil", header: "Valid Till", className: "text-left text-xs" },
                      {
                        key: "status",
                        header: "Status",
                        className: "text-left",
                        render: (r) => <Badge tone={r.active ? "success" : "neutral"}>{r.active ? "active" : "inactive"}</Badge>,
                      },
                      {
                        key: "action",
                        header: "",
                        className: "text-right",
                        render: (r) => (
                          r.active
                            ? <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDeleteCoupon(r.code)}>Deactivate</Button>
                            : <span className="text-xs text-ink-400">—</span>
                        ),
                      },
                    ]}
                  />
                </div>
              );
            }
          }}
        </Tabs>
      </Card>

      {/* Edit Plan Price */}
      <Modal
        open={!!editing}
        onClose={() => setEditing(null)}
        title={`Edit ${editing?.name} pricing`}
        description="Updates the price only — feature flags and duration are preserved."
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditing(null)}>Cancel</Button>
            <Button loading={savingPlan} onClick={savePrice}>Save Changes</Button>
          </>
        }
      >
        <Input label="Price in INR (₹)" type="number" min="0" value={price} onChange={(e) => setPrice(e.target.value)} />
      </Modal>

      {/* Add Plan */}
      <Modal
        open={isAddingPlan}
        onClose={() => setIsAddingPlan(false)}
        title="Create subscription tier (MSH-FR-ADM-02)"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsAddingPlan(false)}>Cancel</Button>
            <Button loading={savingPlan} onClick={handleAddPlan}>Create Tier</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={(e) => { e.preventDefault(); handleAddPlan(); }}>
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Code" required placeholder="e.g. WEEKEND_SPRINT" value={newPlan.code} onChange={(e) => setNewPlan((v) => ({ ...v, code: e.target.value.toUpperCase() }))} />
            <Input label="Plan Name" required placeholder="e.g. Weekend Sprint Track" value={newPlan.name} onChange={(e) => setNewPlan((v) => ({ ...v, name: e.target.value }))} />
          </div>
          <div className="grid sm:grid-cols-3 gap-4">
            <Input label="Price (₹)" type="number" min="0" required value={newPlan.price} onChange={(e) => setNewPlan((v) => ({ ...v, price: e.target.value }))} />
            <Input label="Tier rank" type="number" min="1" required value={newPlan.tierRank} onChange={(e) => setNewPlan((v) => ({ ...v, tierRank: e.target.value }))} />
            <Input label="Duration (days)" type="number" min="1" required value={newPlan.durationDays} onChange={(e) => setNewPlan((v) => ({ ...v, durationDays: e.target.value }))} />
          </div>
          <Input label="Max projects" type="number" min="0" placeholder="Leave blank for no limit" value={newPlan.maxProjects} onChange={(e) => setNewPlan((v) => ({ ...v, maxProjects: e.target.value }))} />
          <div>
            <p className="text-sm font-medium text-ink-900 mb-2">Entitlements</p>
            <div className="grid sm:grid-cols-2 gap-2">
              {PLAN_FLAGS.map(([key, label]) => (
                <label key={key} className="flex items-center gap-2 text-xs text-ink-800 p-2 rounded-lg border border-border/60 hover:bg-cream-50 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={newPlan[key]}
                    onChange={(e) => setNewPlan((v) => ({ ...v, [key]: e.target.checked }))}
                    className="w-4 h-4 rounded border-border text-primary-600 focus:ring-primary-100"
                  />
                  {label}
                </label>
              ))}
            </div>
          </div>
        </form>
      </Modal>

      {/* Add Coupon */}
      <Modal
        open={isAddingCoupon}
        onClose={() => setIsAddingCoupon(false)}
        title="Create promotional coupon code"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsAddingCoupon(false)}>Cancel</Button>
            <Button loading={savingCoupon} onClick={handleAddCoupon}>Create Coupon</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={(e) => { e.preventDefault(); handleAddCoupon(); }}>
          <Input
            label="Coupon Code"
            required
            placeholder="e.g. MORIAH50"
            value={newCoupon.code}
            onChange={(e) => setNewCoupon((v) => ({ ...v, code: e.target.value.toUpperCase() }))}
          />
          <div className="grid sm:grid-cols-2 gap-4">
            <Select
              label="Discount Type"
              value={newCoupon.discountType}
              onChange={(e) => setNewCoupon((v) => ({ ...v, discountType: e.target.value }))}
              options={[
                { value: "PERCENTAGE", label: "Percentage (%)" },
                { value: "FLAT", label: "Flat amount (₹)" },
              ]}
            />
            <Input
              label={newCoupon.discountType === "PERCENTAGE" ? "Percent off" : "Rupees off"}
              type="number"
              min="0"
              required
              placeholder={newCoupon.discountType === "PERCENTAGE" ? "e.g. 25" : "e.g. 5000"}
              value={newCoupon.discountValue}
              onChange={(e) => setNewCoupon((v) => ({ ...v, discountValue: e.target.value }))}
            />
          </div>
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Valid From" type="date" value={newCoupon.validFrom} onChange={(e) => setNewCoupon((v) => ({ ...v, validFrom: e.target.value }))} />
            <Input label="Valid Until" type="date" value={newCoupon.validUntil} onChange={(e) => setNewCoupon((v) => ({ ...v, validUntil: e.target.value }))} />
          </div>
          <Input
            label="Max redemptions"
            type="number"
            min="0"
            placeholder="Leave blank for unlimited"
            value={newCoupon.maxRedemptions}
            onChange={(e) => setNewCoupon((v) => ({ ...v, maxRedemptions: e.target.value }))}
          />
        </form>
      </Modal>
    </div>
  );
}
