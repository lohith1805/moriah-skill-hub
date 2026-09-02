import { useEffect, useState } from "react";
import {
  LogOut, CheckCircle2, Plus, Edit, Trash2, AlertTriangle,
  FileWarning, ShieldAlert, Laptop, Key, Award, FileText
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Tabs from "../../components/ui/Tabs";
import Modal from "../../components/ui/Modal";
import ConfirmDialog from "../../components/ui/ConfirmDialog";
import { Input, Select, Textarea } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";
import { trySyncGraduateToTalentPool, hasUploadedResume } from "../../services/hrService";

const INITIAL_EXITS = [
  {
    id: "x1",
    name: "Vikram Sethi",
    type: "Staff Member",
    department: "Engineering",
    itClearance: "Complete",
    accountsClearance: "Complete",
    exitInterview: "Completed",
    clearance: "Complete",
    exitDate: "2026-08-30",
    reason: "Higher studies abroad"
  },
  {
    id: "x2",
    name: "Pooja Hegde",
    type: "Intern",
    department: "Incubation",
    itClearance: "Pending",
    accountsClearance: "Complete",
    exitInterview: "Scheduled",
    clearance: "Pending",
    exitDate: "2026-09-10",
    reason: "Completion of internship tenure"
  }
];

const INITIAL_DISCIPLINARY = [
  {
    id: "d1",
    name: "Ritesh Agarwal",
    type: "Student / Intern",
    level: "Level 1 Warning",
    reason: "Consecutive missed sprint deadlines & standup absenteeism",
    actionRequired: "Submit 2 pending PRs within 72 hours under PIP oversight",
    issuedDate: "2026-08-25",
    status: "Active"
  }
];

export default function HrExitManagement() {
  const [exits, setExits] = useState([]);
  const [pipRecords, setPipRecords] = useState([]);
  const [disciplinary, setDisciplinary] = useState([]);
  const [loading, setLoading] = useState(true);
  const [confirmId, setConfirmId] = useState(null);

  // Record Exit modal
  const [createOpen, setCreateOpen] = useState(false);
  const [values, setValues] = useState({
    name: "",
    type: "Staff Member",
    department: "Engineering",
    reason: "",
    exitDate: "",
    itClearance: "Pending",
    accountsClearance: "Pending",
    exitInterview: "Scheduled"
  });

  // Issue Disciplinary Notice modal
  const [noticeOpen, setNoticeOpen] = useState(false);
  const [noticeValues, setNoticeValues] = useState({
    name: "",
    type: "Student / Intern",
    level: "Level 1 Warning",
    reason: "",
    actionRequired: ""
  });

  const { notify } = useToast();

  useEffect(() => {
    // Load Exits
    const savedExits = localStorage.getItem("msh_hr_exits");
    let loadedExits = INITIAL_EXITS;
    if (savedExits) {
      loadedExits = JSON.parse(savedExits);
      setExits(loadedExits);
    } else {
      localStorage.setItem("msh_hr_exits", JSON.stringify(INITIAL_EXITS));
      setExits(INITIAL_EXITS);
    }

    // Backfill: publish any graduate whose exit clearance is finalized AND
    // who has since uploaded a resume, but who isn't in the Talent Pool yet
    // (e.g. clearance was finalized before they uploaded, or before this
    // resume gate existed). Idempotent, so this is harmless to run on every
    // load — candidates without a resume are simply left out until they
    // upload one.
    loadedExits
      .filter((x) => x.type === "Graduating Student" && x.clearance === "Complete")
      .forEach((x) => trySyncGraduateToTalentPool(x.name));

    // Load Disciplinary
    const savedDisc = localStorage.getItem("msh_hr_disciplinary");
    if (savedDisc) {
      setDisciplinary(JSON.parse(savedDisc));
    } else {
      localStorage.setItem("msh_hr_disciplinary", JSON.stringify(INITIAL_DISCIPLINARY));
      setDisciplinary(INITIAL_DISCIPLINARY);
    }

    // Load Live PIP Records from Trainer Module
    try {
      const rawPip = localStorage.getItem("msh_pip_records");
      setPipRecords(rawPip ? JSON.parse(rawPip) : []);
    } catch (e) {
      setPipRecords([]);
    }

    setLoading(false);
  }, []);

  const persistExits = (data) => {
    setExits(data);
    localStorage.setItem("msh_hr_exits", JSON.stringify(data));
  };

  const persistDisciplinary = (data) => {
    setDisciplinary(data);
    localStorage.setItem("msh_hr_disciplinary", JSON.stringify(data));
  };

  const handleCreateExit = (e) => {
    e.preventDefault();
    const validation = validateForm(values, { name: [required], exitDate: [required] });
    if (Object.keys(validation).length) return;

    const newExit = {
      id: `x_${Date.now()}`,
      name: values.name,
      type: values.type,
      department: values.department,
      reason: values.reason || "Resignation",
      exitDate: values.exitDate,
      itClearance: values.itClearance,
      accountsClearance: values.accountsClearance,
      exitInterview: values.exitInterview,
      clearance: "Pending"
    };

    const updated = [newExit, ...exits];
    persistExits(updated);
    notify(`Exit clearance workflow initialized for ${values.name}.`, { type: "success" });
    setCreateOpen(false);
  };

  const finalizeClearance = () => {
    const target = exits.find((r) => r.id === confirmId);
    const updated = exits.map((r) =>
      r.id === confirmId
        ? { ...r, clearance: "Complete", itClearance: "Complete", accountsClearance: "Complete", exitInterview: "Completed" }
        : r
    );
    persistExits(updated);

    // A graduating student becomes recruitable once their exit clearance is
    // finalized AND they've uploaded a resume — not before. See
    // hrService.trySyncGraduateToTalentPool.
    if (target?.type === "Graduating Student") {
      const nowVisible = trySyncGraduateToTalentPool(target.name);
      notify(
        nowVisible
          ? "Departmental clearance complete. Experience and relieving certificate unlocked — candidate is now visible to Clients in the Talent Pool."
          : "Departmental clearance complete. Experience and relieving certificate unlocked. Candidate stays hidden from Clients until they upload their resume.",
        { type: "success" }
      );
    } else {
      notify("Departmental clearance complete. Experience and relieving certificate unlocked.", { type: "success" });
    }
    setConfirmId(null);
  };

  const handleIssueNotice = (e) => {
    e.preventDefault();
    const validation = validateForm(noticeValues, { name: [required], reason: [required] });
    if (Object.keys(validation).length) return;

    const newNotice = {
      id: `d_${Date.now()}`,
      name: noticeValues.name,
      type: noticeValues.type,
      level: noticeValues.level,
      reason: noticeValues.reason,
      actionRequired: noticeValues.actionRequired || "Compliance required immediately.",
      issuedDate: new Date().toISOString().slice(0, 10),
      status: "Active"
    };

    const updated = [newNotice, ...disciplinary];
    persistDisciplinary(updated);
    notify(`Formal Disciplinary Notice issued to ${noticeValues.name}.`, { type: "warning", title: "Disciplinary Notice Issued" });
    setNoticeOpen(false);
    setNoticeValues({ name: "", type: "Student / Intern", level: "Level 1 Warning", reason: "", actionRequired: "" });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Performance & Exit Management"
        subtitle="PIP performance tracking, formal disciplinary notices, and exit clearance workflows"
        breadcrumbs={[{ label: "Dashboard", to: "/hr/dashboard" }, { label: "Exit & PIP" }]}
        action={
          <div className="flex gap-2">
            <Button variant="secondary" icon={FileWarning} onClick={() => setNoticeOpen(true)}>
              Issue Disciplinary Notice
            </Button>
            <Button icon={Plus} onClick={() => setCreateOpen(true)}>
              Record Exit Workflow
            </Button>
          </div>
        }
      />

      <Card>
        <Tabs
          tabs={[
            { key: "clearance", label: "Exit Clearances & Interviews" },
            { key: "pip", label: `Active PIP Monitoring (${pipRecords.length})` },
            { key: "disciplinary", label: `Disciplinary Notices (${disciplinary.length})` }
          ]}
        >
          {(active) => {
            if (active === "clearance") {
              return (
                <Table
                  loading={loading}
                  data={exits}
                  columns={[
                    {
                      key: "name",
                      header: "Employee / Intern",
                      className: "text-left font-medium text-ink-900",
                      render: (r) => (
                        <div>
                          <p className="font-semibold text-ink-900">{r.name}</p>
                          <p className="text-xs text-ink-500">{r.type} · {r.department}</p>
                        </div>
                      )
                    },
                    {
                      key: "itClearance",
                      header: "IT Asset Return",
                      className: "text-left",
                      render: (r) => <Badge tone={r.itClearance === "Complete" ? "success" : "warning"}>{r.itClearance}</Badge>
                    },
                    {
                      key: "accounts",
                      header: "Accounts No-Dues",
                      className: "text-left",
                      render: (r) => <Badge tone={r.accountsClearance === "Complete" ? "success" : "warning"}>{r.accountsClearance}</Badge>
                    },
                    {
                      key: "interview",
                      header: "Exit Interview",
                      className: "text-left",
                      render: (r) => <Badge tone={r.exitInterview === "Completed" ? "success" : "neutral"}>{r.exitInterview}</Badge>
                    },
                    {
                      key: "clearance",
                      header: "Overall Clearance",
                      className: "text-left",
                      render: (r) => <Badge tone={r.clearance === "Complete" ? "success" : "gold"}>{r.clearance}</Badge>
                    },
                    {
                      key: "resume",
                      header: "Resume (Client Visibility)",
                      className: "text-left",
                      render: (r) =>
                        r.type === "Graduating Student" ? (
                          <Badge tone={hasUploadedResume(r.name) ? "success" : "warning"}>
                            {hasUploadedResume(r.name) ? "Uploaded" : "Not Uploaded"}
                          </Badge>
                        ) : (
                          <span className="text-xs text-ink-400">N/A</span>
                        )
                    },
                    {
                      key: "action",
                      header: "",
                      className: "text-right",
                      render: (r) => (
                        <div className="flex gap-2 justify-end">
                          {r.clearance !== "Complete" && (
                            <Button size="sm" icon={CheckCircle2} onClick={() => setConfirmId(r.id)}>
                              Finalize Clearance
                            </Button>
                          )}
                          <Button size="sm" variant="danger" icon={Trash2} onClick={() => {
                            const updated = exits.filter(x => x.id !== r.id);
                            persistExits(updated);
                          }}>
                            Delete
                          </Button>
                        </div>
                      )
                    }
                  ]}
                />
              );
            }

            if (active === "pip") {
              return (
                <div className="flex flex-col gap-4 text-left">
                  <div className="p-3 bg-amber-50 rounded-lg border border-amber-200 text-amber-900 text-xs">
                    ⚡ <strong>Live Synced with Trainer PIP Engine:</strong> Showing active Performance Improvement Plans flagged by PMs/Trainers.
                  </div>
                  {pipRecords.length === 0 ? (
                    <p className="text-sm text-ink-400 py-8 text-center">No active student or staff PIP cases currently open.</p>
                  ) : (
                    <Table
                      data={pipRecords}
                      columns={[
                        { key: "studentName", header: "Student Name", className: "text-left font-medium text-ink-900" },
                        { key: "triggerReason", header: "PIP Trigger Reason", className: "text-left" },
                        { key: "startDate", header: "Start Date", className: "text-left" },
                        {
                          key: "status",
                          header: "Status",
                          className: "text-left",
                          render: (r) => <Badge tone={r.status === "Active" ? "warning" : "success"}>{r.status}</Badge>
                        }
                      ]}
                    />
                  )}
                </div>
              );
            }

            if (active === "disciplinary") {
              return (
                <Table
                  data={disciplinary}
                  columns={[
                    { key: "name", header: "Recipient", className: "text-left font-medium text-ink-900" },
                    { key: "level", header: "Warning Level", className: "text-left", render: (r) => <Badge tone="danger">{r.level}</Badge> },
                    { key: "reason", header: "Infraction / Reason", className: "text-left max-w-sm truncate" },
                    { key: "actionRequired", header: "Remedial Action", className: "text-left" },
                    { key: "issuedDate", header: "Date Issued", className: "text-left" }
                  ]}
                />
              );
            }
          }}
        </Tabs>
      </Card>

      {/* Record Exit Workflow Modal */}
      <Modal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        title="Initialize Employee Exit Clearance Workflow (MSH-FR-HR-05)"
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>Cancel</Button>
            <Button icon={Plus} onClick={handleCreateExit}>Initialize Workflow</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleCreateExit}>
          <Input label="Full Name" required value={values.name} onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Select
              label="Staff / Candidate Type"
              options={[
                { value: "Staff Member", label: "Full-Time Staff Member" },
                { value: "Intern", label: "Intern / Apprentice" },
                { value: "Graduating Student", label: "Graduating Student" }
              ]}
              value={values.type}
              onChange={(e) => setValues((v) => ({ ...v, type: e.target.value }))}
            />
            <Input label="Exit / Relieving Date" type="date" required value={values.exitDate} onChange={(e) => setValues((v) => ({ ...v, exitDate: e.target.value }))} />
          </div>
          <Input label="Reason for Separation" placeholder="e.g. Higher studies, Career progression" value={values.reason} onChange={(e) => setValues((v) => ({ ...v, reason: e.target.value }))} />
        </form>
      </Modal>

      {/* Issue Disciplinary Notice Modal */}
      <Modal
        open={noticeOpen}
        onClose={() => setNoticeOpen(false)}
        title="Issue Formal Disciplinary Notice (MSH-FR-HR-05)"
        description="Records a formal performance warning against the employee/intern profile."
        footer={
          <>
            <Button variant="secondary" onClick={() => setNoticeOpen(false)}>Cancel</Button>
            <Button icon={ShieldAlert} onClick={handleIssueNotice}>Issue Notice</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleIssueNotice}>
          <Input label="Recipient Name" required placeholder="e.g. Ritesh Agarwal" value={noticeValues.name} onChange={(e) => setNoticeValues((v) => ({ ...v, name: e.target.value }))} />
          <Select
            label="Warning Escalation Level"
            options={[
              { value: "Level 1 Warning", label: "Level 1 Warning (First Advisory)" },
              { value: "Level 2 Warning", label: "Level 2 Warning (PIP Escalation)" },
              { value: "Final Disciplinary Notice", label: "Final Disciplinary Notice (Termination Pending)" }
            ]}
            value={noticeValues.level}
            onChange={(e) => setNoticeValues((v) => ({ ...v, level: e.target.value }))}
          />
          <Textarea label="Infraction Reason & Policy Breach" required rows={3} placeholder="Describe the breach (e.g. code plagiarism, unexcused absence, client NDA breach)..." value={noticeValues.reason} onChange={(e) => setNoticeValues((v) => ({ ...v, reason: e.target.value }))} />
          <Input label="Corrective Action Required" placeholder="e.g. Submit 2 pending sprint modules by Friday" value={noticeValues.actionRequired} onChange={(e) => setNoticeValues((v) => ({ ...v, actionRequired: e.target.value }))} />
        </form>
      </Modal>

      <ConfirmDialog
        open={!!confirmId}
        onClose={() => setConfirmId(null)}
        onConfirm={finalizeClearance}
        title="Finalize Multi-Department Exit Clearance?"
        description="This signs off IT asset recovery, accounts no-dues, and exit interview notes, immediately unlocking the Experience & Relieving Certificate."
        confirmLabel="Finalize & Unlock Certificate"
      />
    </div>
  );
}