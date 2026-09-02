import { useEffect, useState } from "react";
import { LogIn, LogOut, CheckCircle2, Clock } from "lucide-react";
import Card from "../ui/Card";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";

const todayStr = () => new Date().toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
const timeNow = () => new Date().toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });

// Self-service clock in/out for any non-student, non-admin, non-client
// role (HR, Trainer, BA, Developer, Lead Gen...). Reads/writes the same
// "msh_attendance_logs" store HR's Attendance & Leave → Live Biometric /
// Web Check-ins and Staff Attendance Ledger tabs already use, so HR sees
// exactly when this person clocked in and out today.
export default function AttendanceCheckinWidget({ role }) {
  const { user } = useAuth();
  const { notify } = useToast();
  const [checkIn, setCheckIn] = useState(null);
  const [checkOut, setCheckOut] = useState(null);
  const [checkingIn, setCheckingIn] = useState(false);
  const [checkingOut, setCheckingOut] = useState(false);

  const load = () => {
    if (!user?.name) return;
    try {
      const raw = localStorage.getItem("msh_attendance_logs") || "[]";
      const logs = JSON.parse(raw);
      const today = todayStr();
      const mine = logs.find((l) => l.name === user.name && l.date === today);
      setCheckIn(mine?.checkIn && mine.checkIn !== "--" ? mine.checkIn : null);
      setCheckOut(mine?.checkOut && mine.checkOut !== "--" ? mine.checkOut : null);
    } catch (e) {
      console.warn("Failed to load today's attendance:", e);
    }
  };

  useEffect(() => {
    load();
  }, [user]);

  const upsert = (patch) => {
    const raw = localStorage.getItem("msh_attendance_logs") || "[]";
    const logs = JSON.parse(raw);
    const today = todayStr();
    const idx = logs.findIndex((l) => l.name === user.name && l.date === today);
    if (idx > -1) {
      logs[idx] = { ...logs[idx], ...patch };
    } else {
      logs.unshift({
        id: `log-${Date.now()}`,
        name: user.name,
        role: role || user.role || "Staff",
        date: today,
        checkIn: null,
        checkOut: "--",
        hours: 0,
        deviceId: "WEB-AUTH-PORTAL",
        status: "Present",
        notes: "Self check-in",
        loggedBy: "self",
        ...patch,
      });
    }
    localStorage.setItem("msh_attendance_logs", JSON.stringify(logs));
  };

  const handleCheckIn = async () => {
    setCheckingIn(true);
    await new Promise((r) => setTimeout(r, 500));
    const timeStr = timeNow();
    const now = new Date();
    const status = now.getHours() > 9 || (now.getHours() === 9 && now.getMinutes() > 15) ? "Late" : "Present";
    upsert({ checkIn: timeStr, status, loggedBy: "self" });
    setCheckIn(timeStr);
    setCheckingIn(false);
    notify(`Checked in at ${timeStr}.`, { type: "success" });
  };

  const handleCheckOut = async () => {
    setCheckingOut(true);
    await new Promise((r) => setTimeout(r, 500));
    const timeStr = timeNow();
    upsert({ checkOut: timeStr });
    setCheckOut(timeStr);
    setCheckingOut(false);
    notify(`Checked out at ${timeStr}.`, { type: "success" });
  };

  return (
    <Card className="border-l-4 border-l-primary-600 bg-gradient-to-br from-cream-50/40 to-white">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h3 className="font-display font-bold text-ink-900 text-base">Today's Attendance</h3>
          <p className="text-xs text-ink-500 mt-1">Clock in and out — HR tracks this on the Attendance & Leave screen.</p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {checkIn ? (
            <Badge tone="success" className="text-sm px-3 py-1 font-semibold flex items-center gap-1">
              <CheckCircle2 size={14} /> Checked In at {checkIn}
            </Badge>
          ) : (
            <span className="text-xs font-semibold text-warning-600 bg-warning-50 px-2.5 py-1 rounded-md border border-warning-100 flex items-center gap-1">
              <Clock size={12} /> Pending Check-in
            </span>
          )}
          {checkOut && (
            <Badge tone="primary" className="text-sm px-3 py-1 font-semibold flex items-center gap-1">
              <CheckCircle2 size={14} /> Checked Out at {checkOut}
            </Badge>
          )}
          {!checkIn && (
            <Button size="sm" icon={LogIn} loading={checkingIn} onClick={handleCheckIn}>Check In</Button>
          )}
          {checkIn && !checkOut && (
            <Button size="sm" variant="secondary" icon={LogOut} loading={checkingOut} onClick={handleCheckOut}>Check Out</Button>
          )}
        </div>
      </div>
    </Card>
  );
}
