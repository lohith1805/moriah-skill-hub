import { useEffect, useState } from "react";
import { Check, X, Clock, Fingerprint } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Tabs from "../../components/ui/Tabs";
import Modal from "../../components/ui/Modal";
import ProgressBar from "../../components/ui/ProgressBar";
import { Select } from "../../components/ui/FormField";
import {
  getEmployees, getLeaveRequests, actionLeaveRequest,
  getClockinLogs, logCheckin, markStaffAttendance, getStaffAttendanceSummary,
} from "../../services/hrService";
import { useToast } from "../../context/ToastContext";

const thisMonth = () => new Date().toISOString().slice(0, 7); // YYYY-MM
const daysAgoIso = (n) => {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
};

export default function HrAttendanceLeave() {
  const [employees, setEmployees] = useState([]);
  const [leaves, setLeaves] = useState([]);
  const [clockins, setClockins] = useState([]);
  const [ledger, setLedger] = useState([]);
  const [loading, setLoading] = useState(true);

  // Check-in logger modal
  const [checkinOpen, setCheckinOpen] = useState(false);
  const [checkinUuid, setCheckinUuid] = useState("");
  const [checkinDevice, setCheckinDevice] = useState("BIO-GATE-01");
  const [logging, setLogging] = useState(false);

  const { notify } = useToast();

  const load = () => {
    setLoading(true);
    Promise.all([
      getEmployees().catch(() => []),
      getLeaveRequests().catch((e) => {
        notify(e.message || "Could not load leave requests.", { type: "error" });
        return [];
      }),
      getClockinLogs({ from: daysAgoIso(14) }).catch(() => []),
      getStaffAttendanceSummary(thisMonth()).catch(() => []),
    ]).then(([e, l, c, s]) => {
      setEmployees(e);
      setLeaves(l);
      setClockins(c);
      setLedger(s);
      setLoading(false);
    });
  };

  useEffect(() => {
    load();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const decide = async (id, decision) => {
    try {
      const updated = await actionLeaveRequest(id, decision);
      setLeaves((prev) => prev.map((l) => (l.id === id ? updated : l)));
      notify(`Leave request ${decision.toLowerCase()}.`, { type: decision === "Approved" ? "success" : "warning" });
    } catch (err) {
      notify(err.message || "Could not record the decision.", { type: "error" });
      load();
    }
  };

  const handleLogCheckin = async (e) => {
    e.preventDefault();
    if (!checkinUuid) return;
    setLogging(true);
    try {
      await logCheckin({ userUuid: checkinUuid, device: checkinDevice });
      const who = employees.find((x) => x.userUuid === checkinUuid);
      notify(`Check-in recorded for ${who?.name || "staff"} via ${checkinDevice}.`, { type: "success" });
      setCheckinOpen(false);
      setCheckinUuid("");
      load();
    } catch (err) {
      notify(err.message || "Could not record the check-in.", { type: "error" });
    } finally {
      setLogging(false);
    }
  };

  const markAttendance = async (row, status) => {
    setClockins((prev) => prev.map((c) => (c.id === row.id ? { ...c, status } : c)));
    try {
      await markStaffAttendance({ userUuid: row.userUuid, workDate: row.workDate, feStatus: status });
      notify(`Attendance marked as ${status}.`, { type: "success" });
      load();
    } catch (err) {
      notify(err.message || "Could not update attendance.", { type: "error" });
      load();
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Employee Attendance & Leave"
        subtitle="Leave approvals, the staff attendance ledger, and biometric / web check-ins"
        breadcrumbs={[{ label: "Dashboard", to: "/hr/dashboard" }, { label: "Attendance & Leave" }]}
        action={
          <Button icon={Fingerprint} onClick={() => setCheckinOpen(true)}>
            Log Biometric / Web Check-in
          </Button>
        }
      />

      <Card>
        <Tabs
          tabs={[
            { key: "leave", label: `Leave Approvals (${leaves.filter((l) => l.status === "Pending").length} Pending)` },
            { key: "attendance", label: "Staff Attendance Ledger" },
            { key: "clockins", label: "Live Biometric / Web Check-ins" },
          ]}
        >
          {(active) => {
            if (active === "leave") {
              return (
                <Table
                  loading={loading}
                  data={leaves}
                  columns={[
                    { key: "employee", header: "Employee / Intern", className: "text-left font-medium text-ink-900" },
                    { key: "type", header: "Leave Type", className: "text-left", render: (r) => <Badge tone="primary">{r.type}</Badge> },
                    { key: "dates", header: "Duration", className: "text-left font-mono text-xs", render: (r) => `${r.from} → ${r.to}` },
                    { key: "reason", header: "Reason & Notes", className: "text-left max-w-xs truncate" },
                    {
                      key: "status",
                      header: "Status",
                      className: "text-left",
                      render: (r) => <Badge tone={r.status === "Approved" ? "success" : r.status === "Pending" ? "warning" : "error"}>{r.status}</Badge>,
                    },
                    {
                      key: "action",
                      header: "",
                      className: "text-right",
                      render: (r) => r.status === "Pending" && (
                        <div className="flex items-center gap-1.5 justify-end">
                          <Button size="sm" icon={Check} onClick={() => decide(r.id, "Approved")}>Approve</Button>
                          <Button size="sm" variant="secondary" icon={X} onClick={() => decide(r.id, "Rejected")}>Reject</Button>
                        </div>
                      ),
                    },
                  ]}
                />
              );
            }

            if (active === "attendance") {
              return (
                <Table
                  loading={loading}
                  data={ledger}
                  emptyTitle="Nothing recorded this month yet"
                  columns={[
                    { key: "name", header: "Staff Member", className: "text-left font-medium text-ink-900" },
                    { key: "department", header: "Department", className: "text-left text-xs" },
                    {
                      key: "breakdown",
                      header: "Present / Late / Absent",
                      className: "text-left font-mono text-xs",
                      render: (r) => `${r.presentDays} / ${r.lateDays} / ${r.absentDays}`,
                    },
                    { key: "onLeaveDays", header: "On Leave", className: "text-left text-xs", render: (r) => `${r.onLeaveDays} d` },
                    {
                      key: "attendancePct",
                      header: "Attendance Rate (this month)",
                      className: "text-left",
                      render: (r) => (
                        <div className="w-36">
                          {r.attendancePct == null ? (
                            <span className="text-xs text-ink-400">—</span>
                          ) : (
                            <ProgressBar value={r.attendancePct} tone={r.attendancePct >= 90 ? "success" : r.attendancePct >= 75 ? "gold" : "danger"} showValue size="sm" />
                          )}
                        </div>
                      ),
                    },
                  ]}
                />
              );
            }

            if (active === "clockins") {
              return (
                <Table
                  loading={loading}
                  data={clockins}
                  emptyTitle="No check-ins in the last 14 days"
                  columns={[
                    { key: "name", header: "Employee Name", className: "text-left font-medium text-ink-900" },
                    { key: "role", header: "Role", className: "text-left text-xs" },
                    { key: "date", header: "Date", className: "text-left" },
                    { key: "checkIn", header: "Clock In", className: "text-left font-mono text-xs" },
                    { key: "checkOut", header: "Clock Out", className: "text-left font-mono text-xs" },
                    { key: "deviceId", header: "Terminal / Channel", className: "text-left font-mono text-xs", render: (r) => <span className="bg-cream-100 text-ink-700 px-2 py-0.5 rounded">{r.deviceId}</span> },
                    {
                      key: "status",
                      header: "Status",
                      className: "text-left",
                      render: (r) => <Badge tone={r.status === "Present" ? "success" : r.status === "Late" ? "warning" : r.status === "On Leave" ? "neutral" : "error"}>{r.status}</Badge>,
                    },
                    {
                      key: "action",
                      header: "Mark Attendance",
                      className: "text-right",
                      render: (r) => (
                        <div className="flex items-center gap-1.5 justify-end">
                          <Button size="sm" variant={r.status === "Present" ? "primary" : "secondary"} icon={Check} onClick={() => markAttendance(r, "Present")} />
                          <Button size="sm" variant={r.status === "Late" ? "primary" : "secondary"} icon={Clock} onClick={() => markAttendance(r, "Late")} />
                          <Button size="sm" variant={r.status === "Absent" ? "danger" : "secondary"} icon={X} onClick={() => markAttendance(r, "Absent")} />
                        </div>
                      ),
                    },
                  ]}
                />
              );
            }
          }}
        </Tabs>
      </Card>

      {/* Biometric / Web Check-in Logger */}
      <Modal
        open={checkinOpen}
        onClose={() => setCheckinOpen(false)}
        title="Biometric / Web Check-in Logger (MSH-FR-HR-03)"
        footer={
          <>
            <Button variant="secondary" onClick={() => setCheckinOpen(false)}>Cancel</Button>
            <Button icon={Fingerprint} loading={logging} onClick={handleLogCheckin}>Log Check-in</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleLogCheckin}>
          <Select
            label="Select Employee / Trainer"
            required
            placeholder="Choose a staff member"
            options={employees.map((e) => ({ value: e.userUuid, label: `${e.name} (${e.designation || e.department})` }))}
            value={checkinUuid}
            onChange={(e) => setCheckinUuid(e.target.value)}
          />
          <Select
            label="Biometric Machine / Web Device"
            options={[
              { value: "BIO-GATE-01", label: "BIO-GATE-01 (Main Floor Turnstile)" },
              { value: "BIO-GATE-02", label: "BIO-GATE-02 (Trainer Lab Wing)" },
              { value: "WEB-AUTH-PORTAL", label: "WEB-AUTH-PORTAL (Remote Web Check-in)" },
            ]}
            value={checkinDevice}
            onChange={(e) => setCheckinDevice(e.target.value)}
          />
        </form>
      </Modal>
    </div>
  );
}
