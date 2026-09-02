import { forwardRef } from "react";
import { Loader2 } from "lucide-react";
import clsx from "clsx";

const VARIANTS = {
  primary: "bg-primary-700 text-white hover:bg-primary-600 active:bg-primary-800 disabled:bg-primary-300",
  gold: "bg-gold-500 text-primary-900 hover:bg-gold-400 active:bg-gold-600 disabled:bg-gold-200",
  secondary: "bg-white text-primary-700 border border-border hover:bg-cream-100 active:bg-cream-200 disabled:text-ink-400",
  ghost: "bg-transparent text-primary-700 hover:bg-primary-50 active:bg-primary-100 disabled:text-ink-400",
  danger: "bg-error-500 text-white hover:bg-error-600 active:bg-error-600 disabled:bg-error-50 disabled:text-error-500",
};

const SIZES = {
  sm: "h-8 px-3 text-sm gap-1.5",
  md: "h-10 px-4 text-sm gap-2",
  lg: "h-12 px-6 text-base gap-2",
};

const Button = forwardRef(
  ({ children, variant = "primary", size = "md", loading = false, icon: Icon, iconPosition = "left", fullWidth = false, className, disabled, ...props }, ref) => {
    return (
      <button
        ref={ref}
        disabled={disabled || loading}
        className={clsx(
          "inline-flex items-center justify-center whitespace-nowrap rounded-lg font-medium transition-colors duration-150 disabled:cursor-not-allowed",
          VARIANTS[variant],
          SIZES[size],
          fullWidth && "w-full",
          className
        )}
        {...props}
      >
        {loading ? (
          <Loader2 size={16} className="animate-spin" />
        ) : (
          Icon && iconPosition === "left" && <Icon size={16} />
        )}
        {children}
        {!loading && Icon && iconPosition === "right" && <Icon size={16} />}
      </button>
    );
  }
);
Button.displayName = "Button";
export default Button;
