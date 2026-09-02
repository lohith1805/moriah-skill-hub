import { useEffect, useState } from "react";
import {
  Plus, CalendarClock, Video, Edit, Trash2, Users, FileText,
  Clock, CheckCircle2, MessageSquare, Link, Calendar
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getMeetings, saveMeetings } from "../../services/baService";

export default function BaMeetings() {
  const [meetings, setMeetings] = useState([]);
  const [loading, setLoading] = useState(true);

  // Schedule modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [values, setValues] = useState({
    title: "",
    type: "Sprint Demo",
    client: "",
    date: "",
    time: "03:00 PM",
    meetLink: "",
    agenda: "",
    attendees: "Client Lead, Trainer, BA, Developer Lead"
  });

  // MOM Notes modal state
  const [momOpen, setMomOpen] = useState(false);
  const [activeMeeting, setActiveMeeting] = useState(null);
  const [momText, setMomText] = useState("");

  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  const load = () => {
    setLoading(true);
    getMeetings().then((m) => {
      setMeetings(m);
      setLoading(false);
    });
  };

  useEffect(() => {
    load();
  }, []);

  const persist = (data) => {
    setMeetings(data);
    saveMeetings(data);
  };

  const handleSchedule = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { title: [required], date: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    setSubmitting(true);
    try {
      const generatedLink = values.meetLink || `https://meet.google.com/msh-${Math.random().toString(36).substr(2, 4)}-${Math.random().toString(36).substr(2, 3)}`;
      const newMeeting = {
        id: `m_${Date.now()}`,
        title: values.title,
        type: values.type,
        client: values.client || "Enterprise Client",
        date: values.date,
        time: values.time || "03:00 PM",
        meetLink: generatedLink,
        agenda: values.agenda || "Review sprint milestones and collect client sign-off.",
        attendees: values.attendees.split(",").map((s) => s.trim()),
        momNotes: ""
      };

      const updated = [newMeeting, ...meetings];
      persist(updated);
      notify("Client ceremony scheduled and calendar invites dispatched.", { type: "success", title: "Meeting Scheduled" });
      setModalOpen(false);
      setValues({ title: "", type: "Sprint Demo", client: "", date: "", time: "03:00 PM", meetLink: "", agenda: "", attendees: "Client Lead, Trainer, BA, Developer Lead" });
    } finally {
      setSubmitting(false);
    }
  };

  const openMomModal = (meeting) => {
    setActiveMeeting(meeting);
    setMomText(meeting.momNotes || "");
    setMomOpen(true);
  };

  const saveMomNotes = (e) => {
    e.preventDefault();
    const updated = meetings.map((m) => (m.id === activeMeeting.id ? { ...m, momNotes: momText } : m));
    persist(updated);
    notify("Minutes of Meeting (MOM) recorded.", { type: "success" });
    setMomOpen(false);
  };

  const handleDelete = (id) => {
    const target = meetings.find((m) => m.id === id);
    const updated = meetings.filter((m) => m.id !== id);
    persist(updated);
    notify(`Meeting "${target?.title}" removed.`, { type: "success" });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Client Meeting Coordination"
        subtitle="Direct scheduling for sprint demos, backlog refinement, and sign-off ceremonies with minutes of meeting (MOM)"
        breadcrumbs={[{ label: "Dashboard", to: "/ba/dashboard" }, { label: "Meetings" }]}
        action={
          <Button icon={Plus} onClick={() => { setErrors({}); setModalOpen(true); }}>
            Schedule Client Ceremony
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
                      <CalendarClock size={13} /> {m.date} at {m.time} · Client: <strong className="text-ink-800">{m.client}</strong>
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <Badge tone={m.type === "Sprint Demo" ? "gold" : m.type === "Sign-off Meeting" ? "success" : "primary"}>
                    {m.type}
                  </Badge>
                  <Button size="sm" icon={Video} onClick={() => window.open(m.meetLink, "_blank")}>
                    Join Video Call
                  </Button>
                  <Button size="sm" variant="secondary" icon={FileText} onClick={() => openMomModal(m)}>
                    {m.momNotes ? "View / Edit MOM" : "Log MOM"}
                  </Button>
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
                  <span className="font-semibold text-ink-700">Invited Attendees:</span>
                  <div className="flex flex-wrap gap-1 mt-1">
                    {(m.attendees || []).map((att, i) => (
                      <span key={i} className="bg-white border border-border px-2 py-0.5 rounded text-[11px] text-ink-700">
                        {att}
                      </span>
                    ))}
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

      {/* Schedule Ceremony Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Schedule Client Ceremony (MSH-FR-BA-04)"
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button loading={submitting} icon={Calendar} onClick={handleSchedule}>Schedule & Send Invites</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleSchedule}>
          <Input
            label="Meeting Title"
            required
            placeholder="e.g. Meeting title"
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
            <Input
              label="Client Partner"
              placeholder="e.g. Client company name"
              value={values.client}
              onChange={(e) => setValues((v) => ({ ...v, client: e.target.value }))}
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
              value={values.time}
              onChange={(e) => setValues((v) => ({ ...v, time: e.target.value }))}
            />
          </div>

          <Textarea
            label="Agenda & Focus Items"
            rows={2}
            placeholder="Key milestones to demonstrate..."
            value={values.agenda}
            onChange={(e) => setValues((v) => ({ ...v, agenda: e.target.value }))}
          />
          <Input
            label="Invited Stakeholders (Comma-separated)"
            value={values.attendees}
            onChange={(e) => setValues((v) => ({ ...v, attendees: e.target.value }))}
          />
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
