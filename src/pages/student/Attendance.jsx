import { useEffect, useState } from "react";
import { Video, CheckCircle2, CalendarClock } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { useToast } from "../../context/ToastContext";
import { getMyStandups, getMyAttendance, checkInToStandup } from "../../services/studentService";

const todayIso = () => new Date().toISOString().slice(0, 10);
const TONE = { Present: "success", Late: "warning", Absent: "error", Excused: "neutral" };

export default function StudentAttendance() {
  const { notify } = useToast();
  const [loading, setLoading] = useState(true);
  const [standup, setStandup] = useState(null);
  const [history, setHistory] = useState([]);
  const [note, setNote] = useState("");
  const [checkingIn, setCheckingIn] = useState(false);

  const myAttendanceForStandup = (standupId) => history.find((h) => String(h.standupId) === String(standupId));

  const load = () => {
    setLoading(true);
    Promise.all([getMyStandups(todayIso()), getMyAttendance()])
      .then(([standups, att]) => {
        setStandup(standups.find((s) => s.status !== "CANCELLED") || null);
        setHistory(att);
      })
      .catch((e) => notify(e.message || "Could not load your standups.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const checkIn = async () => {
    if (!standup) return;
    setCheckingIn(true);
    try {
      const res = await checkInToStandup(standup.id, note.trim());
      notify(`Checked in — marked ${res.status}.`, { type: "success" });
      load();
    } catch (err) {
      notify(err.message || "Could not check in.", { type: "error" });
    } finally {
      setCheckingIn(false);
    }
  };

  const mine = standup ? myAttendanceForStandup(standup.id) : null;
  const standupTime = standup
    ? new Date(standup.scheduledAt).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })
    : "";

  return (
    <div>
      <PageHeader
        title="Standups & Attendance"
        subtitle="Join today's standup and check in — your trainer confirms attendance from their dashboard"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Standups" }]}
      />

      <Card className="border-l-4 border-l-primary-600">
        <CardHeader
          title="Today's Standup"
          subtitle={new Date().toLocaleDateString("en-IN", { weekday: "long", day: "2-digit", month: "long" })}
        />
        {loading ? (
          <div className="flex justify-center py-12"><LoadingSpinner label="Loading…" /></div>
        ) : !standup ? (
          <p className="py-10 text-center text-sm text-ink-500">No standup scheduled for today.</p>
        ) : (
          <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center gap-x-6 gap-y-1 text-sm text-ink-600">
              <span><span className="text-ink-400">Starts</span> <strong className="text-ink-900">{standupTime}</strong></span>
              <span><span className="text-ink-400">Late after</span> <strong className="text-ink-900">{standup.lateCutoffMinutes} min</strong></span>
            </div>
            {standup.notes && (
              <p className="text-xs text-ink-500 bg-cream-50 border border-border/50 rounded-lg p-3">{standup.notes}</p>
            )}

            <div className="flex flex-wrap items-center gap-3">
              {standup.meetingLink ? (
                <a href={standup.meetingLink} target="_blank" rel="noreferrer">
                  <Button icon={Video}>Join meet</Button>
                </a>
              ) : (
                <span className="text-xs text-ink-400">No meeting link was added for this standup.</span>
              )}

              {mine ? (
                <Badge tone={TONE[mine.status] || "neutral"} className="px-3 py-1 flex items-center gap-1">
                  <CheckCircle2 size={14} /> {mine.markedByPm ? "Marked" : "Checked in"} — {mine.status}
                </Badge>
              ) : null}
            </div>

            {!mine && (
              <div className="flex flex-col gap-2 border-t border-border/50 pt-4">
                <label className="text-xs font-semibold text-ink-700">Anything blocking you today? <span className="font-normal text-ink-400">(optional)</span></label>
                <textarea
                  rows={2}
                  value={note}
                  onChange={(e) => setNote(e.target.value)}
                  placeholder="e.g. Blocked on API test credentials for the checkout task."
                  className="w-full text-sm rounded-lg border border-border p-3 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100"
                />
                <div className="flex justify-end">
                  <Button icon={CheckCircle2} loading={checkingIn} onClick={checkIn}>I've joined — check in</Button>
                </div>
              </div>
            )}
          </div>
        )}
      </Card>

      <Card className="mt-4">
        <CardHeader title="Attendance History" subtitle="Your standup attendance record" icon={CalendarClock} />
        <Table
          loading={loading}
          data={history}
          emptyTitle="No attendance recorded yet"
          columns={[
            {
              key: "scheduledAt",
              header: "Standup",
              className: "text-left",
              render: (r) => new Date(r.scheduledAt).toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" }),
            },
            {
              key: "status",
              header: "Status",
              className: "text-left",
              render: (r) => <Badge tone={TONE[r.status] || "neutral"}>{r.status}</Badge>,
            },
            {
              key: "source",
              header: "Recorded",
              className: "text-left text-xs text-ink-500",
              render: (r) => (r.autoMarked ? "Auto (no check-in)" : r.markedByPm ? "By trainer" : "Self check-in"),
            },
            {
              key: "checkedInAt",
              header: "Checked in",
              className: "text-left text-xs text-ink-500",
              render: (r) => (r.checkedInAt ? new Date(r.checkedInAt).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" }) : "—"),
            },
          ]}
        />
      </Card>
    </div>
  );
}
