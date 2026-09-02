import { useEffect, useState } from "react";
import { GraduationCap, CheckCircle2 } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import ConfirmDialog from "../../components/ui/ConfirmDialog";
import { useToast } from "../../context/ToastContext";
import { approveGraduation, ensureGraduationExitHandoff } from "../../services/trainerService";
import { useSearchParams } from "react-router-dom";

const CANDIDATES = [];

// Real attendance % for a student — the same "msh_attendance_logs" log
// Standups & Attendance reads/writes to. A student with no logged days yet
// (just joined, no standup taken) has no signal, so we return null rather
// than a fabricated number — the table shows "No data yet" instead of a
// misleading percentage.
function computeAttendance(studentName) {
  try {
    const raw = localStorage.getItem("msh_attendance_logs");
    const logs = raw ? JSON.parse(raw) : [];
    const mine = logs.filter((l) => l.name.toLowerCase() === studentName.toLowerCase());
    if (!mine.length) return null;
    const present = mine.filter((l) => l.status === "Present").length;
    return Math.round((present / mine.length) * 100);
  } catch (e) {
    return null;
  }
}

// Real task completion % — mirrors the same calculation Performance
// Analytics uses (completed / assigned tasks for that student). No tasks
// assigned yet means no signal, so we return null instead of guessing.
function computeTaskCompletion(studentName) {
  try {
    const raw = localStorage.getItem("msh_sprint_tasks");
    const tasks = raw ? JSON.parse(raw) : [];
    const mine = tasks.filter((t) => t.assignee === studentName);
    if (!mine.length) return null;
    const completed = mine.filter((t) => t.status === "Completed").length;
    return Math.round((completed / mine.length) * 100);
  } catch (e) {
    return null;
  }
}

// A student already has a certificate on record once a trainer has approved
// their graduation (see approveGraduation in trainerService). Checking this
// on load — instead of only tracking "cleared" in local component state —
// means the "Cleared" badge survives a page refresh instead of reverting to
// "Pending Review" for a student who was already approved. Returns the
// certificate itself (or null) so the caller can also read its track/id.
function findIssuedCertificate(studentName) {
  try {
    const raw = localStorage.getItem("msh_certificates");
    const certs = raw ? JSON.parse(raw) : [];
    return certs.find((c) => c.studentName?.toLowerCase() === studentName.toLowerCase()) || null;
  } catch (e) {
    return null;
  }
}

export default function TrainerGraduation() {
  const [candidates, setCandidates] = useState(() => {
    const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
    let students = [];
    if (rawList) {
      try {
        const parsed = JSON.parse(rawList);
        students = parsed.filter((u) => u.role === "student" && u.email !== "ananya.student@moriah.io");
      } catch (e) {
        students = [];
      }
    }
    const dynamicCandidates = [...CANDIDATES];
    students.forEach((s) => {
      if (!dynamicCandidates.some((c) => c.name.toLowerCase() === s.name.toLowerCase())) {
        const cert = findIssuedCertificate(s.name);
        dynamicCandidates.push({
          id: s.id,
          name: s.name,
          batch: s.batch || "FS-Batch-14",
          attendance: computeAttendance(s.name),
          taskCompletion: computeTaskCompletion(s.name),
          cleared: !!cert
        });
      }
    });
    return dynamicCandidates;
  });
  const [confirmId, setConfirmId] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const { notify } = useToast();
  const [searchParams] = useSearchParams();
  const query = searchParams.get("search")?.toLowerCase() || "";
  const filteredCandidates = candidates.filter(
    (c) => c.name.toLowerCase().includes(query) || c.batch.toLowerCase().includes(query)
  );

  // Backfill: a student can already be "Cleared" (certificate on record)
  // from an approval that happened before the HR handoff existed, or from
  // any other path that issued a certificate without going through this
  // page's approve button. Without this, that student's certificate exists
  // but HR's Exit Management never received a record for them. Runs once
  // on load and is a no-op for anyone already handed off.
  useEffect(() => {
    candidates.forEach((c) => {
      if (!c.cleared) return;
      const cert = findIssuedCertificate(c.name);
      if (!cert) return;
      ensureGraduationExitHandoff({
        studentId: c.id,
        studentName: c.name,
        track: cert.track,
        certificateId: cert.id,
      });
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const approve = async () => {
    setSubmitting(true);
    try {
      const candidate = candidates.find((c) => c.id === confirmId);
      // Look up the student's registered email so the issued certificate can
      // be matched back to their account (Certificates page filters by this).
      let studentEmail = null;
      try {
        const raw = localStorage.getItem("mORIAH_REGISTERED_USERS");
        const list = raw ? JSON.parse(raw) : [];
        const match = list.find((u) => u.name.toLowerCase() === candidate?.name.toLowerCase());
        studentEmail = match?.email || null;
      } catch (e) {
        studentEmail = null;
      }

      const trackByBatch = {
        "FS-Batch-14": "Full-Stack Development",
        "DA-Batch-07": "Data Analytics",
        "UX-Batch-05": "Product Design",
        "BE-Batch-09": "Backend Engineering",
      };

      await approveGraduation(confirmId, candidate?.name, trackByBatch[candidate?.batch] || "Full-Stack Development", studentEmail);
      setCandidates((prev) => prev.map((c) => (c.id === confirmId ? { ...c, cleared: true } : c)));
      notify("Student cleared for graduation — certificate issued and handed off to HR.", { type: "success", title: "Clearance approved" });
      setConfirmId(null);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader title="Graduation Approval" subtitle="Final sign-off to unlock certificates and HR exit clearance" breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Graduation" }]} />

      <Card>
        <Table
          data={filteredCandidates}
          columns={[
            { key: "name", header: "Student" },
            { key: "batch", header: "Batch" },
            { key: "attendance", header: "Attendance", render: (r) => r.attendance === null ? <span className="text-ink-400">No data yet</span> : `${r.attendance}%` },
            { key: "taskCompletion", header: "Task Completion", render: (r) => r.taskCompletion === null ? <span className="text-ink-400">No data yet</span> : `${r.taskCompletion}%` },
            { key: "status", header: "Status", render: (r) => r.cleared ? <Badge tone="success"><CheckCircle2 size={11} /> Cleared — Sent to HR</Badge> : <Badge tone="gold">Pending Review</Badge> },
            { key: "action", header: "", render: (r) => !r.cleared && (
              <Button size="sm" icon={GraduationCap} onClick={() => setConfirmId(r.id)}>Approve</Button>
            ) },
          ]}
        />
      </Card>

      <ConfirmDialog
        open={!!confirmId}
        onClose={() => setConfirmId(null)}
        onConfirm={approve}
        loading={submitting}
        title="Approve graduation?"
        description="This unlocks the student's certificate and triggers HR exit clearance workflows."
        confirmLabel="Approve Clearance"
      />
    </div>
  );
}