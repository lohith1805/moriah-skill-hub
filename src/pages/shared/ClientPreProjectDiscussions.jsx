import { useEffect, useState } from "react";
import { Video, CalendarClock, Users, FileText, CheckCircle2, StickyNote } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Textarea } from "../../components/ui/FormField";
import EmptyState from "../../components/ui/EmptyState";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { useToast } from "../../context/ToastContext";
import { useAuth } from "../../context/AuthContext";
import { ROLES } from "../../utils/constants";
import { getMyMeetings, addMeetingNote } from "../../services/meetingService";

const DASHBOARD_PATH_BY_ROLE = {
  [ROLES.DEVELOPER]: "/developer/dashboard",
  [ROLES.ADMIN]: "/admin/dashboard",
  [ROLES.BUSINESS_ANALYST]: "/ba/dashboard",
  [ROLES.CLIENT]: "/client/dashboard",
};

const STATUS_TONE = { SCHEDULED: "primary", COMPLETED: "success", CANCELLED: "error" };

/**
 * Read-only invitee view of "Client Pre-Project Discussions" — a BA schedules these under
 * /ba/meetings and checks off who to invite from a role -> employee picker; this is where each
 * checked person (Developer, Admin, or a BA who isn't the scheduler) sees what they've been
 * invited to, without needing BUSINESS_ANALYST access themselves.
 */
export default function ClientPreProjectDiscussions() {
  const { notify } = useToast();
  const { user } = useAuth();
  const [meetings, setMeetings] = useState([]);
  const [loading, setLoading] = useState(true);

  // Note modal — any invitee can jot one down once the meeting is COMPLETED (backend enforces
  // this; the button below just doesn't offer it before then).
  const [noteMeeting, setNoteMeeting] = useState(null);
  const [noteText, setNoteText] = useState("");
  const [savingNote, setSavingNote] = useState(false);

  const load = () => {
    setLoading(true);
    getMyMeetings()
      .then(setMeetings)
      .catch((e) => notify(e?.message || "Couldn't load your meeting invites.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(load, [notify]);

  const openNote = (m) => {
    setNoteMeeting(m);
    setNoteText(m.momNotes || "");
  };

  const saveNote = async (e) => {
    e.preventDefault();
    if (!noteText.trim()) return;
    setSavingNote(true);
    try {
      await addMeetingNote(noteMeeting.id, noteText.trim());
      notify("Note saved.", { type: "success" });
      setNoteMeeting(null);
      load();
    } catch (err) {
      notify(err?.message || "Couldn't save this note.", { type: "error" });
    } finally {
      setSavingNote(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Client Pre-Project Discussions"
        subtitle="Meetings a Business Analyst has invited you to — kickoffs, requirement walkthroughs, and sign-off ceremonies"
        breadcrumbs={[
          { label: "Dashboard", to: DASHBOARD_PATH_BY_ROLE[user?.role] || "/" },
          { label: "Client Pre-Project Discussions" },
        ]}
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading your invites…" /></div>
      ) : meetings.length === 0 ? (
        <Card>
          <EmptyState
            icon={CalendarClock}
            title="No meetings yet"
            description="Once a Business Analyst invites you to a Client Pre-Project Discussion, it will show up here — and you'll get an email too."
          />
        </Card>
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
                      <CalendarClock size={13} /> {m.date} at {m.time}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={STATUS_TONE[m.status] || "neutral"}>{m.status}</Badge>
                  {m.meetLink && (
                    <Button size="sm" icon={Video} onClick={() => window.open(m.meetLink, "_blank")}>Join</Button>
                  )}
                </div>
              </div>

              <div className="grid sm:grid-cols-2 gap-3 text-xs bg-cream-50/50 p-3 rounded-lg border border-border">
                <div>
                  <span className="font-semibold text-ink-700">Agenda:</span>
                  <p className="text-ink-600 mt-0.5">{m.agenda || "No agenda provided."}</p>
                </div>
                <div>
                  <span className="font-semibold text-ink-700 flex items-center gap-1"><Users size={12} /> Also invited:</span>
                  <div className="flex flex-wrap gap-1 mt-1">
                    {m.attendees.length === 0 ? (
                      <span className="text-ink-400">Just you</span>
                    ) : (
                      m.attendees.map((a) => (
                        <span key={a.uuid} className="bg-white border border-border px-2 py-0.5 rounded text-[11px] text-ink-700">
                          {a.fullName}
                        </span>
                      ))
                    )}
                  </div>
                </div>
              </div>

              {m.momNotes ? (
                <div className="p-3 bg-white rounded-lg border border-success-200 text-xs">
                  <div className="flex items-center justify-between">
                    <p className="font-bold text-success-900 flex items-center gap-1">
                      <CheckCircle2 size={13} className="text-success-600" /> Note:
                    </p>
                    {m.status === "COMPLETED" && (
                      <Button size="sm" variant="ghost" icon={StickyNote} onClick={() => openNote(m)}>Edit</Button>
                    )}
                  </div>
                  <p className="text-ink-700 mt-1 italic leading-relaxed">{m.momNotes}</p>
                </div>
              ) : m.status === "COMPLETED" ? (
                <Button size="sm" variant="secondary" icon={StickyNote} className="self-start" onClick={() => openNote(m)}>
                  Add a note about this meeting
                </Button>
              ) : null}
            </Card>
          ))}
        </div>
      )}

      <Modal
        open={!!noteMeeting}
        onClose={() => setNoteMeeting(null)}
        title={noteMeeting ? `Note — ${noteMeeting.title}` : "Note"}
        footer={
          <>
            <Button variant="secondary" onClick={() => setNoteMeeting(null)}>Cancel</Button>
            <Button icon={StickyNote} loading={savingNote} onClick={saveNote}>Save Note</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={saveNote}>
          <p className="text-xs text-ink-500">
            A short summary of what this discussion covered — visible to everyone invited.
          </p>
          <Textarea
            label="Note"
            rows={5}
            placeholder="e.g. Client confirmed the checkout scope; BA to finalize FRS by Friday."
            value={noteText}
            onChange={(e) => setNoteText(e.target.value)}
          />
        </form>
      </Modal>
    </div>
  );
}
