import { useEffect, useState } from "react";
import { Check, X, Clock, CalendarPlus } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import { Select } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { useToast } from "../../context/ToastContext";
import {
  getBatches,
  getStudentsForBatch,
  getStandups,
  scheduleStandup,
  overrideAttendance,
} from "../../services/trainerService";

const todayIso = () => new Date().toISOString().slice(0, 10);
const TONE = { Present: "success", Late: "warning", Absent: "error", Excused: "neutral" };

export default function TrainerStandups() {
  const { notify } = useToast();
  const [batches, setBatches] = useState([]);
  const [batchId, setBatchId] = useState("");
  const [standup, setStandup] = useState(null);
  const [roster, setRoster] = useState([]); // { userUuid, name, status, marked }
  const [loading, setLoading] = useState(false);
  const [scheduling, setScheduling] = useState(false);

  useEffect(() => {
    getBatches()
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
      .then(([standups, students]) => {
        const active = standups.find((s) => s.status !== "Cancelled") || null;
        setStandup(active);
        setRoster(
          students
            .filter((s) => s.status === "ACTIVE" || s.status === "ON_PIP")
            .map((s) => ({ userUuid: s.userUuid, name: s.name, status: "Absent", marked: false }))
        );
      })
      .catch((e) => notify(e.message || "Could not load the standup.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load(batchId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [batchId]);

  const schedule = async () => {
    setScheduling(true);
    try {
      await scheduleStandup({ batchId, scheduledAt: new Date().toISOString(), lateCutoffMinutes: 15 });
      notify("Today's standup scheduled.", { type: "success" });
      load(batchId);
    } catch (err) {
      notify(err.message || "Could not schedule the standup.", { type: "error" });
    } finally {
      setScheduling(false);
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

  return (
    <div>
      <PageHeader
        title="Daily Standups & Attendance"
        subtitle="Mark attendance for today's standup"
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
        />

        {loading ? (
          <div className="flex justify-center py-16"><LoadingSpinner label="Loading…" /></div>
        ) : !standup ? (
          <div className="py-12 flex flex-col items-center text-center gap-3">
            <p className="text-sm text-ink-500">No standup scheduled for today in this batch.</p>
            <Button icon={CalendarPlus} loading={scheduling} onClick={schedule} disabled={!batchId}>
              Schedule today's standup
            </Button>
          </div>
        ) : (
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
                    {r.marked && <Check size={13} className="text-success-600" />}
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
        )}
      </Card>
    </div>
  );
}
