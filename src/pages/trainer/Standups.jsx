import { useState } from "react";
import { Check, X, Clock } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import { Select } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";

const ROSTER = [];
const todayStr = () => new Date().toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });

export default function TrainerStandups() {
  const [roster, setRoster] = useState(() => {
    const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
    let students = [];
    if (rawList) {
      try {
        const parsed = JSON.parse(rawList);
        students = parsed.filter((u) => u.role === "student");
      } catch (e) {
        students = [];
      }
    }
    
    // Read today's check-ins from HR database logs
    let todayLogs = [];
    try {
      const rawLogs = localStorage.getItem("msh_attendance_logs");
      if (rawLogs) {
        const parsed = JSON.parse(rawLogs);
        const today = todayStr();
        todayLogs = parsed.filter((l) => l.date === today);
      }
    } catch (e) {
      console.warn("Failed to load today's attendance logs:", e);
    }

    const dynamicRoster = [];
    students.forEach((s) => {
      const checkin = todayLogs.find((l) => l.name.toLowerCase() === s.name.toLowerCase());
      dynamicRoster.push({
        id: s.id,
        name: s.name,
        status: checkin ? checkin.status : "Absent",
        blocker: checkin ? checkin.notes : "No check-in yet",
        checkIn: checkin ? checkin.checkIn || checkin.time : null,
        checkOut: checkin && checkin.checkOut && checkin.checkOut !== "--" ? checkin.checkOut : null
      });
    });
    return dynamicRoster;
  });
  const { notify } = useToast();

  // Shared helper: create or update today's attendance log row for a
  // student with whatever fields are passed in `patch`.
  const upsertLog = (student, patch) => {
    try {
      const rawLogs = localStorage.getItem("msh_attendance_logs") || "[]";
      const logs = JSON.parse(rawLogs);
      const today = todayStr();
      const idx = logs.findIndex((l) => l.date === today && l.name.toLowerCase() === student.name.toLowerCase());
      if (idx > -1) {
        logs[idx] = { ...logs[idx], ...patch };
      } else {
        logs.unshift({
          id: `log-${Date.now()}`,
          name: student.name,
          role: "Student",
          date: today,
          checkIn: null,
          checkOut: "--",
          hours: 0,
          deviceId: "BIO-GATE-01",
          status: "Present",
          notes: "Manually logged by Trainer",
          loggedBy: "trainer",
          ...patch
        });
      }
      localStorage.setItem("msh_attendance_logs", JSON.stringify(logs));
    } catch (e) {
      console.warn("Failed to save manual log:", e);
    }
  };

  const mark = (id, status) => {
    setRoster((prev) => {
      const next = prev.map((r) => (r.id === id ? { ...r, status } : r));
      const student = prev.find((r) => r.id === id);
      if (student) upsertLog(student, { status, checkIn: student.checkIn || new Date().toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" }) });
      return next;
    });
    notify(`Attendance marked as ${status}.`, { type: "success" });
  };

  return (
    <div>
      <PageHeader
        title="Daily Standups & Attendance"
        subtitle="Log blockers and mark attendance for today's standup"
        breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Standups" }]}
        action={<Select options={[{ value: "b1", label: "FS-Batch-14" }, { value: "b2", label: "DA-Batch-07" }]} value="b1" onChange={() => {}} className="w-48" />}
      />

      <Card>
        <CardHeader title="Today's Standup — FS-Batch-14" subtitle={new Date().toLocaleDateString("en-IN", { weekday: "long", day: "2-digit", month: "long" })} />
        <Table
          data={roster}
          columns={[
            { key: "name", header: "Student" },
            { key: "blocker", header: "Blocker Notes" },
            { key: "checkIn", header: "Clock In", className: "font-mono text-xs", render: (r) => r.checkIn || "--" },
            { key: "checkOut", header: "Clock Out", className: "font-mono text-xs", render: (r) => r.checkOut || "--" },
            { key: "status", header: "Status", render: (r) => (
              <Badge tone={r.status === "Present" ? "success" : r.status === "Late" ? "warning" : "error"}>{r.status}</Badge>
            ) },
            { key: "markAttendance", header: "Mark Attendance", render: (r) => (
              <div className="flex items-center gap-1.5">
                <Button size="sm" variant={r.status === "Present" ? "primary" : "secondary"} icon={Check} onClick={() => mark(r.id, "Present")} />
                <Button size="sm" variant={r.status === "Late" ? "primary" : "secondary"} icon={Clock} onClick={() => mark(r.id, "Late")} />
                <Button size="sm" variant={r.status === "Absent" ? "danger" : "secondary"} icon={X} onClick={() => mark(r.id, "Absent")} />
              </div>
            ) },
          ]}
        />
      </Card>
    </div>
  );
}
