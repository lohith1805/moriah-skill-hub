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
import { getLeads, logInteraction } from "../../services/crmService";

const CAMPAIGNS = [];

// Maps a campaign's audience label to the CRM lead "type" it targets, so a
// campaign can pull in the actual matching leads to message.
const AUDIENCE_TO_LEAD_TYPE = {
  "B2C Students": "Student (B2C)",
  "Colleges": "College Tie-up",
  "Corporates": "Enterprise / Corporate"
};

const DEFAULT_TEMPLATE = "Hi {{name}}! 👋 Welcome to Moriah Skill Hub. We'd love to help you kickstart your career with our project-based training tracks.";

export default function LeadCampaigns() {
  const [campaigns, setCampaigns] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // Create states
  const [modalOpen, setModalOpen] = useState(false);
  const [values, setValues] = useState({ name: "", channel: "", audience: "", template: "" });
  
  // Edit states
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState({ name: "", channel: "", audience: "", template: "", status: "" });

  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  // Send Campaign Modal — shows matching leads (by audience -> lead type)
  // so the agent can pick who to message, then actually sends via WhatsApp
  // (wa.me deep link, like the per-lead flow) or Email (mailto), one per
  // selected lead, and logs it on that lead's interaction timeline.
  const [sendModalOpen, setSendModalOpen] = useState(false);
  const [sendCampaign, setSendCampaign] = useState(null);
  const [matchingLeads, setMatchingLeads] = useState([]);
  const [leadsLoading, setLeadsLoading] = useState(false);
  const [selectedLeadIds, setSelectedLeadIds] = useState(new Set());
  const [sending, setSending] = useState(false);

  useEffect(() => {
    const saved = localStorage.getItem("msh_campaigns");
    if (saved) {
      setCampaigns(JSON.parse(saved));
    } else {
      localStorage.setItem("msh_campaigns", JSON.stringify(CAMPAIGNS));
      setCampaigns(CAMPAIGNS);
    }
    setLoading(false);
  }, []);

  const handleCreate = (e) => {
    e.preventDefault();
    const validation = validateForm(values, { name: [required], channel: [required], audience: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const created = {
      id: `c_${Date.now()}`,
      name: values.name,
      channel: values.channel === "whatsapp" ? "WhatsApp" : "Email",
      audience: values.audience === "b2c" ? "B2C Students" : values.audience === "colleges" ? "Colleges" : "Corporates",
      sent: 0,
      replied: 0,
      status: "Active",
      template: values.template || ""
    };

    const updated = [created, ...campaigns];
    setCampaigns(updated);
    localStorage.setItem("msh_campaigns", JSON.stringify(updated));
    notify(`Campaign "${values.name}" created and launched.`, { type: "success" });
    setModalOpen(false);
    setValues({ name: "", channel: "", audience: "", template: "" });
  };

  const openEdit = (c) => {
    setEditingId(c.id);
    setEditValues({
      name: c.name,
      channel: c.channel === "WhatsApp" ? "whatsapp" : "email",
      audience: c.audience === "B2C Students" ? "b2c" : c.audience === "Colleges" ? "colleges" : "corporates",
      template: c.template || "",
      status: c.status,
    });
    setErrors({});
    setEditModalOpen(true);
  };

  const handleEditSave = (e) => {
    e.preventDefault();
    const validation = validateForm(editValues, { name: [required], channel: [required], audience: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const updated = campaigns.map((c) => {
      if (c.id === editingId) {
        return {
          ...c,
          name: editValues.name,
          channel: editValues.channel === "whatsapp" ? "WhatsApp" : "Email",
          audience: editValues.audience === "b2c" ? "B2C Students" : editValues.audience === "colleges" ? "Colleges" : "Corporates",
          template: editValues.template,
          status: editValues.status,
        };
      }
      return c;
    });

    setCampaigns(updated);
    localStorage.setItem("msh_campaigns", JSON.stringify(updated));
    notify("Campaign updated successfully.", { type: "success", title: "Campaign Updated" });
    setEditModalOpen(false);
    setEditingId(null);
  };

  const handleDelete = (id) => {
    const campaign = campaigns.find(c => c.id === id);
    const updated = campaigns.filter((c) => c.id !== id);
    setCampaigns(updated);
    localStorage.setItem("msh_campaigns", JSON.stringify(updated));
    notify(`Campaign "${campaign?.name}" deleted successfully.`, { type: "success", title: "Deleted" });
  };

  const handleComplete = (id) => {
    const nextCampaigns = campaigns.map((c) => (c.id === id ? { ...c, status: "Completed" } : c));
    setCampaigns(nextCampaigns);
    localStorage.setItem("msh_campaigns", JSON.stringify(nextCampaigns));
    notify("Campaign marked as completed.", { type: "success", title: "Campaign Completed" });
  };

  // Open the Send modal — loads leads and pre-selects everyone whose type
  // matches this campaign's target audience.
  const openSendModal = async (campaign) => {
    setSendCampaign(campaign);
    setSendModalOpen(true);
    setLeadsLoading(true);
    const allLeads = await getLeads();
    const targetType = AUDIENCE_TO_LEAD_TYPE[campaign.audience];
    const matched = allLeads.filter((l) => l.type === targetType);
    setMatchingLeads(matched);
    setSelectedLeadIds(new Set(matched.map((l) => l.id)));
    setLeadsLoading(false);
  };

  const toggleLeadSelected = (id) => {
    setSelectedLeadIds((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const buildMessage = (lead) => {
    const template = sendCampaign?.template?.trim() || DEFAULT_TEMPLATE;
    return template
      .replace(/{{name}}/g, lead.name)
      .replace(/{{type}}/g, lead.type)
      .replace(/{{link}}/g, `${window.location.origin}/register?leadId=${lead.id}`);
  };

  // Actually sends the campaign: opens a WhatsApp/Email compose window per
  // selected lead (personalized with their name), logs the outreach on
  // that lead's interaction timeline, and bumps the campaign's Sent count.
  const handleSendCampaign = async () => {
    const targets = matchingLeads.filter((l) => selectedLeadIds.has(l.id));
    if (targets.length === 0) {
      notify("Select at least one lead to send to.", { type: "warning" });
      return;
    }

    setSending(true);
    let sentCount = 0;
    let skippedCount = 0;

    for (const lead of targets) {
      const message = buildMessage(lead);
      if (sendCampaign.channel === "WhatsApp") {
        if (!lead.phone) { skippedCount++; continue; }
        const clean = lead.phone.replace(/[^0-9]/g, "");
        const formatted = clean.length === 10 ? `91${clean}` : clean;
        window.open(`https://wa.me/${formatted}?text=${encodeURIComponent(message)}`, "_blank");
      } else {
        if (!lead.email) { skippedCount++; continue; }
        window.open(`mailto:${lead.email}?subject=${encodeURIComponent(sendCampaign.name)}&body=${encodeURIComponent(message)}`, "_blank");
      }

      await logInteraction(lead.id, {
        channel: sendCampaign.channel,
        outcome: "Campaign Sent",
        notes: `[${sendCampaign.name}] ${message}`
      });
      sentCount++;
    }

    const updated = campaigns.map((c) => (c.id === sendCampaign.id ? { ...c, sent: (c.sent || 0) + sentCount } : c));
    setCampaigns(updated);
    localStorage.setItem("msh_campaigns", JSON.stringify(updated));

    notify(
      skippedCount > 0
        ? `Sent to ${sentCount} lead${sentCount === 1 ? "" : "s"}, skipped ${skippedCount} missing ${sendCampaign.channel === "WhatsApp" ? "phone numbers" : "emails"}.`
        : `Campaign sent to ${sentCount} lead${sentCount === 1 ? "" : "s"}.`,
      { type: sentCount > 0 ? "success" : "warning", title: "Campaign Sent" }
    );

    setSending(false);
    setSendModalOpen(false);
  };

  return (
    <div>
      <PageHeader
        title="Campaigns"
        subtitle="Pre-built rich email templates and 1-click WhatsApp messaging"
        breadcrumbs={[{ label: "Dashboard", to: "/leads/dashboard" }, { label: "Campaigns" }]}
        action={<Button icon={Plus} onClick={() => { setErrors({}); setModalOpen(true); }}>New Campaign</Button>}
      />

      <Card>
        <Table
          loading={loading}
          data={campaigns}
          onRowClick={(r) => openSendModal(r)}
          emptyTitle="No campaigns yet"
          emptyHint="Create a campaign to bulk-message leads matching an audience segment."
          columns={[
            { key: "name", header: "Campaign", className: "text-left" },
            { key: "channel", header: "Channel", className: "text-left", render: (r) => <Badge tone={r.channel === "WhatsApp" ? "success" : "info"}>{r.channel === "WhatsApp" ? <MessageCircle size={11} className="mr-1" /> : <Mail size={11} className="mr-1" />}{r.channel}</Badge> },
            { key: "audience", header: "Audience", className: "text-left" },
            { key: "sent", header: "Sent", className: "text-left" },
            { key: "replied", header: "Replied", className: "text-left" },
            { key: "status", header: "Status", className: "text-left", render: (r) => <Badge tone={r.status === "Active" ? "success" : "neutral"}>{r.status}</Badge> },
            { key: "action", header: "", className: "text-right", render: (r) => (
              <div className="flex gap-2 justify-end" onClick={(e) => e.stopPropagation()}>
                {r.status === "Active" && (
                  <Button size="sm" icon={Send} onClick={() => openSendModal(r)}>Send</Button>
                )}
                {r.status === "Active" && (
                  <Button size="sm" variant="secondary" icon={Check} onClick={() => handleComplete(r.id)}>Complete</Button>
                )}
                <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(r)}>Edit</Button>
                <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r.id)}>Delete</Button>
              </div>
            ) },
          ]}
        />
      </Card>

      {/* Create Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Create Campaign"
        footer={<>
          <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
          <Button icon={Megaphone} onClick={handleCreate}>Launch Campaign</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleCreate}>
          <Input label="Campaign name" required placeholder="e.g. September Enrollment Push" value={values.name} onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))} error={errors.name} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Select label="Channel" required placeholder="Select channel" options={[{ value: "whatsapp", label: "WhatsApp" }, { value: "email", label: "Email" }]} value={values.channel} onChange={(e) => setValues((v) => ({ ...v, channel: e.target.value }))} error={errors.channel} />
            <Select label="Audience" required placeholder="Select audience" options={[{ value: "b2c", label: "B2C Students" }, { value: "colleges", label: "Colleges" }, { value: "corporates", label: "Corporates" }]} value={values.audience} onChange={(e) => setValues((v) => ({ ...v, audience: e.target.value }))} error={errors.audience} />
          </div>
          <Textarea label="Message template" placeholder="Hi {{name}}, ready to kickstart your career with Moriah Skill Hub?" rows={4} value={values.template} onChange={(e) => setValues((v) => ({ ...v, template: e.target.value }))} />
        </form>
      </Modal>

      {/* Edit Modal */}
      <Modal
        open={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Edit Campaign"
        footer={<>
          <Button variant="secondary" onClick={() => setEditModalOpen(false)}>Cancel</Button>
          <Button onClick={handleEditSave}>Save Changes</Button>
        </>}
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleEditSave}>
          <Input label="Campaign name" required placeholder="e.g. September Enrollment Push" value={editValues.name} onChange={(e) => setEditValues((v) => ({ ...v, name: e.target.value }))} error={errors.name} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Select label="Channel" required placeholder="Select channel" options={[{ value: "whatsapp", label: "WhatsApp" }, { value: "email", label: "Email" }]} value={editValues.channel} onChange={(e) => setEditValues((v) => ({ ...v, channel: e.target.value }))} error={errors.channel} />
            <Select label="Audience" required placeholder="Select audience" options={[{ value: "b2c", label: "B2C Students" }, { value: "colleges", label: "Colleges" }, { value: "corporates", label: "Corporates" }]} value={editValues.audience} onChange={(e) => setEditValues((v) => ({ ...v, audience: e.target.value }))} error={errors.audience} />
          </div>
          <div className="flex flex-col gap-1.5">
            <span className="text-sm font-medium text-ink-900">Campaign Status</span>
            <select
              value={editValues.status}
              onChange={(e) => setEditValues((v) => ({ ...v, status: e.target.value }))}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 bg-white"
            >
              <option value="Active">Active</option>
              <option value="Completed">Completed</option>
            </select>
          </div>
          <Textarea label="Message template" placeholder="Hi {{name}}, ready to kickstart your career with Moriah Skill Hub?" rows={4} value={editValues.template} onChange={(e) => setEditValues((v) => ({ ...v, template: e.target.value }))} />
        </form>
      </Modal>

      {/* Send Campaign Modal — pick which matching leads get messaged */}
      <Modal
        open={sendModalOpen}
        onClose={() => { if (!sending) setSendModalOpen(false); }}
        title={sendCampaign ? `Send: ${sendCampaign.name}` : "Send Campaign"}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setSendModalOpen(false)} disabled={sending}>Cancel</Button>
            <Button icon={sending ? Loader2 : Send} onClick={handleSendCampaign} disabled={sending || matchingLeads.length === 0}>
              {sending ? "Sending…" : `Send to ${selectedLeadIds.size} lead${selectedLeadIds.size === 1 ? "" : "s"}`}
            </Button>
          </>
        }
      >
        {sendCampaign && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <div className="flex items-center gap-2 text-xs text-ink-500 bg-cream-50 border border-border rounded-lg px-3 py-2">
              <Users size={14} />
              Targeting <strong className="text-ink-900">{sendCampaign.audience}</strong> via{" "}
              <strong className="text-ink-900">{sendCampaign.channel}</strong> — matched by lead type "{AUDIENCE_TO_LEAD_TYPE[sendCampaign.audience]}"
            </div>

            {leadsLoading ? (
              <div className="flex justify-center py-8"><Loader2 className="animate-spin text-primary-600" size={20} /></div>
            ) : matchingLeads.length === 0 ? (
              <p className="text-sm text-ink-400 text-center py-8">
                No leads in your pipeline currently match this audience. Add or ingest leads of this type first.
              </p>
            ) : (
              <div className="flex flex-col divide-y divide-border border border-border rounded-xl max-h-[260px] overflow-y-auto bg-white">
                {matchingLeads.map((lead) => (
                  <label key={lead.id} className="flex items-center gap-3 px-3 py-2.5 hover:bg-cream-50/60 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedLeadIds.has(lead.id)}
                      onChange={() => toggleLeadSelected(lead.id)}
                      className="accent-primary-700"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-ink-900 truncate">{lead.name}</p>
                      <p className="text-xs text-ink-400 truncate">
                        {sendCampaign.channel === "WhatsApp" ? (lead.phone || "No phone on file") : (lead.email || "No email on file")}
                      </p>
                    </div>
                    {((sendCampaign.channel === "WhatsApp" && !lead.phone) || (sendCampaign.channel === "Email" && !lead.email)) && (
                      <Badge tone="warning">Missing contact</Badge>
                    )}
                  </label>
                ))}
              </div>
            )}

            {matchingLeads.length > 0 && (
              <div className="p-3 bg-cream-50/80 rounded-xl border border-border">
                <p className="text-xs font-semibold uppercase tracking-wider text-ink-700 mb-1.5">Message Preview</p>
                <p className="text-sm text-ink-700 whitespace-pre-wrap">
                  {buildMessage(matchingLeads.find((l) => selectedLeadIds.has(l.id)) || matchingLeads[0])}
                </p>
              </div>
            )}

            <p className="text-[11px] text-ink-400">
              Each selected lead gets an individually personalized {sendCampaign.channel === "WhatsApp" ? "WhatsApp" : "email"} message and is logged to their interaction timeline.
            </p>
          </div>
        )}
      </Modal>
    </div>
  );
}