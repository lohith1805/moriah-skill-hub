import { useEffect, useState } from "react";
import { Check, X, Clock, CalendarPlus, Video, Ban } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import { Select, Input } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { useToast } from "../../context/ToastContext";
import {
  getBatches,
  getStudentsForBatch,
  getStandups,
  scheduleStandup,
  cancelStandup,
  overrideAttendance,
  getStandupAttendance,
} from "../../services/trainerService";

const todayIso = () => new Date().toISOString().slice(0, 10);
const TONE = { Present: "success", Late: "warning", Absent: "error", Excused: "neutral" };

// value for <input type="datetime-local"> in local time, rounded to the minute
const nowLocalInput = () => {
  const d = new Date();
  d.setSeconds(0, 0);
  const tz = d.getTimezoneOffset() * 60000;
  return new Date(d - tz).toISOString().slice(0, 16);
};

export default function TrainerStandups() {
  const { notify } = useToast();
  const [batches, setBatches] = useState([]);
  const [batchId, setBatchId] = useState("");
  const [standup, setStandup] = useState(null);
  const [roster, setRoster] = useState([]); // { userUuid, name, status, marked }
  const [loading, setLoading] = useState(false);

  // Schedule modal
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [scheduling, setScheduling] = useState(false);
  const [form, setForm] = useState({ scheduledAt: nowLocalInput(), meetingLink: "", lateCutoffMinutes: 15, notes: "" });
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    getBatches({ scope: "mine" })
      .then((b) => {
        setBatches(b);
        if (b.length) setBatchId(String(b[0].id));
      })
      .catch((e) => notify(e.message || "Could not load batches.", { type: "error" }));
  }, [notify]);

  const load = (id) => {
    if (!id) return;
    setLoading(true);
    Promise.all([getStandups(id, todayIso()), getStudentsForBatch(id)])
      .then(async ([standups, students]) => {
        const active = standups.find((s) => s.status !== "Cancelled") || null;
        setStandup(active);

        const activeStudents = students.filter((s) => s.status === "ACTIVE" || s.status === "ON_PIP");
        let marks = [];
        if (active) {
          marks = await getStandupAttendance(id, active.id).catch(() => []);
        }
        const byUuid = Object.fromEntries(marks.map((m) => [m.userUuid, m]));
        setRoster(
          activeStudents.map((s) => {
            const m = byUuid[s.userUuid];
            return {
              userUuid: s.userUuid,
              name: s.name,
              status: m?.status || "Absent",
              // a row already exists on the server (self check-in or a prior mark)
              marked: !!m,
              selfCheckedIn: !!m && !m.markedByPm && !m.autoMarked,
            };
          })
        );
      })
      .catch((e) => notify(e.message || "Could not load the standup.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load(batchId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [batchId]);

  const openSchedule = () => {
    setForm({ scheduledAt: nowLocalInput(), meetingLink: "", lateCutoffMinutes: 15, notes: "" });
    setScheduleOpen(true);
  };

  const submitSchedule = async () => {
    if (!batchId) return;
    if (!form.meetingLink.trim()) {
      notify("Add the meeting link students will use to join.", { type: "error" });
      return;
    }
    setScheduling(true);
    try {
      await scheduleStandup({
        batchId,
        scheduledAt: new Date(form.scheduledAt).toISOString(),
        meetingLink: form.meetingLink.trim(),
        lateCutoffMinutes: Number(form.lateCutoffMinutes) || 15,
        notes: form.notes.trim() || null,
      });
      notify("Standup scheduled — students can now join from their dashboard.", { type: "success" });
      setScheduleOpen(false);
      load(batchId);
    } catch (err) {
      notify(err.message || "Could not schedule the standup.", { type: "error" });
    } finally {
      setScheduling(false);
    }
  };

  const doCancel = async () => {
    if (!standup) return;
    setCancelling(true);
    try {
      await cancelStandup(standup.id);
      notify("Standup cancelled.", { type: "success" });
      load(batchId);
    } catch (err) {
      notify(err.message || "Could not cancel the standup.", { type: "error" });
    } finally {
      setCancelling(false);
    }
  };

  const mark = async (userUuid, feStatus) => {
    if (!standup) return;
    setRoster((prev) => prev.map((r) => (r.userUuid === userUuid ? { ...r, status: feStatus } : r)));
    try {
      await overrideAttendance(standup.id, userUuid, feStatus);
      setRoster((prev) => prev.map((r) => (r.userUuid === userUuid ? { ...r, marked: true } : r)));
      notify(`Marked ${feStatus}.`, { type: "success" });
    } catch (err) {
      notify(err.message || "Could not record attendance.", { type: "error" });
    }
  };

  const batchName = batches.find((b) => String(b.id) === String(batchId))?.name || "";
  const standupTime = standup
    ? new Date(standup.scheduledAt).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })
    : "";

  return (
    <div>
      <PageHeader
        title="Daily Standups & Attendance"
        subtitle="Schedule the standup meeting, then mark attendance once everyone has joined"
        breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Standups" }]}
        action={
          <Select
            className="w-56"
            value={batchId}
            onChange={(e) => setBatchId(e.target.value)}
            options={batches.map((b) => ({ value: String(b.id), label: b.name }))}
            placeholder={batches.length ? "Select a batch" : "No batches"}
          />
        }
      />

      <Card>
        <CardHeader
          title={`Today's Standup — ${batchName || "—"}`}
          subtitle={new Date().toLocaleDateString("en-IN", { weekday: "long", day: "2-digit", month: "long" })}
          action={
            standup && (
              <div className="flex items-center gap-2">
                {standup.meetingLink && (
                  <a href={standup.meetingLink} target="_blank" rel="noreferrer">
                    <Button size="sm" icon={Video}>Join meet</Button>
                  </a>
                )}
                <Button size="sm" variant="secondary" icon={Ban} loading={cancelling} onClick={doCancel}>Cancel</Button>
              </div>
            )
          }
        />

        {loading ? (
          <div className="flex justify-center py-16"><LoadingSpinner label="Loading…" /></div>
        ) : !standup ? (
          <div className="py-12 flex flex-col items-center text-center gap-3">
            <p className="text-sm text-ink-500">No standup scheduled for today in this batch.</p>
            <Button icon={CalendarPlus} onClick={openSchedule} disabled={!batchId}>
              Schedule today's standup
            </Button>
          </div>
        ) : (
          <>
            <div className="mb-4 flex flex-wrap items-center gap-x-6 gap-y-1 text-sm text-ink-600">
              <span><span className="text-ink-400">Scheduled for</span> <strong className="text-ink-900">{standupTime}</strong></span>
              <span><span className="text-ink-400">Late after</span> <strong className="text-ink-900">{standup.lateCutoffMinutes} min</strong></span>
              {standup.meetingLink ? (
                <a href={standup.meetingLink} target="_blank" rel="noreferrer" className="text-primary-700 hover:underline truncate max-w-xs">{standup.meetingLink}</a>
              ) : (
                <span className="text-ink-400">No meeting link</span>
              )}
            </div>
            {standup.notes && <p className="mb-4 text-xs text-ink-500 bg-cream-50 border border-border/50 rounded-lg p-3">{standup.notes}</p>}
            <Table
              data={roster}
              emptyTitle="No active students in this batch"
              columns={[
                { key: "name", header: "Student", className: "text-left font-medium text-ink-900" },
                {
                  key: "status",
                  header: "Status",
                  className: "text-left",
                  render: (r) => (
                    <span className="flex items-center gap-2">
                      <Badge tone={TONE[r.status] || "neutral"}>{r.status}</Badge>
                      {r.selfCheckedIn && <span className="text-[11px] text-success-600">self check-in</span>}
                      {r.marked && !r.selfCheckedIn && <Check size={13} className="text-success-600" />}
                    </span>
                  ),
                },
                {
                  key: "mark",
                  header: "Mark Attendance",
                  className: "text-right",
                  render: (r) => (
                    <div className="flex items-center gap-1.5 justify-end">
                      <Button size="sm" variant={r.status === "Present" ? "primary" : "secondary"} icon={Check} onClick={() => mark(r.userUuid, "Present")} />
                      <Button size="sm" variant={r.status === "Late" ? "primary" : "secondary"} icon={Clock} onClick={() => mark(r.userUuid, "Late")} />
                      <Button size="sm" variant={r.status === "Absent" ? "danger" : "secondary"} icon={X} onClick={() => mark(r.userUuid, "Absent")} />
                    </div>
                  ),
                },
              ]}
            />
          </>
        )}
      </Card>

      <Modal
        open={scheduleOpen}
        onClose={() => setScheduleOpen(false)}
        title={`Schedule standup — ${batchName}`}
        footer={
          <>
            <Button variant="secondary" onClick={() => setScheduleOpen(false)}>Cancel</Button>
            <Button icon={CalendarPlus} loading={scheduling} onClick={submitSchedule}>Schedule</Button>
          </>
        }
      >
        <div className="flex flex-col gap-4 text-left font-sans">
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-900">Date &amp; time</label>
            <input
              type="datetime-local"
              value={form.scheduledAt}
              onChange={(e) => setForm((f) => ({ ...f, scheduledAt: e.target.value }))}
              className="w-full rounded-lg border border-border px-3 py-2 text-sm outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 bg-white"
            />
          </div>
          <Input
            label="Meeting link"
            required
            type="url"
            placeholder="https://meet.google.com/abc-defg-hij"
            value={form.meetingLink}
            onChange={(e) => setForm((f) => ({ ...f, meetingLink: e.target.value }))}
          />
          <Input
            label="Mark late after (minutes)"
            type="number"
            min="1"
            max="180"
            value={form.lateCutoffMinutes}
            onChange={(e) => setForm((f) => ({ ...f, lateCutoffMinutes: e.target.value }))}
          />
          <div className="flex flex-col gap-1.5">
            <label className="text-sm font-medium text-ink-900">Notes <span className="text-ink-400 font-normal">(optional)</span></label>
            <textarea
              rows={2}
              value={form.notes}
              onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              placeholder="Agenda, focus for today, etc."
              className="w-full rounded-lg border border-border p-3 text-sm outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100"
            />
          </div>
        </div>
      </Modal>
    </div>
  );
}
