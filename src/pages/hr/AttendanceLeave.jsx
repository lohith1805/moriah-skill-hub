import { useEffect, useState } from "react";
import { Check, X, CalendarCheck, Clock, Fingerprint, Plus, CheckCircle2, UserCheck, ShieldCheck } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Tabs from "../../components/ui/Tabs";
import Modal from "../../components/ui/Modal";
import ProgressBar from "../../components/ui/ProgressBar";
import { Input, Select } from "../../components/ui/FormField";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { getEmployees, getLeaveRequests, actionLeaveRequest, getClockinLogs, logCheckin } from "../../services/hrService";
import { useToast } from "../../context/ToastContext";

export default function HrAttendanceLeave() {
  const [employees, setEmployees] = useState([]);
  const [leaves, setLeaves] = useState([]);
  const [clockins, setClockins] = useState([]);
  const [loading, setLoading] = useState(true);

  // Check-in simulator modal
  const [checkinOpen, setCheckinOpen] = useState(false);
  const [checkinName, setCheckinName] = useState("");
  const [checkinDevice, setCheckinDevice] = useState("BIO-GATE-01");

  const { notify } = useToast();

  const load = () => {
    setLoading(true);
    Promise.all([
      getEmployees().catch(() => []),
      getLeaveRequests().catch((e) => {
        notify(e.message || "Could not load leave requests.", { type: "error" });
        return [];
      }),
      getClockinLogs().catch(() => []),
    ]).then(([e, l, c]) => {
      setEmployees(e);
      setLeaves(l);
      setClockins(c);
      setLoading(false);
    });
  };

  useEffect(() => {
    load();
  }, []);

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

  const handleSimulateCheckin = async (e) => {
    e.preventDefault();
    if (!checkinName) return;

    await logCheckin({
      name: checkinName,
      deviceId: checkinDevice,
      status: "Present"
    });

    notify(`Biometric check-in recorded for ${checkinName} via ${checkinDevice}.`, { type: "success" });
    setCheckinOpen(false);
    setCheckinName("");
    load();
  };

  const markAttendance = (id, status) => {
    setClockins((prev) => prev.map((c) => (c.id === id ? { ...c, status } : c)));
    try {
      const raw = localStorage.getItem("msh_attendance_logs") || "[]";
      const logs = JSON.parse(raw);
      const idx = logs.findIndex((l) => l.id === id);
      if (idx > -1) {
        logs[idx] = { ...logs[idx], status };
        localStorage.setItem("msh_attendance_logs", JSON.stringify(logs));
      }
    } catch (e) {}
    notify(`Attendance marked as ${status}.`, { type: "success" });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Employee Attendance & Leave"
        subtitle="Leave approvals are live; the attendance ledger and biometric check-ins are still a local demo (no backend endpoint yet)"
        breadcrumbs={[{ label: "Dashboard", to: "/hr/dashboard" }, { label: "Attendance & Leave" }]}
        action={
          <Button icon={Fingerprint} onClick={() => setCheckinOpen(true)}>
            Simulate Biometric / Web Check-in
          </Button>
        }
      />

      <Card>
        <Tabs
          tabs={[
            { key: "leave", label: `Leave Approvals (${leaves.filter(l => l.status === "Pending").length} Pending)` },
            { key: "attendance", label: "Staff Attendance Ledger" },
            { key: "clockins", label: "Live Biometric / Web Check-ins" }
          ]}
        >
          {(active) => {
            if (active === "leave") {
              return (
                <div className="flex flex-col gap-3">
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
                        render: (r) => <Badge tone={r.status === "Approved" ? "success" : r.status === "Pending" ? "warning" : "error"}>{r.status}</Badge>
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
                        )
                      }
                    ]}
                  />
                </div>
              );
            }

            if (active === "attendance") {
              return (
                <Table
                  loading={loading}
                  data={employees}
                  columns={[
                    { key: "name", header: "Staff Member", className: "text-left font-medium text-ink-900" },
                    { key: "role", header: "Role / Track", className: "text-left" },
                    {
                      key: "attendance",
                      header: "Monthly Attendance Rate",
                      className: "text-left",
                      render: (r) => (
                        <div className="w-36">
                          <ProgressBar value={r.attendance} tone={r.attendance >= 90 ? "success" : r.attendance >= 75 ? "gold" : "danger"} showValue size="sm" />
                        </div>
                      )
                    },
                    { key: "leaveBalance", header: "Leave Balance", className: "text-left", render: (r) => <span className="font-semibold text-ink-800">{r.leaveBalance} days</span> },
                    { key: "status", header: "Active Status", className: "text-left", render: (r) => <Badge tone="success">{r.status}</Badge> }
                  ]}
                />
              );
            }

            if (active === "clockins") {
              // Staff-only view — student check-ins are tracked on the
              // Trainer's Standups & Attendance page instead.
              const staffClockins = clockins.filter((c) => (c.role || "").toLowerCase() !== "student");
              return (
                <Table
                  loading={loading}
                  data={staffClockins}
                  columns={[
                    { key: "name", header: "Employee Name", className: "text-left font-medium text-ink-900" },
                    { key: "role", header: "Role", className: "text-left" },
                    { key: "date", header: "Date", className: "text-left" },
                    { key: "checkIn", header: "Clock In", className: "text-left font-mono text-xs", render: (r) => r.checkIn || r.time || "--" },
                    { key: "checkOut", header: "Clock Out", className: "text-left font-mono text-xs", render: (r) => r.checkOut || "--" },
                    { key: "deviceId", header: "Terminal Device", className: "text-left font-mono text-xs", render: (r) => <span className="bg-cream-100 text-ink-700 px-2 py-0.5 rounded">{r.deviceId || "—"}</span> },
                    {
                      key: "status",
                      header: "Status",
                      className: "text-left",
                      render: (r) => <Badge tone={r.status === "Present" ? "success" : r.status === "Late" ? "warning" : "error"}>{r.status}</Badge>
                    },
                    {
                      key: "action",
                      header: "Mark Attendance",
                      className: "text-right",
                      render: (r) => (
                        <div className="flex items-center gap-1.5 justify-end">
                          <Button size="sm" variant={r.status === "Present" ? "primary" : "secondary"} icon={Check} onClick={() => markAttendance(r.id, "Present")} />
                          <Button size="sm" variant={r.status === "Late" ? "primary" : "secondary"} icon={Clock} onClick={() => markAttendance(r.id, "Late")} />
                          <Button size="sm" variant={r.status === "Absent" ? "danger" : "secondary"} icon={X} onClick={() => markAttendance(r.id, "Absent")} />
                        </div>
                      )
                    }
                  ]}
                />
              );
            }
          }}
        </Tabs>
      </Card>

      {/* Biometric / Web Checkin Modal */}
      <Modal
        open={checkinOpen}
        onClose={() => setCheckinOpen(false)}
        title="Biometric / Web Check-in Logger (MSH-FR-HR-03)"
        footer={
          <>
            <Button variant="secondary" onClick={() => setCheckinOpen(false)}>Cancel</Button>
            <Button icon={Fingerprint} onClick={handleSimulateCheckin}>Log Check-in</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleSimulateCheckin}>
          <Select
            label="Select Employee / Trainer"
            required
            options={employees.map((e) => ({ value: e.name, label: `${e.name} (${e.role})` }))}
            value={checkinName}
            onChange={(e) => setCheckinName(e.target.value)}
          />
          <Select
            label="Biometric Machine / Web Device"
            options={[
              { value: "BIO-GATE-01", label: "BIO-GATE-01 (Main Floor Turnstile)" },
              { value: "BIO-GATE-02", label: "BIO-GATE-02 (Trainer Lab Wing)" },
              { value: "WEB-AUTH-PORTAL", label: "WEB-AUTH-PORTAL (Remote Web Check-in)" }
            ]}
            value={checkinDevice}
            onChange={(e) => setCheckinDevice(e.target.value)}
          />
        </form>
      </Modal>
    </div>
  );
}
