import { Link } from "react-router-dom";
import { ShieldAlert } from "lucide-react";
import Button from "../../components/ui/Button";

export default function Unauthorized() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-cream-100 text-center px-4">
      <ShieldAlert className="text-error-500" size={40} />
      <h1 className="font-display text-3xl font-bold text-ink-900 mt-4">Access restricted</h1>
      <p className="text-ink-500 mt-2 max-w-sm">You don't have permission to view this part of the platform.</p>
      <Link to="/login" className="mt-6">
        <Button>Back to Sign In</Button>
      </Link>
    </div>
  );
}
