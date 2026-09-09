import { useEffect, useState } from "react";
import { GraduationCap, CheckCircle2, Award } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import { Select } from "../../components/ui/FormField";
import ConfirmDialog from "../../components/ui/ConfirmDialog";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { useToast } from "../../context/ToastContext";
import { getBatches, getStudentsForBatch, graduateStudent, issueCertificate } from "../../services/trainerService";

const STATUS_TONE = {
  ACTIVE: "info",
  ON_PIP: "warning",
  GRADUATED: "success",
  TERMINATED: "error",
  REASSIGNED: "neutral",
};
const canGraduate = (s) => s.status === "ACTIVE" || s.status === "ON_PIP";

export default function TrainerGraduation() {
  const { notify } = useToast();
  const [batches, setBatches] = useState([]);
  const [batchId, setBatchId] = useState("");
  const [roster, setRoster] = useState([]);
  const [loading, setLoading] = useState(false);
  const [target, setTarget] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [issuingUuid, setIssuingUuid] = useState("");
  const [issuedUuids, setIssuedUuids] = useState(() => new Set());

  useEffect(() => {
    getBatches({ scope: "mine" })
      .then((b) => {
        setBatches(b);
        if (b.length) setBatchId(String(b[0].id));
      })
      .catch((e) => notify(e.message || "Could not load batches.", { type: "error" }));
  }, [notify]);

  const loadRoster = (id) => {
    if (!id) return setRoster([]);
    setLoading(true);
    getStudentsForBatch(id)
      .then(setRoster)
      .catch((e) => notify(e.message || "Could not load the roster.", { type: "error" }))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadRoster(batchId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [batchId]);

  // Issue (or re-attempt) a certificate for a student who is already GRADUATED — the `approve`
  // flow only tries once, in the same click as graduating, so a student graduated while a
  // precondition wasn't met (open sprint, unfinished task) otherwise has no path to a certificate.
  const issueFor = async (student) => {
    setIssuingUuid(student.userUuid);
    try {
      const cert = await issueCertificate(batchId, student.userUuid, "COMPLETION");
      setIssuedUuids((s) => new Set(s).add(student.userUuid));
      notify(`Certificate ${cert.certificateNumber || ""} issued for ${student.name}.`, {
        type: "success",
        title: "Certificate issued",
      });
    } catch (err) {
      notify(err.message || "Could not issue the certificate.", { type: "error" });
    } finally {
      setIssuingUuid("");
    }
  };

  const approve = async () => {
    const student = target;
    setSubmitting(true);
    try {
      await graduateStudent(batchId, student.userUuid);
      let cert = null;
      try {
        cert = await issueCertificate(batchId, student.userUuid, "COMPLETION");
      } catch (certErr) {
        // Graduation succeeded; the certificate can be issued separately if a
        // precondition (all sprints COMPLETED, no open PIP) isn't met yet.
        notify(
          `${student.name} graduated. Certificate not issued yet: ${certErr.message || "check the batch's sprints and PIP status."}`,
          { type: "warning", title: "Graduated" }
        );
      }
      if (cert) {
        notify(
          `${student.name} graduated — certificate ${cert.certificateNumber || ""} issued.`,
          { type: "success", title: "Clearance approved" }
        );
      }
      setTarget(null);
      loadRoster(batchId);
    } catch (err) {
      notify(err.message || "Could not graduate this student.", { type: "error" });
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Graduation Approval"
        subtitle="Final sign-off — graduates the student and issues their completion certificate"
        breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Graduation" }]}
      />

      <Card className="mb-4">
        <Select
          label="Batch"
          value={batchId}
          onChange={(e) => setBatchId(e.target.value)}
          options={batches.map((b) => ({ value: String(b.id), label: `${b.name} · ${b.track}` }))}
          placeholder={batches.length ? "Select a batch" : "No batches — create one first"}
        />
      </Card>

      <Card>
        {loading ? (
          <div className="flex justify-center py-16"><LoadingSpinner label="Loading roster…" /></div>
        ) : (
          <Table
            data={roster}
            emptyTitle="No students in this batch"
            emptyHint="Enrol students on the Batches page, then graduate them here."
            columns={[
              {
                key: "name",
                header: "Student",
                className: "text-left font-medium text-ink-900",
                render: (r) => (
                  <div>
                    <p className="font-semibold text-ink-900">{r.name}</p>
                    <p className="text-xs text-ink-500">{r.email}</p>
                  </div>
                ),
              },
              {
                key: "finalScore",
                header: "Final Score",
                className: "text-left",
                render: (r) => (r.finalScore != null ? `${r.finalScore}` : <span className="text-ink-400">—</span>),
              },
              {
                key: "status",
                header: "Status",
                className: "text-left",
                render: (r) => <Badge tone={STATUS_TONE[r.status] || "neutral"}>{r.status}</Badge>,
              },
              {
                key: "action",
                header: "",
                className: "text-right",
                render: (r) =>
                  r.status === "GRADUATED" ? (
                    <div className="flex items-center justify-end gap-2">
                      <Badge tone="success"><CheckCircle2 size={11} className="inline mr-1" />Graduated</Badge>
                      {issuedUuids.has(r.userUuid) ? (
                        <Badge tone="neutral"><Award size={11} className="inline mr-1" />Certificate issued</Badge>
                      ) : (
                        <Button
                          size="sm"
                          variant="secondary"
                          icon={Award}
                          loading={issuingUuid === r.userUuid}
                          onClick={() => issueFor(r)}
                        >
                          Issue certificate
                        </Button>
                      )}
                    </div>
                  ) : canGraduate(r) ? (
                    <Button size="sm" icon={GraduationCap} onClick={() => setTarget(r)}>Approve</Button>
                  ) : (
                    <span className="text-xs text-ink-400">Not eligible</span>
                  ),
              },
            ]}
          />
        )}
      </Card>

      <ConfirmDialog
        open={!!target}
        onClose={() => setTarget(null)}
        onConfirm={approve}
        loading={submitting}
        title={target ? `Graduate ${target.name}?` : "Approve graduation?"}
        description="This sets the student to GRADUATED and issues a completion certificate (requires no open PIP, no unfinished tasks assigned to the student, and every sprint in the batch COMPLETED). It cannot be undone."
        confirmLabel="Approve & issue certificate"
      />
    </div>
  );
}
