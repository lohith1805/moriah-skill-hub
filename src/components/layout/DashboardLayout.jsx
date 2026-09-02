import { useState } from "react";
import { Outlet, Link, useLocation } from "react-router-dom";
import Sidebar from "./Sidebar";
import Header from "./Header";
import { useAuth } from "../../context/AuthContext";
import { GraduationCap, AlertTriangle } from "lucide-react";
import Button from "../ui/Button";

export default function DashboardLayout() {
  const { user } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);
  const location = useLocation();

  const isStudentPendingBatch = user?.role === "student" && !user?.batch;
  
  // Routes that do NOT require a batch assignment
  const bypassRoutes = ["/student/profile", "/student/settings", "/student/subscription"];
  const isBypassed = bypassRoutes.some((route) => location.pathname.startsWith(route));
  
  const shouldBlock = isStudentPendingBatch && !isBypassed;

  return (
    <div className="flex min-h-screen bg-cream-100">
      <Sidebar role={user?.role} mobileOpen={mobileOpen} onCloseMobile={() => setMobileOpen(false)} />
      <div className="flex-1 min-w-0 flex flex-col lg:pl-64">
        <Header onOpenMobile={() => setMobileOpen(true)} />
        <main className="flex-1 p-4 lg:p-6 max-w-[1400px] w-full mx-auto">
          {shouldBlock ? (
            <div className="flex flex-col items-center justify-center py-20 text-center font-sans animate-fade-in">
              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-gold-100 text-gold-800 animate-bounce mb-6">
                <GraduationCap size={32} />
              </div>
              <h2 className="font-display text-2xl font-bold text-ink-900">Cohort Allocation Pending</h2>
              <p className="text-sm text-ink-500 mt-3 max-w-md mx-auto">
                Welcome to Moriah Skill Hub! You are registered in the <strong className="text-ink-800">{user?.track}</strong> track.
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
