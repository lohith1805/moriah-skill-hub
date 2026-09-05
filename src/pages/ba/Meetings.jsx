import { useEffect, useState } from "react";
import {
  Plus, CalendarClock, Video, Trash2, Users, FileText,
  CheckCircle2, Calendar, X, Pencil
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getMeetings, createMeeting, updateMeeting, saveMeetingMinutes, deleteMeeting, getMeetingStaffDirectory } from "../../services/baService";
import { getClientProjects } from "../../services/clientService";

const INVITE_ROLES = [
  { value: "BUSINESS_ANALYST", label: "Business Analyst" },
  { value: "DEVELOPER", label: "Developer" },
  { value: "ADMIN", label: "Admin" },
  { value: "CLIENT", label: "Client" },
];

const STATUS_TONE = { SCHEDULED: "primary", COMPLETED: "success", CANCELLED: "error" };

const emptyValues = () => ({
  title: "",
  type: "Sprint Demo",
  clientProjectId: "",
  date: "",
  time: "15:00",
  meetLink: "",
  agenda: "",
});

export default function BaMeetings() {
  const [meetings, setMeetings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [projects, setProjects] = useState([]);

  // Schedule/edit modal state — editingId null means "creating a new discussion";
  // otherwise the form is pre-filled from that meeting and submits via updateMeeting.
  const [modalOpen, setModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editingMeeting, setEditingMeeting] = useState(null); // kept for status/momNotes passthrough
  const [submitting, setSubmitting] = useState(false);
  const [values, setValues] = useState(emptyValues());

  // Role -> employee attendee picker. selected: [{ uuid, fullName, role }] — accumulated across
  // role switches so picking Developers then Business Analysts keeps both selections.
  const [pickerRole, setPickerRole] = useState("DEVELOPER");
  const [roster, setRoster] = useState({}); // { [role]: [{uuid, fullName, email}] } — fetched lazily, cached
  const [rosterLoading, setRosterLoading] = useState(false);
  const [selectedAttendees, setSelectedAttendees] = useState([]);

  // MOM Notes modal state
  const [momOpen, setMomOpen] = useState(false);
  const [activeMeeting, setActiveMeeting] = useState(null);
  const [momText, setMomText] = useState("");

  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  const load = () => {
    setLoading(true);
    getMeetings()
      .then((m) => setMeetings(m.filter((x) => x.status !== "CANCELLED")))
      .catch((e) => notify(e.message || "Could not load meetings.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    getClientProjects().then(setProjects).catch(() => setProjects([]));
  }, []);

  useEffect(() => {
    if (!modalOpen || roster[pickerRole]) return;
    setRosterLoading(true);
    getMeetingStaffDirectory(pickerRole)
      .then((list) => setRoster((r) => ({ ...r, [pickerRole]: list })))
      .catch(() => notify(`Couldn't load the ${pickerRole} roster.`, { type: "error" }))
      .finally(() => setRosterLoading(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modalOpen, pickerRole]);

  const openCreate = () => {
    setErrors({});
    setEditingId(null);
    setEditingMeeting(null);
    setValues(emptyValues());
    setSelectedAttendees([]);
    setPickerRole("DEVELOPER");
    setModalOpen(true);
  };

  const openEdit = (meeting) => {
    setErrors({});
    setEditingId(meeting.id);
    setEditingMeeting(meeting);
    setValues({
      title: meeting.title,
      type: meeting.type || "Sprint Demo",
      clientProjectId: meeting.clientProjectId != null ? String(meeting.clientProjectId) : "",
      date: meeting.date,
      time: meeting.time,
      meetLink: meeting.meetLink || "",
      agenda: meeting.agenda || "",
    });
    // Pre-fill from the meeting's real attendee list — role is unknown until the BA re-opens that
    // role's tab in the picker (cosmetic only: it's just the chip's "· Role" label).
    setSelectedAttendees((meeting.attendeeList || []).map((a) => ({ uuid: a.uuid, fullName: a.fullName, role: null })));
    setPickerRole("DEVELOPER");
    setModalOpen(true);
  };

  const toggleAttendee = (person, role) => {
    setSelectedAttendees((current) => {
      const already = current.some((a) => a.uuid === person.uuid);
      if (already) return current.filter((a) => a.uuid !== person.uuid);
      return [...current, { uuid: person.uuid, fullName: person.fullName, role }];
    });
  };

  const removeAttendee = (uuid) => setSelectedAttendees((current) => current.filter((a) => a.uuid !== uuid));

  const handleSubmit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { title: [required], date: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    setSubmitting(true);
    try {
      const generatedLink = values.meetLink || `https://meet.google.com/msh-${Math.random().toString(36).substr(2, 4)}-${Math.random().toString(36).substr(2, 3)}`;
      const project = projects.find((p) => String(p.id) === String(values.clientProjectId));
      const payload = {
        title: values.title,
        type: values.type,
        client: project?.clientName || "",
        clientProjectId: values.clientProjectId || null,
        date: values.date,
        time: values.time || "15:00",
        meetLink: generatedLink,
        agenda: values.agenda || "Review sprint milestones and collect client sign-off.",
        attendeeUuids: selectedAttendees.map((a) => a.uuid),
      };

      if (editingId) {
        await updateMeeting(editingId, {
          ...payload,
          status: editingMeeting?.status || "SCHEDULED",
          momNotes: editingMeeting?.momNotes || "",
        });
        notify("Client Pre-Project Discussion updated — newly invited attendees have been emailed.", { type: "success", title: "Meeting Updated" });
      } else {
        await createMeeting(payload);
        notify(
          selectedAttendees.length
            ? `Client Pre-Project Discussion scheduled — ${selectedAttendees.length} ${selectedAttendees.length === 1 ? "person" : "people"} invited by email.`
            : "Client Pre-Project Discussion scheduled.",
          { type: "success", title: "Meeting Scheduled" }
        );
      }
      setModalOpen(false);
      load();
    } catch (err) {
      notify(err.message || "Could not save the meeting.", { type: "error" });
    } finally {
      setSubmitting(false);
    }
  };

  const openMomModal = (meeting) => {
    setActiveMeeting(meeting);
    setMomText(meeting.momNotes || "");
    setMomOpen(true);
  };

  const saveMomNotes = async (e) => {
    e.preventDefault();
    try {
      await saveMeetingMinutes(activeMeeting.id, momText, activeMeeting);
      notify("Minutes of Meeting (MOM) recorded.", { type: "success" });
      setMomOpen(false);
      load();
    } catch (err) {
      notify(err.message || "Could not save the minutes.", { type: "error" });
    }
  };

  const handleDelete = async (id) => {
    const target = meetings.find((m) => m.id === id);
    try {
      await deleteMeeting(id);
      notify(`Meeting "${target?.title}" cancelled — invited attendees have been emailed.`, { type: "success" });
      load();
    } catch (err) {
      notify(err.message || "Could not cancel the meeting.", { type: "error" });
    }
  };

  const currentRoster = roster[pickerRole] || [];

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Client Pre-Project Discussions"
        subtitle="Schedule kickoffs, requirement walkthroughs, and sign-off ceremonies — invite people by role, and they're emailed automatically"
        breadcrumbs={[{ label: "Dashboard", to: "/ba/dashboard" }, { label: "Meetings" }]}
        action={
          <Button icon={Plus} onClick={openCreate}>
            Schedule Discussion
          </Button>
        }
      />

      {loading ? (
        <div className="flex justify-center py-20"><LoadingSpinner label="Loading ceremonies…" /></div>
      ) : (
        <div className="flex flex-col gap-4">
          {meetings.map((m) => (
            <Card key={m.id} className="text-left flex flex-col gap-3">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-border pb-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary-50 text-primary-700">
                    <Video size={20} />
                  </div>
                  <div>
                    <h3 className="font-semibold text-ink-900 text-sm">{m.title}</h3>
                    <p className="text-xs text-ink-500 flex items-center gap-1.5 mt-0.5">
                      <CalendarClock size={13} /> {m.date} at {m.timeDisplay || m.time}
                      {m.client && <> · Client: <strong className="text-ink-800">{m.client}</strong></>}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Badge tone={STATUS_TONE[m.status] || "neutral"}>{m.status}</Badge>
                  <Badge tone={m.type === "Sprint Demo" ? "gold" : m.type === "Sign-off Meeting" ? "success" : "primary"}>
                    {m.type}
                  </Badge>
                  {m.status !== "CANCELLED" && (
                    <Button size="sm" icon={Video} onClick={() => window.open(m.meetLink, "_blank")}>
                      Join Video Call
                    </Button>
                  )}
                  <Button size="sm" variant="secondary" icon={FileText} onClick={() => openMomModal(m)}>
                    {m.momNotes ? "View / Edit MOM" : "Log MOM"}
                  </Button>
                  <Button size="sm" variant="secondary" icon={Pencil} onClick={() => openEdit(m)}>Edit</Button>
                  <Button size="sm" variant="ghost" icon={Trash2} onClick={() => handleDelete(m.id)} className="text-error-600 hover:bg-error-50" />
                </div>
              </div>

              {/* Agenda & Attendees */}
              <div className="grid sm:grid-cols-2 gap-3 text-xs bg-cream-50/50 p-3 rounded-lg border border-border">
                <div>
                  <span className="font-semibold text-ink-700">Agenda & Objectives:</span>
                  <p className="text-ink-600 mt-0.5">{m.agenda || "Sprint review and demonstration of student pull requests."}</p>
                </div>
                <div>
                  <span className="font-semibold text-ink-700 flex items-center gap-1"><Users size={12} /> Invited Attendees:</span>
                  <div className="flex flex-wrap gap-1 mt-1">
                    {(m.attendees || []).length === 0 ? (
                      <span className="text-ink-400">Nobody invited yet</span>
                    ) : (
                      (m.attendees || []).map((att, i) => (
                        <span key={i} className="bg-white border border-border px-2 py-0.5 rounded text-[11px] text-ink-700">
                          {att}
                        </span>
                      ))
                    )}
                  </div>
                </div>
              </div>

              {/* Minutes of Meeting (MOM) note highlight */}
              {m.momNotes && (
                <div className="p-3 bg-white rounded-lg border border-success-200 text-xs">
                  <p className="font-bold text-success-900 flex items-center gap-1">
                    <CheckCircle2 size={13} className="text-success-600" /> Recorded Minutes of Meeting (MOM):
                  </p>
                  <p className="text-ink-700 mt-1 italic leading-relaxed">{m.momNotes}</p>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}

      {/* Schedule/Edit Discussion Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editingId ? "Edit Client Pre-Project Discussion" : "Schedule a Client Pre-Project Discussion"}
        size="lg"
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button loading={submitting} icon={Calendar} onClick={handleSubmit}>
              {editingId ? "Save Changes" : "Schedule & Send Invites"}
            </Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleSubmit}>
          <Input
            label="Meeting Title"
            required
            placeholder="e.g. Storefront Revamp — Kickoff"
            value={values.title}
            onChange={(e) => setValues((v) => ({ ...v, title: e.target.value }))}
            error={errors.title}
          />
          <div className="grid sm:grid-cols-2 gap-4">
            <Select
              label="Ceremony Type"
              options={[
                { value: "Sprint Demo", label: "Sprint Demo Review" },
                { value: "Backlog Refinement", label: "Backlog Refinement" },
                { value: "Sign-off Meeting", label: "Architecture / Scope Sign-off" },
                { value: "Client Kickoff", label: "Client Project Kickoff" }
              ]}
              value={values.type}
              onChange={(e) => setValues((v) => ({ ...v, type: e.target.value }))}
            />
            <Select
              label="Client Project"
              placeholder="Not tied to a project"
              options={projects.map((p) => ({ value: String(p.id), label: `${p.title} — ${p.clientName}` }))}
              value={values.clientProjectId}
              onChange={(e) => setValues((v) => ({ ...v, clientProjectId: e.target.value }))}
            />
          </div>

          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              label="Date"
              type="date"
              required
              value={values.date}
              onChange={(e) => setValues((v) => ({ ...v, date: e.target.value }))}
              error={errors.date}
            />
            <Input
              label="Time"
              type="time"
              required
              value={values.time}
              onChange={(e) => setValues((v) => ({ ...v, time: e.target.value }))}
            />
          </div>

          <Input
            label="Meeting Link"
            placeholder="e.g. Google Meet / Zoom link — auto-generated if left blank"
            value={values.meetLink}
            onChange={(e) => setValues((v) => ({ ...v, meetLink: e.target.value }))}
          />

          <Textarea
            label="Agenda & Focus Items"
            rows={2}
            placeholder="Key milestones to demonstrate..."
            value={values.agenda}
            onChange={(e) => setValues((v) => ({ ...v, agenda: e.target.value }))}
          />

          {/* Role -> employee attendee picker */}
          <div className="rounded-lg border border-dashed border-primary-300 bg-primary-50/40 p-3 flex flex-col gap-3">
            <p className="text-xs font-semibold text-ink-700 flex items-center gap-1.5">
              <Users size={14} className="text-primary-600" /> Invite attendees — checked people are emailed and see this on their own dashboard
            </p>
            {editingId && (
              <p className="text-xs text-ink-500 -mt-1">
                Already-invited people are pre-checked below (switch roles to find and uncheck someone).
                Only newly-added people are re-emailed when you save.
              </p>
            )}
            <Select
              label="Role"
              options={INVITE_ROLES}
              value={pickerRole}
              onChange={(e) => setPickerRole(e.target.value)}
            />
            <div className="flex flex-col divide-y divide-border border border-border rounded-lg max-h-[160px] overflow-y-auto bg-white">
              {rosterLoading ? (
                <p className="text-xs text-ink-400 py-3 text-center">Loading roster…</p>
              ) : currentRoster.length === 0 ? (
                <p className="text-xs text-ink-400 py-3 text-center">No active {INVITE_ROLES.find((r) => r.value === pickerRole)?.label} accounts.</p>
              ) : (
                currentRoster.map((person) => (
                  <label key={person.uuid} className="flex items-center gap-3 px-3 py-2 hover:bg-cream-50/60 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={selectedAttendees.some((a) => a.uuid === person.uuid)}
                      onChange={() => toggleAttendee(person, pickerRole)}
                      className="accent-primary-700"
                    />
                    <span className="text-xs text-ink-800 flex-1">{person.fullName}</span>
                    <span className="text-[11px] text-ink-400">{person.email}</span>
                  </label>
                ))
              )}
            </div>

            {selectedAttendees.length > 0 && (
              <div>
                <p className="text-xs font-semibold text-ink-700 mb-1.5">Invited ({selectedAttendees.length})</p>
                <div className="flex flex-wrap gap-1.5">
                  {selectedAttendees.map((a) => (
                    <span key={a.uuid} className="inline-flex items-center gap-1 bg-white border border-border px-2 py-1 rounded-full text-[11px] text-ink-700">
                      {a.fullName}
                      {a.role && <span className="text-ink-400">· {INVITE_ROLES.find((r) => r.value === a.role)?.label}</span>}
                      <button type="button" onClick={() => removeAttendee(a.uuid)} className="text-ink-400 hover:text-error-600">
                        <X size={12} />
                      </button>
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        </form>
      </Modal>

      {/* Minutes of Meeting (MOM) Logger Modal */}
      <Modal
        open={momOpen}
        onClose={() => setMomOpen(false)}
        title={activeMeeting ? `Log MOM: ${activeMeeting.title}` : "Minutes of Meeting"}
        footer={
          <>
            <Button variant="secondary" onClick={() => setMomOpen(false)}>Cancel</Button>
            <Button onClick={saveMomNotes}>Save Minutes of Meeting</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={saveMomNotes}>
          <p className="text-xs text-ink-500">
            Document key discussions, client feedback, action items, and decisions made during the ceremony.
          </p>
          <Textarea
            label="Minutes & Key Action Items"
            rows={5}
            placeholder="e.g. Milestone 2 approved. Client requested pagination addition to the table by next Tuesday..."
            value={momText}
            onChange={(e) => setMomText(e.target.value)}
          />
        </form>
      </Modal>
    </div>
  );
}
