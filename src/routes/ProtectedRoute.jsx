import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { ROLE_HOME } from "../utils/roleAccess";
import LoadingSpinner from "../components/ui/LoadingSpinner";

// Guards a subtree: requires auth, and optionally restricts to a set of roles.
export default function ProtectedRoute({ allowedRoles }) {
  const { user, isAuthenticated, hydrating } = useAuth();
  const location = useLocation();

  // On a hard refresh we may have a token but no user yet — AuthContext is
  // fetching /users/me. Don't bounce to /login until that settles.
  if (hydrating) {
    return (
      <div className="flex min-h-[50vh] items-center justify-center">
        <LoadingSpinner />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to={ROLE_HOME[user.role] || "/login"} replace />;
  }

  return <Outlet />;
}
