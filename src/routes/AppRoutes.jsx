import { Routes, Route } from "react-router-dom";
import ProtectedRoute from "./ProtectedRoute";
import AuthLayout from "../components/layout/AuthLayout";
import DashboardLayout from "../components/layout/DashboardLayout";
import { ROLES } from "../utils/constants";

// Public
import Home from "../pages/public/Home";
import VerifyCertificate from "../pages/public/VerifyCertificate";

// Auth
import Login from "../pages/auth/Login";
import Register from "../pages/auth/Register";
import ForgotPassword from "../pages/auth/ForgotPassword";
import ResetPassword from "../pages/auth/ResetPassword";
import VerifyEmail from "../pages/auth/VerifyEmail";
import OAuthCallback from "../pages/auth/OAuthCallback";
import AcceptInvite from "../pages/auth/AcceptInvite";

// Shared
import NotFound from "../pages/shared/NotFound";
import Unauthorized from "../pages/shared/Unauthorized";
import SharedProfile from "../pages/shared/Profile";
import SharedSettings from "../pages/shared/Settings";

// Student
import StudentDashboard from "../pages/student/Dashboard";
import StudentProfile from "../pages/student/Profile";
import StudentSubscription from "../pages/student/Subscription";
import StudentTasks from "../pages/student/Tasks";
import StudentProjects from "../pages/student/Projects";
import StudentSubmissions from "../pages/student/Submissions";
import StudentAssessments from "../pages/student/Assessments";
import StudentLearning from "../pages/student/Learning";
import StudentPipStatus from "../pages/student/PipStatus";
import StudentCertificates from "../pages/student/Certificates";
import StudentResources from "../pages/student/Resources";
import StudentInterviews from "../pages/student/Interviews";
import StudentAttendance from "../pages/student/Attendance";

// Trainer
import TrainerDashboard from "../pages/trainer/Dashboard";
import TrainerBatches from "../pages/trainer/Batches";
import TrainerSprintPlanning from "../pages/trainer/SprintPlanning";
import TrainerStandups from "../pages/trainer/Standups";
import TrainerCodeReview from "../pages/trainer/CodeReview";
import TrainerAssessments from "../pages/trainer/Assessments";
import TrainerAnalytics from "../pages/trainer/Analytics";
import TrainerPipManagement from "../pages/trainer/PIPManagement";
import TrainerGraduation from "../pages/trainer/Graduation";
import TrainerResources from "../pages/trainer/Resources";

// Developer
import DeveloperDashboard from "../pages/developer/Dashboard";
import DeveloperProjects from "../pages/developer/Projects";
import DeveloperClientRequirements from "../pages/developer/ClientRequirements";
import DeveloperBugChallenges from "../pages/developer/BugChallenges";
import DeveloperAssessmentBank from "../pages/developer/AssessmentBank";
import DeveloperVideoLessons from "../pages/developer/VideoLessons";
import DeveloperResources from "../pages/developer/Resources";

// Lead Generator
import LeadGenDashboard from "../pages/leadgen/Dashboard";
import LeadPipeline from "../pages/leadgen/Pipeline";
import LeadCampaigns from "../pages/leadgen/Campaigns";
import LeadTargets from "../pages/leadgen/Targets";

// HR
import HrDashboard from "../pages/hr/Dashboard";
import HrAttendanceLeave from "../pages/hr/AttendanceLeave";
import HrPayroll from "../pages/hr/Payroll";
import HrDocuments from "../pages/hr/Documents";
import HrExitManagement from "../pages/hr/ExitManagement";
import HrOnboarding from "../pages/hr/Onboarding";

// Business Analyst
import BaDashboard from "../pages/ba/Dashboard";
import BaDocuments from "../pages/ba/Documents";
import BaResourcePlanning from "../pages/ba/ResourcePlanning";
import BaClientReview from "../pages/ba/ClientReview";
import BaMeetings from "../pages/ba/Meetings";

// Admin
import AdminDashboard from "../pages/admin/Dashboard";
import AdminUserManagement from "../pages/admin/UserManagement";
import AdminPlans from "../pages/admin/Plans";
import AdminTransactions from "../pages/admin/Transactions";
import AdminAuditLogs from "../pages/admin/AuditLogs";
import AdminReports from "../pages/admin/Reports";

// Client
import ClientDashboard from "../pages/client/Dashboard";
import ClientProjects from "../pages/client/Projects";
import ClientTalentPool from "../pages/client/TalentPool";
import ClientDemos from "../pages/client/Demos";

