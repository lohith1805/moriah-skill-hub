import { useEffect, useState } from "react";
import { Outlet, Link, useLocation } from "react-router-dom";
import Sidebar from "./Sidebar";
import Header from "./Header";
import { useAuth } from "../../context/AuthContext";
import { GraduationCap, AlertTriangle, UserCheck } from "lucide-react";
import Button from "../ui/Button";
import { getMyBatch } from "../../services/studentService";
import { getMyOnboardingStatus } from "../../services/hrService";

// Staff roles that get an `employees` row (auto-provisioned on invite-accept) and
// are therefore subject to the "HR hasn't confirmed you yet" gate. Admin and HR
// themselves are excluded — HR is who lifts the gate, so gating them is circular.
const GATED_STAFF_ROLES = ["developer", "trainer", "business_analyst", "lead_generator"];
// FE role code -> the URL segment its routes use.
const ROLE_PATH = { trainer: "trainer", developer: "developer", hr: "hr", business_analyst: "ba", lead_generator: "leads" };

export default function DashboardLayout() {
  const { user } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();

  // `/users/me` (and therefore `user`) carries NO batch enrolment, so the old
  // `!user?.batch` check blocked every student forever. Read the real enrolment
  // from GET /api/v1/batches (student-scoped → the batches they're actually in,
  // PLANNED or ACTIVE) and gate on that.
  const isStudent = user?.role === "student";
  const [myBatch, setMyBatch] = useState(null);
  const [batchChecked, setBatchChecked] = useState(!isStudent);

  useEffect(() => {
    if (!isStudent) {
      setBatchChecked(true);
      return;
    }
    let cancelled = false;
    getMyBatch()
      .then((b) => !cancelled && setMyBatch(b))
      .catch(() => {})
      .finally(() => !cancelled && setBatchChecked(true));
    return () => {
      cancelled = true;
    };
  }, [isStudent]);

  const isStudentPendingBatch = isStudent && batchChecked && !myBatch;

  // Staff onboarding gate: a newly-invited staff member can't use their dashboard
  // until HR has filled in their real employee record and approved it (PENDING_HR
  // -> CONFIRMED). Same shape as the student batch gate above.
  const isGatedStaff = GATED_STAFF_ROLES.includes(user?.role);
  const [onbGated, setOnbGated] = useState(false);
  const [onbChecked, setOnbChecked] = useState(!isGatedStaff);

  useEffect(() => {
    if (!isGatedStaff) {
      setOnbChecked(true);
      return;
    }
    let cancelled = false;
    getMyOnboardingStatus()
      .then((s) => !cancelled && setOnbGated(!!s.accessGated))
      .finally(() => !cancelled && setOnbChecked(true));
    return () => {
      cancelled = true;
    };
  }, [isGatedStaff]);

  // Routes that stay reachable while blocked (student batch OR staff onboarding).
  const bypassRoutes = [
    "/student/profile", "/student/settings", "/student/subscription",
    "/profile", "/settings", "/onboarding-documents",
  ];
  const isBypassed = bypassRoutes.some((route) => location.pathname.endsWith(route) || location.pathname.startsWith(route));

  const isStaffPendingOnboarding = isGatedStaff && onbChecked && onbGated;

  const shouldBlock = (isStudentPendingBatch || isStaffPendingOnboarding) && !isBypassed;
  // Don't flash real content (or the pending screen) before the checks resolve.
  const isChecking = ((isStudent && !batchChecked) || (isGatedStaff && !onbChecked)) && !isBypassed;
  const isCheckingBatch = isChecking; // kept name below

  return (
    <div className="flex min-h-screen bg-cream-100">
      <Sidebar role={user?.role} mobileOpen={mobileOpen} onCloseMobile={() => setMobileOpen(false)} />
      <div className="flex-1 min-w-0 flex flex-col lg:pl-64">
        <Header onOpenMobile={() => setMobileOpen(true)} />
        <main className="flex-1 p-4 lg:p-6 max-w-[1400px] w-full mx-auto">
          {isCheckingBatch ? (
            <div className="flex items-center justify-center py-20 text-sm text-ink-400">Loading your dashboard…</div>
          ) : isStaffPendingOnboarding && !isBypassed ? (
            <div className="flex flex-col items-center justify-center py-20 text-center font-sans animate-fade-in">
              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-primary-100 text-primary-800 mb-6">
                <UserCheck size={32} />
              </div>
              <h2 className="font-display text-2xl font-bold text-ink-900">HR is setting up your account</h2>
              <p className="text-sm text-ink-500 mt-3 max-w-md mx-auto">
                Welcome to Moriah Skill Hub! Your staff account is active, but HR still needs to confirm your
                employee record — department, reporting manager and compensation — before your dashboard opens up.
              </p>
              <div className="mt-6 rounded-lg border border-warning-200 bg-warning-50 p-5 text-left text-sm text-warning-800 max-w-lg mx-auto">
                <p className="font-medium text-warning-900 mb-2 flex items-center gap-1.5">
                  <AlertTriangle size={18} className="text-warning-600 shrink-0" />
                  What happens next?
                </p>
                <p className="leading-relaxed">
                  You'll appear in HR's <strong>Pending Employee Records</strong> list. Once they review and
                  approve it, everything unlocks automatically on your next sign-in. You can already start
                  uploading your onboarding documents in the meantime.
                </p>
              </div>
              <div className="mt-8 flex gap-3">
                <Link to={`/${ROLE_PATH[user?.role] || user?.role}/onboarding-documents`}>
                  <Button>Upload Onboarding Documents</Button>
                </Link>
                <Link to={`/${ROLE_PATH[user?.role] || user?.role}/profile`}>
                  <Button variant="secondary">Go to My Profile</Button>
                </Link>
              </div>
            </div>
          ) : shouldBlock ? (
            <div className="flex flex-col items-center justify-center py-20 text-center font-sans animate-fade-in">
              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-gold-100 text-gold-800 animate-bounce mb-6">
                <GraduationCap size={32} />
              </div>
              <h2 className="font-display text-2xl font-bold text-ink-900">Cohort Allocation Pending</h2>
              <p className="text-sm text-ink-500 mt-3 max-w-md mx-auto">
                Welcome to Moriah Skill Hub! You are registered in the <strong className="text-ink-800">{myBatch?.trackCode || user?.track}</strong> track.
              </p>
              <div className="mt-6 rounded-lg border border-warning-200 bg-warning-50 p-5 text-left text-sm text-warning-800 max-w-lg mx-auto">
                <p className="font-medium text-warning-900 mb-2 flex items-center gap-1.5">
                  <AlertTriangle size={18} className="text-warning-600 shrink-0" />
                  What happens next?
                </p>
                <p className="leading-relaxed">
                  A trainer needs to allocate you to an active batch cohort before you can access your sprint board, video lectures, projects, and assessments.
                </p>
                <p className="mt-3 text-xs text-warning-700">
                  Please contact the training operations team or your trainer to get assigned to a cohort.
                </p>
              </div>
              <div className="mt-8 flex gap-3">
                <Link to="/student/profile">
                  <Button variant="secondary">Go to My Profile</Button>
                </Link>
                <Link to="/student/subscription">
                  <Button>View Subscription</Button>
                </Link>
              </div>
            </div>
          ) : (
            <Outlet />
          )}
        </main>
      </div>
    </div>
  );
}
