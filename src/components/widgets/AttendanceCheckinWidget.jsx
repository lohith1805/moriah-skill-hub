import { useEffect, useState } from "react";
import { LogIn, LogOut, CheckCircle2, Clock } from "lucide-react";
import Card from "../ui/Card";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { getMyStaffAttendanceToday, logCheckin, clockOut } from "../../services/hrService";

// Self-service clock in/out for any staff role (HR, Trainer, BA, Developer,
// Lead Gen). Wired to the real staff-attendance API — POST /hr/attendance/checkin
// and /checkout — so HR sees this on the Attendance & Leave screen. Needs the
// caller to have an employees row; otherwise it shows a quiet "not set up" note.
export default function AttendanceCheckinWidget() {
  const { user } = useAuth();
  const { notify } = useToast();
  const [today, setToday] = useState(null); // { checkIn, checkOut, status } | null
  const [loading, setLoading] = useState(true);
  const [notEnrolled, setNotEnrolled] = useState(false);
  const [checkingIn, setCheckingIn] = useState(false);
  const [checkingOut, setCheckingOut] = useState(false);

  const load = () => {
    setLoading(true);
    getMyStaffAttendanceToday(user?.uuid)
      .then((t) => setToday(t))
      .catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (user) load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const handleCheckIn = async () => {
    setCheckingIn(true);
    try {
      const res = await logCheckin({ device: "WEB-AUTH-PORTAL" });
      notify(`Checked in — marked ${res.status}.`, { type: "success" });
      load();
    } catch (err) {
      if (err?.status === 404) {
        setNotEnrolled(true);
      } else {
        notify(err?.message || "Couldn't check in.", { type: "error" });
      }
    } finally {
      setCheckingIn(false);
    }
  };

  const handleCheckOut = async () => {
    setCheckingOut(true);
    try {
      await clockOut();
      notify("Checked out.", { type: "success" });
      load();
    } catch (err) {
      notify(err?.message || "Couldn't check out.", { type: "error" });
    } finally {
      setCheckingOut(false);
    }
  };

  if (notEnrolled) {
    return (
      <Card className="border-l-4 border-l-ink-300 bg-cream-50/40">
        <h3 className="font-display font-bold text-ink-900 text-base">Today's Attendance</h3>
        <p className="text-xs text-ink-500 mt-1">
          No employee record is linked to your account yet — ask HR to add you before you can clock in.
        </p>
      </Card>
    );
  }

  return (
    <Card className="border-l-4 border-l-primary-600 bg-gradient-to-br from-cream-50/40 to-white">
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h3 className="font-display font-bold text-ink-900 text-base">Today's Attendance</h3>
          <p className="text-xs text-ink-500 mt-1">Clock in and out — HR tracks this on the Attendance &amp; Leave screen.</p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          {today?.checkIn ? (
            <Badge tone={today.status === "Late" ? "warning" : "success"} className="text-sm px-3 py-1 font-semibold flex items-center gap-1">
              <CheckCircle2 size={14} /> Checked In at {today.checkIn}{today.status === "Late" ? " (Late)" : ""}
            </Badge>
          ) : (
            <span className="text-xs font-semibold text-warning-600 bg-warning-50 px-2.5 py-1 rounded-md border border-warning-100 flex items-center gap-1">
              <Clock size={12} /> Pending Check-in
            </span>
          )}
          {today?.checkOut && (
            <Badge tone="primary" className="text-sm px-3 py-1 font-semibold flex items-center gap-1">
              <CheckCircle2 size={14} /> Checked Out at {today.checkOut}
            </Badge>
          )}
          {!loading && !today?.checkIn && (
            <Button size="sm" icon={LogIn} loading={checkingIn} onClick={handleCheckIn}>Check In</Button>
          )}
          {!loading && today?.checkIn && !today?.checkOut && (
            <Button size="sm" variant="secondary" icon={LogOut} loading={checkingOut} onClick={handleCheckOut}>Check Out</Button>
          )}
        </div>
      </div>
    </Card>
  );
}
