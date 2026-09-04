import { useEffect, useState } from "react";
import { Video, CheckCircle2, CalendarClock } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { useToast } from "../../context/ToastContext";
import { getMyStandups, getMyAttendance } from "../../services/studentService";

const todayIso = () => new Date().toISOString().slice(0, 10);
const TONE = { Present: "success", Late: "warning", Absent: "error", Excused: "neutral" };

export default function StudentAttendance() {
  const { notify } = useToast();
  const [loading, setLoading] = useState(true);
  const [standup, setStandup] = useState(null);
  const [history, setHistory] = useState([]);

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

  const mine = standup ? myAttendanceForStandup(standup.id) : null;
  const standupTime = standup
    ? new Date(standup.scheduledAt).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })
    : "";

  return (
    <div>
      <PageHeader
        title="Standups & Attendance"
        subtitle="Join today's standup from the link below — your trainer records attendance at the start of the day"
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
                  <CheckCircle2 size={14} /> Marked by trainer — {mine.status}
                </Badge>
              ) : (
                <span className="text-xs text-ink-400">Your trainer hasn't marked attendance for this standup yet.</span>
              )}
            </div>
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
              render: (r) => (r.autoMarked ? "Auto (not marked)" : r.markedByPm ? "By trainer" : "By trainer"),
            },
          ]}
        />
      </Card>
    </div>
  );
}
