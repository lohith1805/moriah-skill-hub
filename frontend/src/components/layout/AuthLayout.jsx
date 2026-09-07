import { Outlet, Link } from "react-router-dom";
import { BookOpen, TrendingUp, Trophy } from "lucide-react";
import logoFull from "../../assets/logo-full.png";
import logoMark from "../../assets/logo-mark.png";

/* --------------------------------- Pillars ---------------------------------- */

const PILLARS = [
  { icon: BookOpen, label: "Learn" },
  { icon: TrendingUp, label: "Grow" },
  { icon: Trophy, label: "Succeed" },
];

/* ---------------------------------- Layout ----------------------------------- */

export default function AuthLayout() {
  return (
    <div className="h-screen overflow-hidden grid lg:grid-cols-2 bg-cream-100">
      {/* Hides the browser's own built-in password reveal/clear icon (Edge/IE
          inject one via ::-ms-reveal) so it doesn't stack on top of our
          custom show/hide eye button inside password fields. */}
      <style>{`
        input[type="password"]::-ms-reveal,
        input[type="password"]::-ms-clear {
          display: none;
        }
        input[type="password"]::-webkit-credentials-auto-fill-button,
        input[type="password"]::-webkit-strong-password-auto-fill-button {
          visibility: hidden;
          display: none !important;
          pointer-events: none;
        }
      `}</style>

      {/* Brand panel — height is pinned to the viewport (h-full inside an
          h-screen grid row); the illustration is the only flexible piece,
          so this column can never push the page taller than the screen. */}
      <div className="relative hidden lg:flex flex-col h-full overflow-hidden bg-primary-900 text-white p-10">
        {/* soft ambient glow */}
        <div className="absolute -right-24 -top-24 h-80 w-80 rounded-full bg-gold-500/10" />
        <div className="absolute -left-24 -bottom-24 h-72 w-72 rounded-full bg-gold-500/5" />

        <Link
          to="/"
          className="relative shrink-0 inline-block self-start transition-transform duration-300 hover:-translate-y-0.5"
        >
          <img src={logoMark} alt="Moriah Skill Hub" className="h-28 w-28 xl:h-32 xl:w-32 object-contain" />
        </Link>

        <div className="relative mt-8 max-w-md shrink-0">
          <h1 className="font-display text-3xl xl:text-4xl font-bold leading-tight">
            Where academic theory meets industry{" "}
            <span className="text-gold-400">engineering standards.</span>
          </h1>
          <span className="block h-0.5 w-10 bg-gold-400 mt-5" />
          <p className="mt-4 text-white/60 text-sm leading-relaxed max-w-sm">
            Task-driven, sprint-oriented training combining real client simulations, mentor code reviews,
            and automated performance evaluation — all in one platform.
          </p>
        </div>

        
        <div className="relative shrink-0 grid grid-cols-3 gap-4 max-w-md">
          {PILLARS.map((p) => (
            <div
              key={p.label}
              className="rounded-xl bg-white/5 border border-white/10 py-5 flex flex-col items-center gap-2 transition-colors duration-300 hover:bg-white/10"
            >
              <p.icon size={20} className="text-gold-400" />
              <span className="text-xs font-semibold tracking-wide text-white/80">{p.label}</span>
              <span className="block h-0.5 w-4 bg-gold-400/60" />
            </div>
          ))}
        </div>

        <p className="relative shrink-0 text-xs text-white/35 pt-6">© 2026 Moriah Skill Hub. All rights reserved.</p>
      </div>

      {/* Form panel — scrolls internally if content (e.g. the longer Register
          form) is taller than the screen; the brand panel never moves.
          Note: overflow-y-auto lives on this OUTER plain block, and the
          centering flexbox is a separate INNER child — putting
          justify-center directly on the scrolling element would make the
          top of tall content permanently unreachable when scrolling. */}
      <div className="relative h-full overflow-y-auto bg-cream-100">
        <div className="min-h-full flex flex-col items-center justify-center py-6 sm:py-10 px-0">
          <Link to="/" className="flex lg:hidden items-center gap-2.5 mb-8 self-center transition-transform duration-300 hover:-translate-y-0.5">
            <img src={logoFull} alt="Moriah Skill Hub" className="h-14 w-auto object-contain" />
          </Link>

          <div className="relative w-full px-4 sm:px-8">
            <div className="relative overflow-hidden rounded-2xl bg-white shadow-xl border border-border p-8 sm:p-12">
              {/* decorative corner: dot grid + soft blob, contained to the card */}
              <div className="absolute -right-6 -top-6 h-28 w-28 rounded-full bg-gold-100/70 pointer-events-none" />
              <div
                className="absolute right-6 top-6 h-16 w-20 pointer-events-none opacity-70"
                style={{
                  backgroundImage: "radial-gradient(circle, rgba(212,164,64,0.45) 1.5px, transparent 1.5px)",
                  backgroundSize: "10px 10px",
                }}
              />
              <div className="relative">
                <Outlet />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}