import { useEffect, useState } from "react";
import {
  Plus, Phone, MessageCircle, Mail, Edit, Trash2, Link2, Copy, Check,
  Search, Filter, Upload, Clock, Calendar, CheckCircle2, Video, AlertCircle,
  FileSpreadsheet, Sparkles, Send, UserCheck, ShieldAlert, Download, AlertTriangle
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import Badge from "../../components/ui/Badge";
import KanbanBoard from "../../components/widgets/KanbanBoard";
import FileUpload from "../../components/ui/FileUpload";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import {
  getLeads, createLead, updateLead, updateLeadStage, deleteLead,
  logInteraction, checkDuplicateLead, bulkImportLeads, getLeadActivities,
  isBackwardStage, PREAPPROVED_WHATSAPP_TEMPLATES,
  downloadLeadImportTemplate, parseLeadImportFile
} from "../../services/crmService";
import { useToast } from "../../context/ToastContext";
import { validateForm, required, isPhone, isEmail } from "../../utils/validators";
import { CURRENCY } from "../../utils/constants";

const PIPELINE_STAGES = [
  "New Lead",
  "Contacted",
  "Demo Scheduled",
  "Plan Selected",
  "Payment Pending",
  "Won / Enrolled"
];

// The five sources the backend LeadSource enum supports.
const LEAD_SOURCES = [
  "Landing Page",
  "College Outreach",
  "Corporate Inquiry",
  "Referral",
  "Walk-in"
].map((s) => ({ value: s, label: s }));

const LEAD_TYPES = [
  { value: "Student (B2C)", label: "Student (B2C) - Direct" },
  { value: "College Tie-up", label: "College Tie-up (B2B2C)" },
  { value: "Enterprise / Corporate", label: "Enterprise / Corporate (B2B)" }
];

export default function LeadPipeline() {
  const [leads, setLeads] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const { notify } = useToast();

  // Create Modal
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [values, setValues] = useState({ name: "", phone: "", email: "", type: "Student (B2C)", source: "Landing Page" });
  const [duplicateWarning, setDuplicateWarning] = useState(null);
  const [errors, setErrors] = useState({});

  // Bulk Ingestion Modal
  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkText, setBulkText] = useState("");
  const [bulkResult, setBulkResult] = useState(null);
  const [bulkFile, setBulkFile] = useState(null);
  const [bulkParsing, setBulkParsing] = useState(false);
  const [bulkPreview, setBulkPreview] = useState(null); // { rows, problems }
  const [bulkImporting, setBulkImporting] = useState(false);

  // Edit Modal
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingLead, setEditingLead] = useState(null);
  const [editValues, setEditValues] = useState({ name: "", phone: "", email: "", type: "", source: "", stage: "", dealValue: "" });

  // WhatsApp Omnichannel Modal
  const [whatsappOpen, setWhatsappOpen] = useState(false);
  const [activeLead, setActiveLead] = useState(null);
  const [selectedTemplate, setSelectedTemplate] = useState(PREAPPROVED_WHATSAPP_TEMPLATES[0].id);
  const [customMsg, setCustomMsg] = useState("");

  // Interaction History / Call Logging Drawer Modal
  const [interactionOpen, setInteractionOpen] = useState(false);
  const [interactionLead, setInteractionLead] = useState(null);
  const [activityHistory, setActivityHistory] = useState([]);
  const [activityLoading, setActivityLoading] = useState(false);
  const [logChannel, setLogChannel] = useState("Call");
  const [callOutcome, setCallOutcome] = useState("Connected");
  const [interactionNotes, setInteractionNotes] = useState("");
  const [followUpDate, setFollowUpDate] = useState("");
  const [zoomLink, setZoomLink] = useState("");

  // Enrollment Link Modal
  const [enrollModalOpen, setEnrollModalOpen] = useState(false);
  const [enrollLead, setEnrollLead] = useState(null);
  const [linkCopied, setLinkCopied] = useState(false);

  const load = async () => {
    setLoading(true);
    const data = await getLeads();
    setLeads(data);
    setLoading(false);
  };

  useEffect(() => {
    load();
  }, []);

  // Stage change (Kanban drag). The backend enforces: no stage-skipping, a
  // reason for any backward move, and the converted student's account for
  // "Won / Enrolled" — so gather those before firing the request.
  const move = async (leadId, targetStage) => {
    const lead = leads.find((l) => l.id === leadId);
    if (!lead || lead.stage === targetStage) return;

    const opts = {};
    if (isBackwardStage(lead.stage, targetStage)) {
      const reason = window.prompt(`Moving "${lead.name}" back to "${targetStage}". Reason for the move?`);
      if (!reason || !reason.trim()) {
        notify("A reason is required to move a lead backward.", { type: "warning" });
        return;
      }
      opts.reason = reason.trim();
    }
    if (targetStage === "Won / Enrolled") {
      const email = window.prompt(
        `Mark "${lead.name}" as enrolled.\nEnter the email address the student registered their account with:`,
        lead.email || ""
      );
      if (!email || !email.trim()) return;
      opts.convertedUserEmail = email.trim();
    }

    const prev = leads;
    setLeads((cur) => cur.map((l) => (l.id === leadId ? { ...l, stage: targetStage } : l)));
    try {
      const saved = await updateLeadStage(leadId, targetStage, opts);
      setLeads((cur) => cur.map((l) => (l.id === leadId ? { ...l, ...saved } : l)));
      notify(`Lead moved to "${targetStage}".`, { type: "success" });
    } catch (err) {
      setLeads(prev); // roll back the optimistic move
      notify(err.message || "Could not move the lead.", { type: "error", title: "Move failed" });
    }
  };

  // Realtime Deduplication check on typing
  const handlePhoneChange = (val) => {
    setValues((v) => ({ ...v, phone: val }));
    if (val.length >= 8) {
      const dup = checkDuplicateLead({ phone: val });
      setDuplicateWarning(dup || null);
    } else {
      setDuplicateWarning(null);
    }
  };

  const handleEmailChange = (val) => {
    setValues((v) => ({ ...v, email: val }));
    if (val.includes("@") && val.includes(".")) {
      const dup = checkDuplicateLead({ email: val });
      setDuplicateWarning(dup || null);
    } else if (!duplicateWarning?.phone) {
      setDuplicateWarning(null);
    }
  };

  // Create Lead Submit
  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { name: [required], phone: [required, isPhone], email: [required, isEmail] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    setSubmitting(true);
    try {
      await createLead(values);
      notify("Lead captured and ingested into CRM pipeline.", { type: "success", title: "Lead Ingested" });
      setModalOpen(false);
      setValues({ name: "", phone: "", email: "", type: "Student (B2C)", source: "Landing Page" });
      setDuplicateWarning(null);
      load();
    } catch (err) {
      notify(err.message, { type: "warning", title: "Duplicate Lead Detected" });
    } finally {
      setSubmitting(false);
    }
  };

  // File selected in the bulk modal — parse it (.csv/.xlsx/.xls/.txt) and show
  // a preview before anything actually hits the pipeline.
  const handleBulkFileSelected = async (files) => {
    const file = files?.[0];
    setBulkFile(file || null);
    setBulkPreview(null);
    if (!file) return;
    setBulkParsing(true);
    try {
      const { rows, problems } = await parseLeadImportFile(file);
      setBulkPreview({ rows, problems });
    } catch (err) {
      notify(err.message || "Couldn't read that file.", { type: "error" });
    } finally {
      setBulkParsing(false);
    }
  };

  // Bulk Ingest Process — a parsed file takes priority; falls back to the
  // pasted-text box if no file was picked (same "Name, Phone, Email, Type"
  // per line format as before).
  const handleBulkImport = async () => {
    let parsed = bulkPreview?.rows || [];

    if (!parsed.length && bulkText.trim()) {
      const lines = bulkText.trim().split("\n");
      for (const line of lines) {
        const parts = line.split(",").map((s) => s.trim());
        if (parts.length >= 2) {
          parsed.push({
            name: parts[0],
            phone: parts[1],
            email: parts[2] || "",
            type: parts[3] || "Student (B2C)",
            source: "Bulk Import",
          });
        }
      }
    }

    if (parsed.length === 0) {
      notify("Upload a file or paste at least one 'Name, Phone, Email, Type' line.", { type: "error" });
      return;
    }

    setBulkImporting(true);
    try {
      const res = await bulkImportLeads(parsed);
      setBulkResult(res);
      notify(`Bulk ingestion: ${res.added} leads added, ${res.skipped} duplicates merged/skipped.`, { type: "success" });
      load();
    } finally {
      setBulkImporting(false);
    }
  };

  // WhatsApp Dialog Handlers
  const openWhatsAppModal = (lead) => {
    setActiveLead(lead);
    const tpl = PREAPPROVED_WHATSAPP_TEMPLATES.find((t) => t.id === selectedTemplate) || PREAPPROVED_WHATSAPP_TEMPLATES[0];
    const formattedMsg = tpl.message
      .replace(/{{name}}/g, lead.name)
      .replace(/{{type}}/g, lead.type)
      .replace(/{{link}}/g, `${window.location.origin}/register?leadId=${lead.id}`);
    setCustomMsg(formattedMsg);
    setWhatsappOpen(true);
  };

  const handleTemplateChange = (tplId) => {
    setSelectedTemplate(tplId);
    if (!activeLead) return;
    const tpl = PREAPPROVED_WHATSAPP_TEMPLATES.find((t) => t.id === tplId);
    if (tpl) {
      const formattedMsg = tpl.message
        .replace(/{{name}}/g, activeLead.name)
        .replace(/{{type}}/g, activeLead.type)
        .replace(/{{link}}/g, `${window.location.origin}/register?leadId=${activeLead.id}`);
      setCustomMsg(formattedMsg);
    }
  };

  const sendWhatsAppMessage = async () => {
    if (!activeLead?.phone) {
      notify("No phone number on file for this lead.", { type: "warning" });
      return;
    }

    await logInteraction(activeLead.id, {
      channel: "WhatsApp",
      outcome: "Template Sent",
      notes: customMsg
    });

    const clean = activeLead.phone.replace(/[^0-9]/g, "");
    const formatted = clean.length === 10 ? `91${clean}` : clean;
    const encoded = encodeURIComponent(customMsg);
    window.open(`https://wa.me/${formatted}?text=${encoded}`, "_blank");

    notify(`WhatsApp message sent to ${activeLead.name}.`, { type: "success" });
    setWhatsappOpen(false);
    load();
  };

  // Interaction Drawer
  const loadActivityHistory = (leadId) => {
    setActivityLoading(true);
    getLeadActivities(leadId)
      .then(setActivityHistory)
      .catch(() => setActivityHistory([]))
      .finally(() => setActivityLoading(false));
  };

  const openInteractionModal = (lead) => {
    setInteractionLead(lead);
    setLogChannel("Call");
    setCallOutcome("Connected");
    setInteractionNotes("");
    setFollowUpDate(lead.followUpDate || "");
    setZoomLink("");
    setActivityHistory([]);
    loadActivityHistory(lead.id);
    setInteractionOpen(true);
  };

  const saveInteractionLog = async (e) => {
    e.preventDefault();
    if (!interactionLead) return;

    let notesFinal = interactionNotes;
    if (logChannel === "Zoom Demo" && zoomLink) {
      notesFinal = `Zoom Link: ${zoomLink} | ${notesFinal}`;
    }

    await logInteraction(interactionLead.id, {
      channel: logChannel,
      outcome: callOutcome,
      notes: notesFinal,
      followUpDate: followUpDate || null
    });

    notify(`Interaction logged for ${interactionLead.name}.`, { type: "success" });
    setInteractionNotes("");
    loadActivityHistory(interactionLead.id);
    load();
  };

  // Edit Handlers
  const openEdit = (l) => {
    setEditingLead(l);
    setEditValues({
      name: l.name,
      phone: l.phone,
      email: l.email || "",
      type: l.type,
      source: l.source || "Landing Page",
      stage: l.stage || "New Lead",
      dealValue: l.dealValue ? String(l.dealValue) : ""
    });
    setErrors({});
    setEditModalOpen(true);
  };

  const handleEditSave = async (e) => {
    e.preventDefault();
    const validation = validateForm(editValues, { name: [required], phone: [required, isPhone] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    // Moving to "Won / Enrolled" from the edit modal needs the student's account,
    // same as the board drag.
    const stageChanged = editValues.stage && editValues.stage !== editingLead.stage;
    const opts = {};
    if (stageChanged && editValues.stage === "Won / Enrolled") {
      const email = window.prompt(
        `Mark "${editingLead.name}" as enrolled.\nEnter the email the student registered with:`,
        editingLead.email || ""
      );
      if (!email || !email.trim()) return;
      opts.convertedUserEmail = email.trim();
    }
    if (stageChanged && isBackwardStage(editingLead.stage, editValues.stage)) {
      const reason = window.prompt("Reason for moving this lead backward?");
      if (!reason || !reason.trim()) return;
      opts.reason = reason.trim();
    }

    try {
      await updateLead(editingLead.id, {
        name: editValues.name,
        type: editValues.type,
        stage: stageChanged ? editValues.stage : undefined,
        dealValue: editValues.dealValue ? Number(editValues.dealValue) : null,
        ...opts,
      });
      notify("Lead updated successfully.", { type: "success" });
      setEditModalOpen(false);
      load();
    } catch (err) {
      notify(err.message || "Could not update the lead.", { type: "error", title: "Update failed" });
    }
  };

  const handleDelete = async (id) => {
    const l = leads.find((x) => x.id === id);
    if (!window.confirm(`Archive lead "${l?.name}"? It drops off the board but its history is kept.`)) return;
    try {
      await deleteLead(id);
      notify(`Lead "${l?.name}" archived.`, { type: "success" });
    } catch (err) {
      notify(err.message || "Could not archive the lead.", { type: "error" });
      return;
    }
    load();
  };

  // Filtered Leads
  const filteredLeads = leads.filter((l) => {
    const matchSearch =
      l.name.toLowerCase().includes(search.toLowerCase()) ||
      (l.phone && l.phone.includes(search)) ||
      (l.email && l.email.toLowerCase().includes(search.toLowerCase()));
    const matchType = !typeFilter || l.type === typeFilter;
    return matchSearch && matchType;
  });

  // Calculate stage totals
  const stageStats = PIPELINE_STAGES.reduce((acc, stg) => {
    const matching = filteredLeads.filter((l) => (l.stage === stg || (stg === "New Lead" && l.stage === "New Inquiry") || (stg === "Won / Enrolled" && l.stage === "Enrolled")));
    const val = matching.reduce((sum, item) => sum + (item.dealValue || 0), 0);
    acc[stg] = { count: matching.length, value: val };
    return acc;
  }, {});

  const totalPipelineValue = filteredLeads.reduce((s, l) => s + (l.dealValue || 0), 0);

  return (
    <div className="flex flex-col gap-5">
      <PageHeader
        title="Lead Pipeline & CRM"
        subtitle="Multi-source ingestion, Kanban sales funnel, WhatsApp omnichannel messaging, and interaction logs"
        breadcrumbs={[{ label: "Dashboard", to: "/leads/dashboard" }, { label: "Pipeline" }]}
        action={
          <div className="flex gap-2">
            <Button
              variant="secondary"
              icon={Upload}
              onClick={() => {
                setBulkText("");
                setBulkResult(null);
                setBulkFile(null);
                setBulkPreview(null);
                setBulkOpen(true);
              }}
            >
              Bulk Ingest Leads
            </Button>
            <Button icon={Plus} onClick={() => { setErrors({}); setDuplicateWarning(null); setModalOpen(true); }}>
              Capture Lead
            </Button>
          </div>
        }
      />

      {/* Pipeline Summary Strip & Filters */}
      <Card padding={false} className="p-4 bg-cream-50/50">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="flex flex-wrap items-center gap-3">
            <div className="relative min-w-[240px]">
              <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" />
              <input
                type="text"
                placeholder="Search name, phone, email…"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full pl-9 pr-3 py-1.5 rounded-lg border border-border bg-white text-sm outline-none focus:border-primary-500"
              />
            </div>
            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className="py-1.5 px-3 rounded-lg border border-border bg-white text-sm outline-none focus:border-primary-500"
            >
              <option value="">All Ingestion Channels</option>
              {LEAD_TYPES.map((t) => (
                <option key={t.value} value={t.value}>{t.label}</option>
              ))}
            </select>
          </div>

          <div className="flex items-center gap-4 text-xs sm:text-sm">
            <div className="px-3 py-1.5 rounded-lg bg-white border border-border">
              <span className="text-ink-500">Total Leads: </span>
              <span className="font-semibold text-ink-900">{filteredLeads.length}</span>
            </div>
            <div className="px-3 py-1.5 rounded-lg bg-primary-50 border border-primary-100" title="Sum of confirmed deal values — only counts leads who've actually paid">
              <span className="text-primary-700 font-medium">Revenue (Paid): </span>
              <span className="font-bold text-primary-900">{CURRENCY(totalPipelineValue)}</span>
            </div>
          </div>
        </div>
      </Card>

      {/* Kanban Board */}
      <Card padding={false} className="p-4 overflow-x-auto">
        {loading ? (
          <div className="flex justify-center py-20"><LoadingSpinner label="Loading sales funnel…" /></div>
        ) : (
          <KanbanBoard
            columns={PIPELINE_STAGES}
            items={filteredLeads.map((l) => ({
              ...l,
              status: l.stage === "New Inquiry" ? "New Lead" : l.stage === "Enrolled" ? "Won / Enrolled" : l.stage
            }))}
            onMove={move}
            renderCard={(l) => {
              const interactionsCount = l.interactions?.length || 0;
              const hasFollowUp = !!l.followUpDate;

              return (
                <div className="flex flex-col justify-between h-full min-h-[120px] group text-left">
                  <div>
                    <div className="flex justify-between items-start gap-1">
                      <div>
                        <p className="text-sm font-semibold text-ink-900 truncate">{l.name}</p>
                        <p className="text-xs text-ink-500 truncate mt-0.5">{l.phone || l.email}</p>
                      </div>
                      <div className="opacity-0 group-hover:opacity-100 flex gap-0.5 transition-opacity shrink-0">
                        <button
                          type="button"
                          onClick={(e) => { e.stopPropagation(); openEdit(l); }}
                          className="text-ink-400 hover:text-primary-600 p-1 rounded hover:bg-cream-100 cursor-pointer"
                          title="Edit Lead"
                        >
                          <Edit size={13} />
                        </button>
                        <button
                          type="button"
                          onClick={(e) => { e.stopPropagation(); handleDelete(l.id); }}
                          className="text-ink-400 hover:text-error-500 p-1 rounded hover:bg-cream-100 cursor-pointer"
                          title="Delete Lead"
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>
                    </div>

                    <div className="flex items-center gap-1.5 mt-2 flex-wrap">
                      <Badge tone={l.type === "College Tie-up" ? "gold" : l.type.includes("Enterprise") ? "primary" : "neutral"} className="text-[10px] px-1.5 py-0.2">
                        {l.type}
                      </Badge>
                      {!!l.dealValue && (
                        <span className="text-[11px] font-semibold text-primary-800 bg-primary-50 px-1.5 py-0.5 rounded">
                          {CURRENCY(l.dealValue)}
                        </span>
                      )}
                    </div>

                    {l.selectedPlanName && (
                      <div className="mt-1.5 flex items-center gap-1 text-[10px] text-gold-700 bg-gold-50 rounded px-1.5 py-0.5 w-fit" title="Plan selected & paid by the customer at signup">
                        <CheckCircle2 size={10} /> Plan: {l.selectedPlanName}
                      </div>
                    )}

                    {hasFollowUp && (
                      <p className="text-[10px] text-amber-700 bg-amber-50 rounded px-1.5 py-0.5 mt-1.5 flex items-center gap-1">
                        <Clock size={10} /> Follow-up: {l.followUpDate}
                      </p>
                    )}
                  </div>

                  {/* Quick Action Bar (Call, WhatsApp, Interaction Logs, Enrollment Link) */}
                  <div className="flex items-center justify-between mt-3 pt-2 border-t border-border/60">
                    <div className="flex items-center gap-1">
                      <button
                        onClick={() => openInteractionModal(l)}
                        className="flex h-6 w-6 items-center justify-center rounded-md bg-primary-50 text-primary-700 hover:bg-primary-100 cursor-pointer"
                        title="Log Call / Interaction"
                      >
                        <Phone size={12} />
                      </button>
                      <button
                        onClick={() => openWhatsAppModal(l)}
                        className="flex h-6 w-6 items-center justify-center rounded-md bg-success-50 text-success-600 hover:bg-success-100 cursor-pointer"
                        title="WhatsApp Template Message"
                      >
                        <MessageCircle size={12} />
                      </button>
                      <button
                        onClick={() => {
                          if (l.email) window.open(`mailto:${l.email}`, "_self");
                          else notify("No email on file.", { type: "warning" });
                        }}
                        className="flex h-6 w-6 items-center justify-center rounded-md bg-info-50 text-info-600 hover:bg-info-100 cursor-pointer"
                        title="Send Email"
                      >
                        <Mail size={12} />
                      </button>
                      <button
                        onClick={() => {
                          setEnrollLead(l);
                          setEnrollModalOpen(true);
                          setLinkCopied(false);
                        }}
                        className="flex h-6 w-6 items-center justify-center rounded-md bg-gold-50 text-gold-700 hover:bg-gold-100 cursor-pointer"
                        title="Generate Personalized Enrollment Link"
                      >
                        <Link2 size={12} />
                      </button>
                    </div>

                    <button
                      onClick={() => openInteractionModal(l)}
                      className="text-[10px] text-ink-500 hover:text-ink-800 flex items-center gap-0.5"
                    >
                      {interactionsCount} logs
                    </button>
                  </div>
                </div>
              );
            }}
          />
        )}
      </Card>

      {/* MSH-FR-CRM-01: Multi-Source Capture Lead Modal with Automated Deduplication */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Capture Incoming Lead (Multi-Source Ingestion)"
        description="Captures landing page inquiries, college tie-ups, and corporate requests with automatic duplicate checking."
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button loading={submitting} onClick={handleCreateSubmit}>Ingest Lead</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleCreateSubmit}>
          {duplicateWarning && (
            <div className="p-3 rounded-lg border border-amber-300 bg-amber-50 text-amber-900 text-xs flex items-start gap-2">
              <ShieldAlert size={16} className="text-amber-600 shrink-0 mt-0.5" />
              <div>
                <p className="font-semibold">Potential Duplicate Lead Detected!</p>
                <p className="mt-0.5">
                  <strong>{duplicateWarning.name}</strong> ({duplicateWarning.phone}) is already in stage <strong>"{duplicateWarning.stage}"</strong>.
                </p>
              </div>
            </div>
          )}

          <Input
            label="Full Name / Organization Contact"
            required
            placeholder="e.g. Rahul Sharma or Dr. Raman (CSE HOD)"
            value={values.name}
            onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))}
            error={errors.name}
          />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              label="Phone Number"
              required
              placeholder="e.g. 9876543210"
              value={values.phone}
              onChange={(e) => handlePhoneChange(e.target.value)}
              error={errors.phone}
            />
            <Input
              label="Email Address"
              type="email"
              placeholder="e.g. contact@example.com"
              value={values.email}
              onChange={(e) => handleEmailChange(e.target.value)}
              error={errors.email}
            />
          </div>
          <div className="grid sm:grid-cols-2 gap-4">
            <Select
              label="Lead Channel / Type"
              required
              options={LEAD_TYPES}
              value={values.type}
              onChange={(e) => setValues((v) => ({ ...v, type: e.target.value }))}
            />
            <Select
              label="Ingestion Source"
              options={LEAD_SOURCES}
              value={values.source}
              onChange={(e) => setValues((v) => ({ ...v, source: e.target.value }))}
            />
          </div>
          <p className="text-[11px] text-ink-400 -mt-1">
            No pricing is set at this stage — the deal value is captured automatically once the lead selects a plan and completes payment.
          </p>
        </form>
      </Modal>

      {/* MSH-FR-CRM-01: Bulk Multi-Source Ingestion Modal */}
      <Modal
        open={bulkOpen}
        onClose={() => setBulkOpen(false)}
        title="Bulk Lead Ingestion"
        description="Upload a file of leads at once — Moriah CRM automatically deduplicates records before saving."
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setBulkOpen(false)}>Close</Button>
            <Button icon={Upload} loading={bulkImporting} onClick={handleBulkImport}>
              {bulkPreview?.rows?.length ? `Ingest ${bulkPreview.rows.length} lead${bulkPreview.rows.length === 1 ? "" : "s"}` : "Process & Ingest"}
            </Button>
          </>
        }
      >
        <div className="flex flex-col gap-4 text-left font-sans">
          <div className="rounded-lg border border-dashed border-primary-300 bg-primary-50/40 p-3">
            <div className="flex items-center justify-between gap-3 mb-2">
              <p className="text-xs font-semibold text-ink-700">Upload a file — .csv, .xlsx, .xls, or .txt</p>
              <Button size="xs" variant="secondary" icon={Download} onClick={downloadLeadImportTemplate}>
                Download template
              </Button>
            </div>
            <p className="text-xs text-ink-500 mb-2">
              Columns: <code>Name, Phone, Email, Type, Source</code> (Email and Source are optional; a header row is fine, it's skipped automatically).
            </p>
            <FileUpload
              hint=".csv, .xlsx, .xls, or .txt"
              accept=".csv,.xlsx,.xls,.txt"
              initialFiles={bulkFile ? [bulkFile] : []}
              onChange={handleBulkFileSelected}
            />
            {bulkParsing && <p className="text-xs text-ink-500 mt-2">Reading file…</p>}
            {bulkPreview && (
              <div className="mt-3 flex flex-col gap-2">
                {bulkPreview.rows.length > 0 && (
                  <p className="text-xs font-medium text-success-600">
                    {bulkPreview.rows.length} lead{bulkPreview.rows.length === 1 ? "" : "s"} ready to ingest.
                  </p>
                )}
                {bulkPreview.problems.length > 0 && (
                  <div className="rounded-lg bg-warning-50 border border-warning-500/30 p-2.5 flex flex-col gap-1">
                    <p className="text-xs font-semibold text-warning-600 flex items-center gap-1.5">
                      <AlertTriangle size={13} /> {bulkPreview.problems.length} row{bulkPreview.problems.length === 1 ? "" : "s"} skipped
                    </p>
                    {bulkPreview.problems.slice(0, 5).map((p, i) => <p key={i} className="text-xs text-warning-600">{p}</p>)}
                  </div>
                )}
              </div>
            )}
          </div>

          <details className="text-xs text-ink-500">
            <summary className="cursor-pointer font-semibold text-ink-700">…or paste raw text instead</summary>
            <div className="mt-2 flex flex-col gap-2">
              <p>Format: <code>Name, Phone, Email, Type</code> — one per line.</p>
              <Textarea
                rows={5}
                placeholder={`Aarav Mehta, 9123456711, aarav@gmail.com, Student (B2C)\nProf. Meenakshi, 9845112233, hod@svce.edu.in, College Tie-up`}
                value={bulkText}
                onChange={(e) => setBulkText(e.target.value)}
              />
            </div>
          </details>

          {bulkResult && (
            <div className="p-3 bg-success-50 border border-success-200 text-success-800 rounded-lg text-xs">
              ✅ Successfully ingested <strong>{bulkResult.added}</strong> new leads. Skipped <strong>{bulkResult.skipped}</strong> duplicates.
            </div>
          )}
        </div>
      </Modal>

      {/* MSH-FR-CRM-03: WhatsApp & Omnichannel Pre-Approved Templates Modal */}
      <Modal
        open={whatsappOpen}
        onClose={() => setWhatsappOpen(false)}
        title="WhatsApp & Omnichannel Comms"
        description="Select from verified pre-approved WhatsApp templates or customize variables before sending."
        footer={
          <>
            <Button variant="secondary" onClick={() => setWhatsappOpen(false)}>Cancel</Button>
            <Button icon={Send} onClick={sendWhatsAppMessage}>Send to WhatsApp</Button>
          </>
        }
      >
        {activeLead && (
          <div className="flex flex-col gap-4 text-left font-sans">
            <div className="p-3 bg-cream-50 rounded-lg border border-border flex items-center justify-between text-xs">
              <div>
                <span className="text-ink-500">Recipient: </span>
                <strong className="text-ink-900">{activeLead.name}</strong> ({activeLead.phone})
              </div>
              <Badge tone="success">WhatsApp Webhook Ready</Badge>
            </div>

            <Select
              label="Pre-Approved WhatsApp Template"
              options={PREAPPROVED_WHATSAPP_TEMPLATES.map((t) => ({ value: t.id, label: `${t.name} (${t.category})` }))}
              value={selectedTemplate}
              onChange={(e) => handleTemplateChange(e.target.value)}
            />

            <Textarea
              label="Message Preview"
              rows={4}
              value={customMsg}
              onChange={(e) => setCustomMsg(e.target.value)}
            />

            <p className="text-[11px] text-ink-400">
              ⚡ Clicking Send will open WhatsApp Web / App directly with the populated message and log this outbound conversation into the lead timeline.
            </p>
          </div>
        )}
      </Modal>

      {/* MSH-FR-CRM-04: Call & Interaction History Timeline Modal */}
      <Modal
        open={interactionOpen}
        onClose={() => setInteractionOpen(false)}
        title={interactionLead ? `Interaction History: ${interactionLead.name}` : "Log Interaction"}
        size="lg"
        footer={
          <div className="flex justify-between w-full items-center">
            <span className="text-xs text-ink-400">All interactions are timestamped and logged.</span>
            <div className="flex gap-2">
              <Button variant="secondary" onClick={() => setInteractionOpen(false)}>Close</Button>
              <Button onClick={saveInteractionLog}>Save Interaction</Button>
            </div>
          </div>
        }
      >
        {interactionLead && (
          <div className="flex flex-col gap-5 text-left font-sans">
            {/* New Interaction Logger */}
            <div className="p-4 bg-cream-50/80 rounded-xl border border-border">
              <h4 className="text-xs font-bold uppercase tracking-wider text-ink-700 mb-3">Log New Activity / Outreach</h4>
              <div className="grid sm:grid-cols-3 gap-3">
                <Select
                  label="Channel"
                  options={["Call", "WhatsApp", "Zoom Demo", "Email", "In-Person Meeting"].map((c) => ({ value: c, label: c }))}
                  value={logChannel}
                  onChange={(e) => setLogChannel(e.target.value)}
                />
                <Select
                  label="Call Outcome"
                  options={["Connected", "Left Voicemail", "Callback Requested", "Interested - Sent Link", "Demo Scheduled", "Not Interested"].map((o) => ({ value: o, label: o }))}
                  value={callOutcome}
                  onChange={(e) => setCallOutcome(e.target.value)}
                />
                <Input
                  label="Next Follow-up Date"
                  type="date"
                  value={followUpDate}
                  onChange={(e) => setFollowUpDate(e.target.value)}
                />
              </div>

              {logChannel === "Zoom Demo" && (
                <div className="mt-3">
                  <Input
                    label="Zoom / Google Meet Link"
                    placeholder="https://meet.google.com/xyz-abcd-efg"
                    value={zoomLink}
                    onChange={(e) => setZoomLink(e.target.value)}
                  />
                </div>
              )}

              <div className="mt-3">
                <Input
                  label="Discussion Notes"
                  placeholder="Key discussion points, objections, candidate goals, next steps..."
                  value={interactionNotes}
                  onChange={(e) => setInteractionNotes(e.target.value)}
                />
              </div>
            </div>

            {/* Interaction History Timeline */}
            <div>
              <h4 className="text-xs font-bold uppercase tracking-wider text-ink-700 mb-2">Past Interaction Timeline</h4>
              <div className="flex flex-col divide-y divide-border border border-border rounded-xl max-h-[220px] overflow-y-auto bg-white">
                {activityLoading ? (
                  <p className="text-xs text-ink-400 p-4 text-center">Loading history…</p>
                ) : activityHistory.length === 0 ? (
                  <p className="text-xs text-ink-400 p-4 text-center">No interactions recorded yet. Log your first call above.</p>
                ) : (
                  activityHistory.map((it, idx) => (
                    <div key={idx} className="p-3 flex items-start gap-3 hover:bg-cream-50/50">
                      <div className="flex h-7 w-7 rounded-full bg-primary-50 text-primary-700 items-center justify-center shrink-0 mt-0.5">
                        {it.channel === "Call" ? <Phone size={12} /> : it.channel === "WhatsApp" ? <MessageCircle size={12} /> : it.channel === "Zoom Demo" ? <Video size={12} /> : <Mail size={12} />}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-semibold text-ink-900">{it.channel} · <span className="text-primary-700">{it.outcome}</span></span>
                          <span className="text-[11px] text-ink-400">{new Date(it.timestamp).toLocaleString()}</span>
                        </div>
                        {it.notes && <p className="text-xs text-ink-600 mt-1">{it.notes}</p>}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>
        )}
      </Modal>

      {/* Edit Lead Modal */}
      <Modal
        open={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Edit Lead Details"
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditModalOpen(false)}>Cancel</Button>
            <Button onClick={handleEditSave}>Save Changes</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleEditSave}>
          <Input label="Name" required value={editValues.name} onChange={(e) => setEditValues((v) => ({ ...v, name: e.target.value }))} error={errors.name} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Phone" value={editValues.phone} disabled hint="Contact details identify the lead — re-capture to change." />
            <Input label="Email" value={editValues.email} disabled />
          </div>
          <div className="grid sm:grid-cols-2 gap-4">
            <Select label="Type" options={LEAD_TYPES} value={editValues.type} onChange={(e) => setEditValues((v) => ({ ...v, type: e.target.value }))} />
            <Input label="Source" value={editValues.source} disabled hint="Set at capture." />
          </div>
          <div className="grid sm:grid-cols-2 gap-4">
            <Select label="Stage" options={PIPELINE_STAGES.map((s) => ({ value: s, label: s }))} value={editValues.stage} onChange={(e) => setEditValues((v) => ({ ...v, stage: e.target.value }))} />
            <Input
              label="Deal Value (₹)"
              type="number"
              placeholder="Not paid yet"
              value={editValues.dealValue}
              onChange={(e) => setEditValues((v) => ({ ...v, dealValue: e.target.value }))}
              hint="Auto-filled once the lead pays online. Only set this manually for offline / bank-transfer payments."
            />
          </div>
        </form>
      </Modal>

      {/* Personalized Enrollment Link Modal */}
      <Modal
        open={enrollModalOpen}
        onClose={() => setEnrollModalOpen(false)}
        title="Send Personalized Registration Link"
        footer={<Button variant="secondary" onClick={() => setEnrollModalOpen(false)}>Close</Button>}
      >
        <div className="flex flex-col gap-4 text-left font-sans">
          <p className="text-sm text-ink-600">
            Share this personalized enrollment link with <strong>{enrollLead?.name}</strong>. Their profile and pre-selected plan will be pre-filled automatically:
          </p>
          <div className="flex items-center gap-2 rounded-lg border border-border bg-cream-50 px-3 py-2">
            <code className="flex-1 text-xs text-ink-700 break-all">
              {enrollLead ? `${window.location.origin}/register?leadId=${enrollLead.id}&name=${encodeURIComponent(enrollLead.name)}&phone=${encodeURIComponent(enrollLead.phone || "")}&email=${encodeURIComponent(enrollLead.email || "")}` : ""}
            </code>
            <button
              type="button"
              onClick={() => {
                const link = `${window.location.origin}/register?leadId=${enrollLead?.id}&name=${encodeURIComponent(enrollLead?.name || "")}&phone=${encodeURIComponent(enrollLead?.phone || "")}&email=${encodeURIComponent(enrollLead?.email || "")}`;
                navigator.clipboard?.writeText(link).then(() => {
                  setLinkCopied(true);
                  setTimeout(() => setLinkCopied(false), 2000);
                });
              }}
              className="shrink-0 flex items-center gap-1 rounded-md bg-primary-800 text-white text-xs px-2.5 py-1.5 hover:bg-primary-700 cursor-pointer"
            >
              {linkCopied ? <Check size={13} /> : <Copy size={13} />}
              {linkCopied ? "Copied" : "Copy"}
            </button>
          </div>
        </div>
      </Modal>
    </div>
  );
}