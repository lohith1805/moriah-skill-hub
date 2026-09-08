import { useEffect, useState } from "react";
import { ClipboardList, Check } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import { Select, Input } from "../../components/ui/FormField";
import { getBatches, getSprints, getStudentsForBatch } from "../../services/trainerService";
import { getWeeklyReviews, saveWeeklyReview, mondayOf } from "../../services/pipService";
import { useToast } from "../../context/ToastContext";

const RATINGS = [
  { value: "SATISFACTORY", label: "Satisfactory", tone: "success" },
  { value: "NEEDS_IMPROVEMENT", label: "Needs improvement", tone: "warning" },
  { value: "UNSATISFACTORY", label: "Unsatisfactory", tone: "error" },
];

export default function WeeklyReviews() {
  const { notify } = useToast();
  const [batches, setBatches] = useState([]);
  const [batchId, setBatchId] = useState("");
  const [sprints, setSprints] = useState([]);
  const [sprintId, setSprintId] = useState("");
  const [weekStart, setWeekStart] = useState(mondayOf());
  const [students, setStudents] = useState([]);
  const [rows, setRows] = useState({}); // uuid -> { rating, notes, savedAt }
  const [loading, setLoading] = useState(false);
  const [savingUuid, setSavingUuid] = useState("");

  useEffect(() => {
    getBatches({ scope: "mine" }).then(setBatches).catch(() => setBatches([]));
  }, []);

  const pickBatch = async (id) => {
    setBatchId(id);
    setSprintId("");
    setSprints([]);
    setStudents([]);
    setRows({});
    if (!id) return;
    const [sp, st] = await Promise.all([
      getSprints(id).catch(() => []),
      getStudentsForBatch(id).catch(() => []),
    ]);
    setSprints(sp);
    const active = sp.find((s) => s.status === "Active") || sp[0];
    if (active) setSprintId(String(active.id));
    setStudents(st.filter((s) => s.status === "ACTIVE" || s.status === "ON_PIP"));
  };

  const loadRatings = async () => {
    if (!batchId || !weekStart) return;
    setLoading(true);
    try {
      const existing = await getWeeklyReviews(batchId, mondayOf(weekStart));
      const map = {};
      existing.forEach((r) => { map[r.studentUuid] = { rating: r.rating, notes: r.notes, savedAt: r.reviewedAt }; });
      setRows(map);
    } catch (err) {
      notify(err?.message || "Couldn't load existing ratings.", { type: "error" });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadRatings(); /* eslint-disable-next-line */ }, [batchId, weekStart]);

  const setRow = (uuid, patch) => setRows((r) => ({ ...r, [uuid]: { ...(r[uuid] || {}), ...patch } }));

  const save = async (student) => {
    const row = rows[student.userUuid] || {};
    if (!row.rating) { notify("Pick a rating first.", { type: "warning" }); return; }
    if (!sprintId) { notify("Select a sprint for the week.", { type: "warning" }); return; }
    setSavingUuid(student.userUuid);
    try {
      await saveWeeklyReview({
        studentUuid: student.userUuid,
        batchId,
        sprintId,
        weekStart: mondayOf(weekStart),
        rating: row.rating,
        notes: row.notes || "",
      });
      setRow(student.userUuid, { savedAt: new Date().toISOString() });
      notify(`Weekly review saved for ${student.name}.`, { type: "success" });
    } catch (err) {
      notify(err?.message || "Couldn't save the review.", { type: "error" });
    } finally {
      setSavingUuid("");
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Weekly Reviews"
        subtitle="One qualitative rating per student per week — this is the data source for the REVIEW_FAILED PIP rule and the PIP clearance check"
        breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Weekly Reviews" }]}
      />

      <Card>
        <div className="grid sm:grid-cols-3 gap-4">
          <Select label="Batch" placeholder="Select a batch" value={batchId} onChange={(e) => pickBatch(e.target.value)}
            options={batches.map((b) => ({ value: b.id, label: b.name }))} />
          <Select label="Sprint (for the week)" placeholder={sprints.length ? "Select sprint" : "Pick a batch first"}
            value={sprintId} onChange={(e) => setSprintId(e.target.value)}
            options={sprints.map((s) => ({ value: String(s.id), label: `Sprint ${s.number} · ${s.status}` }))} />
          <Input label="Week starting (snaps to Monday)" type="date" value={weekStart}
            onChange={(e) => setWeekStart(e.target.value)} />
        </div>
        {weekStart && <p className="text-xs text-ink-400 mt-2">Week of {mondayOf(weekStart)}</p>}
      </Card>

      {!batchId ? (
        <EmptyState icon={ClipboardList} title="Pick a batch" description="Choose a batch to rate this week's performance." />
      ) : loading ? (
        <div className="flex justify-center py-12"><LoadingSpinner label="Loading ratings…" /></div>
      ) : students.length === 0 ? (
        <EmptyState icon={ClipboardList} title="No active students" description="This batch has no active or on-PIP students to rate." />
      ) : (
        <div className="flex flex-col gap-3">
          {students.map((s) => {
            const row = rows[s.userUuid] || {};
            return (
              <Card key={s.userUuid} className="flex flex-col gap-3">
                <div className="flex items-center justify-between gap-3 flex-wrap">
                  <p className="font-semibold text-ink-900">{s.name}</p>
                  {row.savedAt && <Badge tone="success" className="flex items-center gap-1"><Check size={12} /> Saved</Badge>}
                </div>
                <div className="flex flex-wrap gap-2">
                  {RATINGS.map((rt) => (
                    <button
                      key={rt.value}
                      type="button"
                      onClick={() => setRow(s.userUuid, { rating: rt.value, savedAt: null })}
                      className={`text-xs font-medium rounded-full px-3 py-1.5 border transition-colors ${
                        row.rating === rt.value
                          ? "border-primary-500 bg-primary-50 text-primary-800"
                          : "border-border bg-white text-ink-600 hover:border-ink-300"
                      }`}
                    >
                      {rt.label}
                    </button>
                  ))}
                </div>
                <Input
                  label="Notes (optional)"
                  placeholder="What stood out this week?"
                  value={row.notes || ""}
                  onChange={(e) => setRow(s.userUuid, { notes: e.target.value, savedAt: null })}
                />
                <div className="flex justify-end">
                  <Button size="sm" loading={savingUuid === s.userUuid} onClick={() => save(s)} disabled={!row.rating}>
                    Save review
                  </Button>
                </div>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
}