export default function AppRoutes() {
  return (
    <Routes>
      {/* Public homepage */}
      <Route path="/" element={<Home />} />

      {/* Public / auth routes */}
      <Route element={<AuthLayout />}>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/forgot-password" element={<ForgotPassword />} />
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="/verify-email" element={<VerifyEmail />} />
        <Route path="/auth/oauth/callback" element={<OAuthCallback />} />
        <Route path="/accept-invite" element={<AcceptInvite />} />
      </Route>

      <Route path="/unauthorized" element={<Unauthorized />} />
      <Route path="/verify/:code" element={<VerifyCertificate />} />

      {/* Student */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.STUDENT]} />}>
        <Route element={<DashboardLayout />}>
          <Route path="/student/dashboard" element={<StudentDashboard />} />
          <Route path="/student/profile" element={<StudentProfile />} />
          <Route path="/student/subscription" element={<StudentSubscription />} />
          <Route path="/student/tasks" element={<StudentTasks />} />
          <Route path="/student/attendance" element={<StudentAttendance />} />
          <Route path="/student/projects" element={<StudentProjects />} />
          <Route path="/student/submissions" element={<StudentSubmissions />} />
          <Route path="/student/learning" element={<StudentLearning />} />
          <Route path="/student/assessments" element={<StudentAssessments />} />
          <Route path="/student/pip" element={<StudentPipStatus />} />
          <Route path="/student/certificates" element={<StudentCertificates />} />
          <Route path="/student/resources" element={<StudentResources />} />
          <Route path="/student/interviews" element={<StudentInterviews />} />
          <Route path="/student/settings" element={<SharedSettings />} />
        </Route>
      </Route>

      {/* Trainer / PM */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.TRAINER]} />}>
        <Route element={<DashboardLayout />}>
          <Route path="/trainer/dashboard" element={<TrainerDashboard />} />
          <Route path="/trainer/batches" element={<TrainerBatches />} />
          <Route path="/trainer/sprints" element={<TrainerSprintPlanning />} />
          <Route path="/trainer/standups" element={<TrainerStandups />} />
          <Route path="/trainer/code-review" element={<TrainerCodeReview />} />
          <Route path="/trainer/assessments" element={<TrainerAssessments />} />
          <Route path="/trainer/analytics" element={<TrainerAnalytics />} />
          <Route path="/trainer/pip" element={<TrainerPipManagement />} />
          <Route path="/trainer/graduation" element={<TrainerGraduation />} />
          <Route path="/trainer/resources" element={<TrainerResources />} />
          <Route path="/trainer/profile" element={<SharedProfile />} />
          <Route path="/trainer/settings" element={<SharedSettings />} />
        </Route>
      </Route>

      {/* Developer */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.DEVELOPER]} />}>
        <Route element={<DashboardLayout />}>
          <Route path="/developer/dashboard" element={<DeveloperDashboard />} />
          <Route path="/developer/projects" element={<DeveloperProjects />} />
          <Route path="/developer/client-requirements" element={<DeveloperClientRequirements />} />
          <Route path="/developer/bug-challenges" element={<DeveloperBugChallenges />} />
          <Route path="/developer/video-lessons" element={<DeveloperVideoLessons />} />
          <Route path="/developer/assessment-bank" element={<DeveloperAssessmentBank />} />
          <Route path="/developer/resources" element={<DeveloperResources />} />
          <Route path="/developer/profile" element={<SharedProfile />} />
          <Route path="/developer/settings" element={<SharedSettings />} />
        </Route>
      </Route>

      {/* Lead Generator */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.LEAD_GENERATOR]} />}>
        <Route element={<DashboardLayout />}>
          <Route path="/leads/dashboard" element={<LeadGenDashboard />} />
          <Route path="/leads/pipeline" element={<LeadPipeline />} />
          <Route path="/leads/campaigns" element={<LeadCampaigns />} />
          <Route path="/leads/targets" element={<LeadTargets />} />
          <Route path="/leads/profile" element={<SharedProfile />} />
          <Route path="/leads/settings" element={<SharedSettings />} />
        </Route>
      </Route>

      {/* HR */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.HR]} />}>
        <Route element={<DashboardLayout />}>
          <Route path="/hr/dashboard" element={<HrDashboard />} />
          <Route path="/hr/attendance" element={<HrAttendanceLeave />} />
          <Route path="/hr/payroll" element={<HrPayroll />} />
          <Route path="/hr/documents" element={<HrDocuments />} />
          <Route path="/hr/onboarding" element={<HrOnboarding />} />
          <Route path="/hr/exit" element={<HrExitManagement />} />
          <Route path="/hr/profile" element={<SharedProfile />} />
          <Route path="/hr/settings" element={<SharedSettings />} />
        </Route>
      </Route>

      {/* Business Analyst */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.BUSINESS_ANALYST]} />}>
        <Route element={<DashboardLayout />}>
          <Route path="/ba/dashboard" element={<BaDashboard />} />
          <Route path="/ba/documents" element={<BaDocuments />} />
          <Route path="/ba/resource-planning" element={<BaResourcePlanning />} />
          <Route path="/ba/client-review" element={<BaClientReview />} />
          <Route path="/ba/meetings" element={<BaMeetings />} />
          <Route path="/ba/profile" element={<SharedProfile />} />
          <Route path="/ba/settings" element={<SharedSettings />} />
        </Route>
      </Route>

      {/* Admin */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.ADMIN]} />}>
        <Route element={<DashboardLayout />}>
          <Route path="/admin/dashboard" element={<AdminDashboard />} />
          <Route path="/admin/users" element={<AdminUserManagement />} />
          <Route path="/admin/plans" element={<AdminPlans />} />
          <Route path="/admin/transactions" element={<AdminTransactions />} />
          <Route path="/admin/audit-logs" element={<AdminAuditLogs />} />
          <Route path="/admin/reports" element={<AdminReports />} />
          <Route path="/admin/profile" element={<SharedProfile />} />
          <Route path="/admin/settings" element={<SharedSettings />} />
        </Route>
      </Route>

      {/* Client */}
      <Route element={<ProtectedRoute allowedRoles={[ROLES.CLIENT]} />}>
        <Route element={<DashboardLayout />}>
          <Route path="/client/dashboard" element={<ClientDashboard />} />
          <Route path="/client/projects" element={<ClientProjects />} />
          <Route path="/client/talent-pool" element={<ClientTalentPool />} />
          <Route path="/client/demos" element={<ClientDemos />} />
          <Route path="/client/profile" element={<SharedProfile />} />
          <Route path="/client/settings" element={<SharedSettings />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}