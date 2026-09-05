import { useEffect, useState } from "react";
import { Megaphone, Plus, MessageCircle, Mail, Edit, Trash2, Check, Send, Users, Loader2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import {
  getLeads,
  logInteraction,
  getCampaigns,
  createCampaign,
  updateCampaign,
  deleteCampaign,
  CAMPAIGN_CHANNELS,
} from "../../services/crmService";

const DEFAULT_TEMPLATE =
  "Hi {{name}}! 👋 Welcome to Moriah Skill Hub. We'd love to help you kickstart your career with our project-based training tracks.";

// The message template used by the client-side "Send" helper is a front-end-only
// convenience (the backend campaign record has no template field), so it is kept
// per-campaign in localStorage rather than sent to the API.
const TEMPLATE_KEY = "msh_campaign_templates";
const readTemplates = () => {
  try {
    return JSON.parse(localStorage.getItem(TEMPLATE_KEY) || "{}");
  } catch {
    return {};
  }
};
const saveTemplate = (id, text) => {
  const all = readTemplates();
  all[id] = text;
  localStorage.setItem(TEMPLATE_KEY, JSON.stringify(all));
};

const STATUS_TONE = {
  PLANNED: "neutral",
  ACTIVE: "success",
  COMPLETED: "info",
  CANCELLED: "danger",
};

const emptyForm = {
  name: "",
  channel: "",
  description: "",
  startDate: "",
  endDate: "",
  budget: "",
  targetLeads: "",
  template: "",
  leadIds: [],
};

export default function LeadCampaigns() {
  const [campaigns, setCampaigns] = useState([]);
  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);
  const [values, setValues] = useState(emptyForm);

  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState({ ...emptyForm, status: "PLANNED" });

  const [errors, setErrors] = useState({});
  const [saving, setSaving] = useState(false);
  const { notify } = useToast();

  // Whole lead list — loaded once, used by both the audience picker on
  // Create/Edit and the Send modal's picker.
  const [leads, setLeads] = useState([]);
  const [leadsLoading, setLeadsLoading] = useState(true);

  // Send Campaign modal — client-side helper: opens a WhatsApp (wa.me) or
  // Email (mailto) compose window per selected lead and logs it on that lead's
  // interaction timeline. Purely a convenience layered over the campaign record.
  const [sendModalOpen, setSendModalOpen] = useState(false);
  const [sendCampaign, setSendCampaign] = useState(null);
  const [selectedLeadIds, setSelectedLeadIds] = useState(new Set());
  const [sending, setSending] = useState(false);

  const load = () => {
    setLoading(true);
    getCampaigns()
      .then((rows) => {
        const templates = readTemplates();
        setCampaigns(rows.map((c) => ({ ...c, template: templates[c.id] || "" })));
      })
      .catch((e) => notify(e.message || "Could not load campaigns.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    setLeadsLoading(true);
    getLeads()
      .then(setLeads)
      .catch(() => setLeads([]))
      .finally(() => setLeadsLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toggleLeadIdIn = (setter) => (id) => {
    setter((v) => ({
      ...v,
      leadIds: v.leadIds.includes(id) ? v.leadIds.filter((x) => x !== id) : [...v.leadIds, id],
    }));
  };
  const toggleFormLead = toggleLeadIdIn(setValues);
  const toggleEditLead = toggleLeadIdIn(setEditValues);

  const handleCreate = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { name: [required], channel: [required] });
    if (!values.leadIds.length) validation.leadIds = "Select at least one lead for this campaign's audience.";
    setErrors(validation);
    if (Object.keys(validation).length) return;

    setSaving(true);
    try {
      const created = await createCampaign(values);
      if (values.template) saveTemplate(created.id, values.template);
      notify(`Campaign "${created.name}" created for ${values.leadIds.length} lead(s).`, { type: "success" });
      setModalOpen(false);
      setValues(emptyForm);
      load();
    } catch (err) {
      notify(err.message || "Could not create the campaign.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const openEdit = (c) => {
    setEditingId(c.id);
    setEditValues({
      name: c.name,
      channel: c.channel,
      description: c.description || "",
      startDate: c.startDate || "",
      endDate: c.endDate || "",
      budget: c.budget ?? "",
      targetLeads: c.targetLeads ?? "",
      status: c.status || "PLANNED",
      template: c.template || "",
      leadIds: c.leadIds || [],
    });
    setErrors({});
    setEditModalOpen(true);
  };

  const handleEditSave = async (e) => {
    e.preventDefault();
    const validation = validateForm(editValues, { name: [required], channel: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    setSaving(true);
    try {
      const updated = await updateCampaign(editingId, editValues);
      saveTemplate(editingId, editValues.template || "");
      notify("Campaign updated.", { type: "success", title: "Campaign Updated" });
      setEditModalOpen(false);
      setEditingId(null);
      load();
      return updated;
    } catch (err) {
      notify(err.message || "Could not update the campaign.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (c) => {
    try {
      await deleteCampaign(c.id);
      notify(`Campaign "${c.name}" cancelled.`, { type: "success", title: "Cancelled" });
      load();
    } catch (err) {
      notify(err.message || "Could not cancel the campaign.", { type: "error" });
    }
  };

  const handleComplete = async (c) => {
    try {
      await updateCampaign(c.id, { ...c, status: "COMPLETED" });
      notify("Campaign marked complete.", { type: "success", title: "Campaign Completed" });
      load();
    } catch (err) {
      notify(err.message || "Could not update the campaign.", { type: "error" });
    }
  };

  const openSendModal = async (campaign) => {
    setSendCampaign(campaign);
    setSendModalOpen(true);
    setLeadsLoading(true);
    try {
      const allLeads = await getLeads();
      setLeads(allLeads);
      // Default to the audience picked when the campaign was created/edited; an
      // older campaign with none saved falls back to everyone, same as before.
      const savedAudience = campaign.leadIds && campaign.leadIds.length ? new Set(campaign.leadIds) : null;
      setSelectedLeadIds(savedAudience || new Set(allLeads.map((l) => l.id)));
    } finally {
      setLeadsLoading(false);
    }
  };

  const toggleLeadSelected = (id) => {
    setSelectedLeadIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const isEmailCampaign = (c) => c?.channel === "EMAIL";

  const buildMessage = (lead) => {
    const template = sendCampaign?.template?.trim() || DEFAULT_TEMPLATE;
    return template
      .replace(/{{name}}/g, lead.name || "there")
      .replace(/{{type}}/g, lead.type || lead.leadType || "")
      .replace(/{{link}}/g, `${window.location.origin}/register?leadId=${lead.id}`);
  };

  const handleSendCampaign = async () => {
    const targets = leads.filter((l) => selectedLeadIds.has(l.id));
    if (targets.length === 0) {
      notify("Select at least one lead to send to.", { type: "warning" });
      return;
    }

    setSending(true);
    let sentCount = 0;
    let skippedCount = 0;

    for (const lead of targets) {
      const message = buildMessage(lead);
      if (isEmailCampaign(sendCampaign)) {
        if (!lead.email) {
          skippedCount++;
          continue;
        }
        window.open(
          `mailto:${lead.email}?subject=${encodeURIComponent(sendCampaign.name)}&body=${encodeURIComponent(message)}`,
          "_blank"
        );
      } else {
        if (!lead.phone) {
          skippedCount++;
          continue;
        }
        const clean = lead.phone.replace(/[^0-9]/g, "");
        const formatted = clean.length === 10 ? `91${clean}` : clean;
        window.open(`https://wa.me/${formatted}?text=${encodeURIComponent(message)}`, "_blank");
      }

      try {
        await logInteraction(lead.id, {
          channel: isEmailCampaign(sendCampaign) ? "Email" : "WhatsApp",
          outcome: "Campaign Sent",
          notes: `[${sendCampaign.name}] ${message}`,
        });
      } catch {
        /* interaction logging is best-effort */
      }
      sentCount++;
    }

    notify(
      skippedCount > 0
        ? `Opened ${sentCount} message${sentCount === 1 ? "" : "s"}, skipped ${skippedCount} missing ${isEmailCampaign(sendCampaign) ? "emails" : "phone numbers"}.`
        : `Opened ${sentCount} message${sentCount === 1 ? "" : "s"}.`,
      { type: sentCount > 0 ? "success" : "warning", title: "Campaign Send" }
    );

    setSending(false);
    setSendModalOpen(false);
  };

  const channelOptions = CAMPAIGN_CHANNELS.map((c) => ({ value: c.value, label: c.label }));

  const formFields = (v, set, toggleLead) => (
    <>
      <Input
        label="Campaign name"
        required
        placeholder="e.g. September Enrollment Push"
        value={v.name}
        onChange={(e) => set((s) => ({ ...s, name: e.target.value }))}
        error={errors.name}
      />
      <div className="grid sm:grid-cols-2 gap-4">
        <Select
          label="Channel"
          required
          placeholder="Select channel"
          options={channelOptions}
          value={v.channel}
          onChange={(e) => set((s) => ({ ...s, channel: e.target.value }))}
          error={errors.channel}
        />
        <Input
          label="Target leads"
          type="number"
          min="0"
          placeholder="e.g. 200"
          value={v.targetLeads}
          onChange={(e) => set((s) => ({ ...s, targetLeads: e.target.value }))}
        />
      </div>
      <div className="grid sm:grid-cols-3 gap-4">
        <Input
          label="Start date"
          type="date"
          value={v.startDate}
          onChange={(e) => set((s) => ({ ...s, startDate: e.target.value }))}
        />
        <Input
          label="End date"
          type="date"
          value={v.endDate}
          onChange={(e) => set((s) => ({ ...s, endDate: e.target.value }))}
        />
        <Input
          label="Budget (₹)"
          type="number"
          min="0"
          placeholder="e.g. 50000"
          value={v.budget}
          onChange={(e) => set((s) => ({ ...s, budget: e.target.value }))}
        />
      </div>
      <Textarea
        label="Description"
        placeholder="What is this campaign for? Who does it target?"
        rows={3}
        value={v.description}
        onChange={(e) => set((s) => ({ ...s, description: e.target.value }))}
      />
      <Textarea
        label="Message template (used by the Send helper — not stored on the server)"
        placeholder="Hi {{name}}, ready to kickstart your career with Moriah Skill Hub?"
        rows={3}
        value={v.template}
        onChange={(e) => set((s) => ({ ...s, template: e.target.value }))}
      />

      <div>
        <div className="flex items-center justify-between mb-1.5">
          <span className="text-sm font-medium text-ink-900">
            Audience <span className="text-error-500">*</span>
          </span>
          <span className="text-xs text-ink-500">{v.leadIds.length} selected</span>
        </div>
        {errors.leadIds && <p className="text-xs text-error-500 mb-1.5">{errors.leadIds}</p>}
        {leadsLoading ? (
          <p className="text-xs text-ink-400 py-3 text-center">Loading leads…</p>
        ) : leads.length === 0 ? (
          <p className="text-xs text-ink-400 py-3 text-center border border-border rounded-lg">
            No leads in your pipeline yet — capture some first.
          </p>
        ) : (
          <div className="flex flex-col divide-y divide-border border border-border rounded-xl max-h-[200px] overflow-y-auto bg-white">
            <label className="flex items-center gap-3 px-3 py-2 bg-cream-50/60 cursor-pointer sticky top-0">
              <input
                type="checkbox"
                checked={v.leadIds.length === leads.length}
                onChange={() =>
                  set((s) => ({ ...s, leadIds: s.leadIds.length === leads.length ? [] : leads.map((l) => l.id) }))
                }
                className="accent-primary-700"
              />
              <span className="text-xs font-semibold text-ink-700">Select all</span>
            </label>
            {leads.map((lead) => (
              <label key={lead.id} className="flex items-center gap-3 px-3 py-2 hover:bg-cream-50/60 cursor-pointer">
                <input
                  type="checkbox"
                  checked={v.leadIds.includes(lead.id)}
                  onChange={() => toggleLead(lead.id)}
                  className="accent-primary-700"
                />
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-ink-900 truncate">{lead.name}</p>
                  <p className="text-xs text-ink-400 truncate">{lead.phone || lead.email || "No contact on file"}</p>
                </div>
              </label>
            ))}
          </div>
        )}
      </div>
    </>
  );

  return (
    <div>
      <PageHeader
        title="Campaigns"
        subtitle="Plan outreach campaigns and bulk-message leads via WhatsApp or email"
        breadcrumbs={[{ label: "Dashboard", to: "/leads/dashboard" }, { label: "Campaigns" }]}
        action={
          <Button
            icon={Plus}
            onClick={() => {
              setErrors({});
              setValues(emptyForm);
              setModalOpen(true);
            }}
          >
            New Campaign
          </Button>
        }
      />

      <Card>
        <Table
          loading={loading}
          data={campaigns}
          onRowClick={(r) => openSendModal(r)}
          emptyTitle="No campaigns yet"
          emptyHint="Create a campaign to plan outreach and bulk-message your leads."
          columns={[
            { key: "name", header: "Campaign", className: "text-left" },
            {
              key: "channel",
              header: "Channel",
              className: "text-left",
              render: (r) => (
                <Badge tone={r.channel === "EMAIL" ? "info" : "success"}>
                  {r.channel === "EMAIL" ? (
                    <Mail size={11} className="mr-1" />
                  ) : (
                    <MessageCircle size={11} className="mr-1" />
                  )}
                  {r.channelLabel}
                </Badge>
              ),
            },
            {
              key: "window",
              header: "Window",
              className: "text-left",
              render: (r) => (
                <span className="text-xs text-ink-500">
                  {r.startDate || "—"}
                  {r.endDate ? ` → ${r.endDate}` : ""}
                </span>
              ),
            },
            {
              key: "targetLeads",
              header: "Target",
              className: "text-left",
              render: (r) => (r.targetLeads != null ? r.targetLeads : "—"),
            },
            {
              key: "budget",
              header: "Budget",
              className: "text-left",
              render: (r) => (r.budget != null ? `₹${r.budget.toLocaleString("en-IN")}` : "—"),
            },
            {
              key: "status",
              header: "Status",
              className: "text-left",
              render: (r) => <Badge tone={STATUS_TONE[r.status] || "neutral"}>{r.status}</Badge>,
            },
            {
              key: "action",
              header: "",
              className: "text-right",
              render: (r) => (
                <div className="flex gap-2 justify-end" onClick={(e) => e.stopPropagation()}>
                  {(r.status === "ACTIVE" || r.status === "PLANNED") && (
                    <Button size="sm" icon={Send} onClick={() => openSendModal(r)}>
                      Send
                    </Button>
                  )}
                  {r.status !== "COMPLETED" && r.status !== "CANCELLED" && (
                    <Button size="sm" variant="secondary" icon={Check} onClick={() => handleComplete(r)}>
                      Complete
                    </Button>
                  )}
                  <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(r)}>
                    Edit
                  </Button>
                  {r.status !== "CANCELLED" && (
                    <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r)}>
                      Cancel
                    </Button>
                  )}
                </div>
              ),
            },
          ]}
        />
      </Card>

      {/* Create Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Create Campaign"
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button icon={Megaphone} onClick={handleCreate} disabled={saving}>
              {saving ? "Saving…" : "Create Campaign"}
            </Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleCreate}>
          {formFields(values, setValues, toggleFormLead)}
        </form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        open={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Edit Campaign"
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditModalOpen(false)} disabled={saving}>
              Cancel
            </Button>
            <Button onClick={handleEditSave} disabled={saving}>
              {saving ? "Saving…" : "Save Changes"}
            </Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleEditSave}>
          {formFields(editValues, setEditValues, toggleEditLead)}
          <div className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-ink-900">Campaign status</span>
            <select
              value={editValues.status}
              onChange={(e) => setEditValues((v) => ({ ...v, status: e.target.value }))}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 bg-white"
            >
              <option value="PLANNED">Planned</option>
              <option value="ACTIVE">Active</option>
              <option value="COMPLETED">Completed</option>
              <option value="CANCELLED">Cancelled</option>
            </select>
          </div>
        </form>
      </Modal>

      {/* Send Campaign Modal — client-side WhatsApp/email compose per lead */}
      <Modal
        open={sendModalOpen}
        onClose={() => {
          if (!sending) setSendModalOpen(false);
        }}
        title={sendCampaign ? `Send: ${sendCampaign.name}` : "Send Campaign"}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setSendModalOpen(false)} disabled={sending}>
              Cancel
            </Button>
            <Button
              icon={sending ? Loader2 : Send}
              onClick={handleSendCampaign}
              disabled={sending || leads.length === 0}
            >
              {sending
                ? "Sending…"
                : `Send to ${selectedLeadIds.size} lead${selectedLeadIds.size === 1 ? "" : "s"}`}
            </Button>
          </>
        }
      >
        {sendCampaign && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <div className="flex items-center gap-2 text-xs text-ink-500 bg-cream-50 border border-border rounded-lg px-3 py-2">
              <Users size={14} />
              Sending via{" "}
              <strong className="text-ink-900">
                {isEmailCampaign(sendCampaign) ? "Email" : "WhatsApp"}
              </strong>{" "}
              — pick which leads to message.
            </div>

            {leadsLoading ? (
              <div className="flex justify-center py-8">
                <Loader2 className="animate-spin text-primary-600" size={20} />
              </div>
            ) : leads.length === 0 ? (
              <p className="text-sm text-ink-400 text-center py-8">
                No leads in your pipeline yet. Add leads first.
              </p>
            ) : (
              <div className="flex flex-col divide-y divide-border border border-border rounded-xl max-h-[260px] overflow-y-auto bg-white">
                {leads.map((lead) => {
                  const missing = isEmailCampaign(sendCampaign) ? !lead.email : !lead.phone;
                  return (
                    <label
                      key={lead.id}
                      className="flex items-center gap-3 px-3 py-2.5 hover:bg-cream-50/60 cursor-pointer"
                    >
                      <input
                        type="checkbox"
                        checked={selectedLeadIds.has(lead.id)}
                        onChange={() => toggleLeadSelected(lead.id)}
                        className="accent-primary-700"
                      />
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-medium text-ink-900 truncate">{lead.name}</p>
                        <p className="text-xs text-ink-400 truncate">
                          {isEmailCampaign(sendCampaign)
                            ? lead.email || "No email on file"
                            : lead.phone || "No phone on file"}
                        </p>
                      </div>
                      {missing && <Badge tone="warning">Missing contact</Badge>}
                    </label>
                  );
                })}
              </div>
            )}

            {leads.length > 0 && (
              <div className="p-3 bg-cream-50/80 rounded-xl border border-border">
                <p className="text-xs font-semibold uppercase tracking-wider text-ink-700 mb-1.5">
                  Message Preview
                </p>
                <p className="text-sm text-ink-700 whitespace-pre-wrap">
                  {buildMessage(leads.find((l) => selectedLeadIds.has(l.id)) || leads[0])}
                </p>
              </div>
            )}

            <p className="text-[11px] text-ink-400">
              Each selected lead gets an individually personalized{" "}
              {isEmailCampaign(sendCampaign) ? "email" : "WhatsApp"} message and is logged to their
              interaction timeline.
            </p>
          </div>
        )}
      </Modal>
    </div>
  );
}
