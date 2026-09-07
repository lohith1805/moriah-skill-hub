import { Link } from "react-router-dom";
import { CompassIcon } from "lucide-react";
import Button from "../../components/ui/Button";

export default function NotFound() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-cream-100 text-center px-4">
      <CompassIcon className="text-primary-700" size={40} />
      <h1 className="font-display text-3xl font-bold text-ink-900 mt-4">Page not found</h1>
      <p className="text-ink-500 mt-2 max-w-sm">The page you're looking for doesn't exist or may have been moved.</p>
      <Link to="/login" className="mt-6">
        <Button>Return to Sign In</Button>
      </Link>
    </div>
  );
}
