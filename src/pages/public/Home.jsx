import { useState, useEffect, useRef } from "react";
import { Link } from "react-router-dom";
import logoFull from "../../assets/logo-full.png";
import logoMark from "../../assets/logo-mark.png";
import heroSlide1 from "../../assets/image-1.png";
import heroSlide2 from "../../assets/image-2.png";
import heroSlide3 from "../../assets/image-3.png";

import {
  ArrowRight,
  Check,
  Menu,
  X,
  GraduationCap,
  Code2,
  Users,
  Briefcase,
  ClipboardCheck,
  LineChart,
  ShieldCheck,
  Star,
  Quote,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Mail,
  Rocket,
  MapPin,
  Phone,
  Clock,
} from "lucide-react";
import Button from "../../components/ui/Button";
import Badge from "../../components/ui/Badge";
import { SUBSCRIPTION_PLANS, CURRENCY } from "../../utils/constants";
import { getPublicStats, getPublicPlans, submitInboundLead } from "../../services/siteService";

const NAV_LINKS = [
  { label: "Home", href: "#" },
  { label: "How it works", href: "#how-it-works" },
  { label: "Pricing", href: "#pricing" },
  { label: "Contact", href: "#contact" },
];

const STATS = [
  { value: "12,000+", label: "Learners trained" },
  { value: "480+", label: "Hiring partners" },
  { value: "86%", label: "Placement rate" },
  { value: "4.8/5", label: "Average rating" },
];

const HERO_SLIDES = [heroSlide1, heroSlide2, heroSlide3];

const FEATURES = [
  {
    icon: Briefcase,
    title: "Real client simulations",
    desc: "Work sprint-by-sprint on live-style briefs modeled on actual client engagements, not toy exercises.",
  },
  {
    icon: ClipboardCheck,
    title: "Mentor code reviews",
    desc: "Every submission is reviewed by a trainer with structured, actionable feedback — not just a pass/fail grade.",
  },
  {
    icon: LineChart,
    title: "Automated performance tracking",
    desc: "Standups, sprint velocity, and quiz scores roll up into a single performance dashboard you and your mentor both see.",
  },
  {
    icon: ShieldCheck,
    title: "Structured accountability",
    desc: "Clear attendance and delivery expectations, with an early-warning PIP process so no one falls through the cracks.",
  },
  {
    icon: GraduationCap,
    title: "Verified certificates",
    desc: "Graduate with a certificate tied to the tasks you actually shipped, ready to share with hiring partners.",
  },
  {
    icon: Users,
    title: "Direct hiring pipeline",
    desc: "Top performers are surfaced to our corporate client talent pool for interviews and live project demos.",
  },
];

const STEPS = [
  { title: "Create your account", desc: "Sign up as a student and tell us your track and availability.", icon: Users },
  { title: "Pick a plan", desc: "Choose the subscription tier that matches how deep you want to go.", icon: ClipboardCheck },
  { title: "Ship sprint tasks", desc: "Take on tasks, submit work, and get reviewed by a mentor every cycle.", icon: Code2 },
  { title: "Graduate & get placed", desc: "Earn your certificate and enter the talent pool for client interviews.", icon: GraduationCap },
];

const TESTIMONIALS = [
  {
    quote: "The sprint structure forced me to work like I was already on a real team. My code reviews were tougher than my first job's.",
    name: "Aditi R.",
    role: "Software Developer track",
  },
  {
    quote: "The PIP system felt strict at first, but it's the reason I never let a sprint slip. It kept me accountable end to end.",
    name: "Karthik S.",
    role: "Business Analyst track",
  },
  {
    quote: "Getting matched into the client talent pool after graduation was the whole reason I signed up. Worth every rupee.",
    name: "Meera P.",
    role: "Developer track graduate",
  },
];

const FAQS = [
  {
    q: "Who is Moriah Skill Hub for?",
    a: "Students and early-career professionals who want structured, deadline-driven training across development, business analysis, lead generation, and related tracks — with real mentor feedback instead of self-paced videos.",
  },
  {
    q: "Can I change my subscription plan later?",
    a: "Yes. You can switch tiers any time from your student dashboard; billing is prorated for the remainder of your current cycle.",
  },
  {
    q: "What happens if I miss deadlines?",
    a: "Our Performance Improvement Plan (PIP) system flags attendance defaults, sprint delays, and missed submissions early, with clear thresholds so you always know where you stand before it becomes a problem.",
  },
  {
    q: "Do I get a certificate at the end?",
    a: "Yes — certificates are tied to the sprints and tasks you actually completed, and top graduates are surfaced directly to our corporate client talent pool.",
  },
];

const INQUIRY_TYPES = ["General question", "Enrollment", "Corporate hiring partner", "Billing support"];

/* ------------------------------ Section heading ---------------------------- */
/* Shared so every section title matches in size, weight and alignment. */

function SectionHeading({ eyebrow, title, subtitle, theme = "light", align = "left" }) {
  const eyebrowColor = theme === "dark" ? "text-gold-400" : "text-primary-600";
  const titleColor = theme === "dark" ? "text-white" : "text-ink-900";
  const subtitleColor = theme === "dark" ? "text-white/60" : "text-ink-500";
  return (
    <div className={"max-w-2xl " + (align === "center" ? "mx-auto text-center" : "")}>
      <p className={"text-xs sm:text-sm font-semibold uppercase tracking-widest " + eyebrowColor}>{eyebrow}</p>
      <h2 className={"font-display text-3xl sm:text-4xl font-bold mt-2 leading-tight " + titleColor}>{title}</h2>
      {subtitle && <p className={"mt-3 text-base " + subtitleColor}>{subtitle}</p>}
    </div>
  );
}

/* Diagonal shine sweep used on hoverable cards. Parent needs `group relative overflow-hidden`. */
function Shine({ tone = "light" }) {
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      <div
        className={
          "absolute -inset-y-full -left-1/2 w-1/3 rotate-12 -translate-x-[250%] transition-transform duration-700 ease-out group-hover:translate-x-[250%] " +
          (tone === "dark" ? "bg-gradient-to-r from-transparent via-white/10 to-transparent" : "bg-gradient-to-r from-transparent via-white/60 to-transparent")
        }
      />
    </div>
  );
}

/* ---------------------------------- Nav ---------------------------------- */

function NavBar() {
  const [open, setOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8);
    window.addEventListener("scroll", onScroll);
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  return (
    <header
      className={
        "sticky top-0 z-40 bg-cream-50/90 backdrop-blur border-b transition-shadow duration-300 " +
        (scrolled ? "border-border shadow-sm" : "border-transparent")
      }
    >
      <div className="max-w-7xl mx-auto px-6 py-4 flex items-center justify-between gap-4">
        <Link to="#" className="flex items-center shrink-0 transition-transform duration-300 hover:scale-[1.03] hover:-rotate-1">
          <img src={logoFull} alt="Moriah Skill Hub Logo" className="h-14 w-55 object-contain" />
        </Link>

        <nav className="hidden md:flex items-center gap-8">
          {NAV_LINKS.map((l) => (
            <a
              key={l.href}
              href={l.href}
              className="relative text-sm font-medium text-ink-700 transition-colors duration-200 hover:text-primary-700 after:absolute after:left-0 after:-bottom-1 after:h-[2px] after:w-0 after:bg-gold-500 after:transition-all after:duration-300 hover:after:w-full"
            >
              {l.label}
            </a>
          ))}
        </nav>

        <div className="hidden md:flex items-center gap-3">
          <Link to="/login">
            <Button variant="ghost" size="md">Sign in</Button>
          </Link>
          <Link to="/register" className="group">
            <Button variant="primary" size="md" icon={ArrowRight} iconPosition="right" className="transition-transform duration-300 group-hover:-translate-y-0.5 group-hover:shadow-lg">
              Get started
            </Button>
          </Link>
        </div>

        <button
          className="md:hidden p-2 text-ink-700 transition-transform duration-200 active:scale-90"
          onClick={() => setOpen((v) => !v)}
          aria-label="Toggle menu"
        >
          <span className="inline-block transition-transform duration-300" style={{ transform: open ? "rotate(90deg)" : "rotate(0deg)" }}>
            {open ? <X size={22} /> : <Menu size={22} />}
          </span>
        </button>
      </div>

      <div
        className={
          "md:hidden overflow-hidden transition-all duration-300 ease-in-out border-t border-border bg-cream-50 " +
          (open ? "max-h-96 opacity-100" : "max-h-0 opacity-0 border-t-0")
        }
      >
        <div className="px-6 py-4 flex flex-col gap-4">
          {NAV_LINKS.map((l) => (
            <a key={l.href} href={l.href} className="text-sm font-medium text-ink-700" onClick={() => setOpen(false)}>
              {l.label}
            </a>
          ))}
          <div className="flex flex-col gap-2 pt-2 border-t border-border">
            <Link to="/login"><Button variant="secondary" fullWidth>Sign in</Button></Link>
            <Link to="/register"><Button variant="primary" fullWidth icon={ArrowRight} iconPosition="right">Get started</Button></Link>
          </div>
        </div>
      </div>
    </header>
  );
}

/* --------------------------------- Hero ---------------------------------- */
/* Compact hero, image-only slider (no captions). */

function HeroSlider() {
  const [active, setActive] = useState(0);
  const timerRef = useRef(null);

  useEffect(() => {
    timerRef.current = setInterval(() => setActive((i) => (i + 1) % HERO_SLIDES.length), 4500);
    return () => clearInterval(timerRef.current);
  }, []);

  const goTo = (i) => {
    clearInterval(timerRef.current);
    setActive(i);
    timerRef.current = setInterval(() => setActive((v) => (v + 1) % HERO_SLIDES.length), 4500);
  };

  const prev = () => goTo((active - 1 + HERO_SLIDES.length) % HERO_SLIDES.length);
  const next = () => goTo((active + 1) % HERO_SLIDES.length);

  return (
    <div className="relative max-w-sm mx-auto lg:max-w-none">
      <div className="group relative p-3 sm:p-4 overflow-hidden">
        <div className="relative aspect-[4/3] sm:aspect-square lg:aspect-[4/3]">
          {HERO_SLIDES.map((src, i) => (
            <div
              key={src}
              className={
                "absolute inset-0 flex items-center justify-center transition-all duration-700 ease-out " +
                (i === active
                  ? "opacity-100 translate-x-0 scale-100"
                  : i < active
                    ? "opacity-0 -translate-x-6 scale-95 pointer-events-none"
                    : "opacity-0 translate-x-6 scale-95 pointer-events-none")
              }
            >
              <img
                src={src}
                alt=""
                className="w-full h-full object-contain drop-shadow-2xl animate-float transition-transform duration-500 group-hover:scale-105"
              />
            </div>
          ))}
        </div>
      </div>


      <div className="flex justify-center gap-2 mt-4">
        {HERO_SLIDES.map((src, i) => (
          <button
            key={src}
            onClick={() => goTo(i)}
            aria-label={`Go to slide ${i + 1}`}
            className={
              "h-2 rounded-full transition-all duration-300 " +
              (i === active ? "w-7 bg-gold-400" : "w-2 bg-white/30 hover:bg-white/50")
            }
          />
        ))}
      </div>
    </div>
  );
}

function Hero() {
  const [stats, setStats] = useState(null);
  useEffect(() => {
    getPublicStats().then(setStats).catch(() => {});
  }, []);
  const bands = stats
    ? [
        { value: `${(stats.graduates + stats.activeLearners).toLocaleString("en-IN")}+`, label: "Learners on the platform" },
        { value: `${stats.activeBatches}`, label: "Active batches" },
        { value: `${stats.certificatesIssued.toLocaleString("en-IN")}+`, label: "Certificates issued" },
        { value: `${stats.hiringPartners}+`, label: "Hiring partners" },
      ]
    : STATS;
  return (
    <section className="relative overflow-hidden bg-primary-800 text-white">
      <div className="absolute -right-24 -top-24 h-80 w-80 rounded-full bg-gold-500/10 animate-pulse-slow" />
      <div className="absolute -left-24 bottom-0 h-60 w-60 rounded-full bg-white/5" />
      <div className="relative max-w-7xl mx-auto px-6 lg:px-8 py-10 lg:py-14 grid lg:grid-cols-2 gap-10 items-center">
        <div className="animate-fade-up">
          <Badge tone="gold" className="mb-4">Learn · Grow · Succeed</Badge>
          <h1 className="font-display text-3xl sm:text-4xl lg:text-5xl font-bold leading-tight">
            Where academic theory meets industry engineering standards.
          </h1>
          <p className="mt-4 text-white/70 text-base leading-relaxed max-w-lg">
            Task-driven, sprint-oriented training combining real client simulations, mentor code reviews,
            and automated performance evaluation — all in one platform.
          </p>
          <div className="mt-7 flex flex-wrap items-center gap-3">
            <Link to="/register" className="group">
              <Button variant="gold" size="lg" icon={ArrowRight} iconPosition="right" className="transition-all duration-300 group-hover:-translate-y-0.5 group-hover:shadow-xl group-hover:shadow-gold-500/20">
                Create your account
              </Button>
            </Link>
            <a href="#pricing" className="group">
              <Button variant="secondary" size="lg" className="bg-white/10 border-white/20 text-white transition-all duration-300 hover:bg-white/20 group-hover:-translate-y-0.5">
                View subscription plans
              </Button>
            </a>
          </div>
        </div>

        <div className="animate-fade-up [animation-delay:150ms]">
          <HeroSlider />
        </div>
      </div>

      <div className="relative border-t border-white/10 bg-primary-900/60">
        <div className="max-w-7xl mx-auto px-6 lg:px-8 py-6 grid grid-cols-2 sm:grid-cols-4 gap-6">
          {bands.map((s) => (
            <div key={s.label} className="group text-center sm:text-left cursor-default">
              <p className="font-display text-2xl font-bold text-gold-400 transition-transform duration-300 group-hover:scale-110 origin-left inline-block">
                {s.value}
              </p>
              <p className="text-xs text-white/60 mt-1 transition-colors duration-300 group-hover:text-white/90">{s.label}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* ------------------------------ How it works ------------------------------ */

function HowItWorks() {
  return (
    <section id="how-it-works" className="max-w-7xl mx-auto px-6 lg:px-8 py-20">
      <SectionHeading
        eyebrow="How it works"
        title="From sign-up to placement, in four steps"
        subtitle="A single guided path — no piecing together random courses."
      />

      <div className="mt-12 grid sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {STEPS.map((step, i) => (
          <div
            key={step.title}
            className="group relative rounded-xl border border-border bg-white p-6 shadow-card overflow-hidden transition-all duration-300 hover:-translate-y-2 hover:shadow-xl hover:border-gold-300"
          >
            <Shine />
            {i < STEPS.length - 1 && (
              <ArrowRight
                size={16}
                className="hidden lg:block absolute -right-4 top-8 text-gold-400 opacity-0 -translate-x-2 group-hover:opacity-100 group-hover:translate-x-0 transition-all duration-300"
              />
            )}
            <div className="relative flex items-center justify-between">
              <span className="font-display text-3xl font-bold text-gold-500 transition-all duration-300 group-hover:scale-125 group-hover:drop-shadow-[0_0_10px_rgba(212,164,64,0.5)] origin-left inline-block">
                {String(i + 1).padStart(2, "0")}
              </span>
              <div className="h-9 w-9 rounded-full bg-primary-50 flex items-center justify-center text-primary-600 transition-all duration-300 group-hover:bg-primary-700 group-hover:text-white group-hover:rotate-12 group-hover:scale-110">
                <step.icon size={16} />
              </div>
            </div>
            <h3 className="relative font-display font-semibold text-ink-900 mt-3">{step.title}</h3>
            <p className="relative text-sm text-ink-500 mt-2 leading-relaxed">{step.desc}</p>
          </div>
        ))}
      </div>
    </section>
  );
}

/* --------------------------------- Features -------------------------------- */

function Features() {
  return (
    <section className="bg-cream-100 border-y border-border">
      <div className="max-w-7xl mx-auto px-6 lg:px-8 py-20">
        <SectionHeading eyebrow="Why Moriah" title="Built like a real engineering org, not a course platform" />

        <div className="mt-12 grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {FEATURES.map((f) => (
            <div
              key={f.title}
              className="group relative rounded-xl border border-border bg-white p-6 shadow-card overflow-hidden transition-all duration-300 hover:-translate-y-2 hover:shadow-xl hover:border-primary-200"
            >
              <Shine />
              <div className="relative h-11 w-11 rounded-lg bg-primary-50 flex items-center justify-center text-primary-700 transition-all duration-500 group-hover:bg-primary-700 group-hover:text-white group-hover:rotate-[18deg] group-hover:scale-110">
                <f.icon size={20} />
              </div>
              <h3 className="relative font-display font-semibold text-ink-900 mt-4 transition-colors duration-300 group-hover:text-primary-700">
                {f.title}
              </h3>
              <p className="relative text-sm text-ink-500 mt-2 leading-relaxed">{f.desc}</p>
              <span className="relative block h-0.5 w-0 bg-gold-400 mt-4 transition-all duration-500 group-hover:w-10" />
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

/* --------------------------------- Pricing --------------------------------- */
/* Deep navy, gold accents — the "premium plan" surface. */

function Pricing() {
  const highlighted = "PROJECT_BASED";
  const [plans, setPlans] = useState(null);
  useEffect(() => {
    getPublicPlans().then(setPlans).catch(() => {});
  }, []);
  const planList = plans && plans.length ? plans : SUBSCRIPTION_PLANS;
  return (
    <section id="pricing" className="bg-primary-900 text-white">
      <div className="max-w-7xl mx-auto px-6 lg:px-8 py-20">
        <SectionHeading
          theme="dark"
          eyebrow="Pricing"
          title="Subscription plans for every stage"
          subtitle="Start on Starter, and upgrade any time from your dashboard as you're ready to go deeper."
        />

        <div className="mt-12 grid sm:grid-cols-2 lg:grid-cols-5 gap-5">
          {planList.map((plan) => {
            const isHighlighted = (plan.code || "").toUpperCase() === highlighted;
            return (
              <div
                key={plan.code}
                className={
                  "group relative rounded-xl p-6 flex flex-col overflow-hidden transition-all duration-300 hover:-translate-y-2 " +
                  (isHighlighted
                    ? "bg-white text-ink-900 ring-2 ring-gold-400 shadow-xl hover:shadow-2xl hover:shadow-gold-500/20"
                    : "bg-white/5 border border-white/10 text-white hover:bg-white/10 hover:border-gold-400/40")
                }
              >
                <Shine tone={isHighlighted ? "light" : "dark"} />
                {isHighlighted && <Badge tone="gold" className="relative mb-3 self-start animate-pulse-slow">Most popular</Badge>}
                <h3 className="relative font-display font-semibold">{plan.name}</h3>
                <p className={"relative text-xs mt-0.5 " + (isHighlighted ? "text-ink-500" : "text-white/50")}>{plan.model}</p>
                <p className="relative font-display text-2xl font-bold mt-4 transition-transform duration-300 group-hover:scale-105 origin-left">
                  {CURRENCY(plan.price)}
                </p>

                <ul className="relative flex flex-col gap-2 mt-6 flex-1 text-xs">
                  {(plan.features || []).map((feature) => (
                    <li key={feature} className="flex items-start gap-2">
                      <Check size={14} className={isHighlighted ? "text-primary-700 mt-0.5 shrink-0" : "text-gold-400 mt-0.5 shrink-0"} />
                      <span className={isHighlighted ? "text-ink-700" : "text-white/80"}>{feature}</span>
                    </li>
                  ))}
                </ul>

                <Link to="/register" className="relative mt-6">
                  <Button
                    variant={isHighlighted ? "primary" : "ghost"}
                    size="sm"
                    fullWidth
                    className={
                      (isHighlighted ? "" : "border border-white/20 text-white hover:bg-white/10 ") +
                      "transition-transform duration-200 group-hover:scale-[1.03]"
                    }
                  >
                    Get started
                  </Button>
                </Link>
              </div>
            );
          })}
        </div>

        <p className="text-center text-white/40 text-xs mt-8">
          Already enrolled? <Link to="/login" className="text-gold-400 hover:underline">Sign in</Link> and manage your plan from Subscription &amp; Billing.
        </p>
      </div>
    </section>
  );
}

/* ------------------------------- Testimonials ------------------------------ */
/* Light spotlight carousel — deliberately different from Pricing's dark surface. */

function Testimonials() {
  const [active, setActive] = useState(0);

  useEffect(() => {
    const id = setInterval(() => setActive((i) => (i + 1) % TESTIMONIALS.length), 3200);
    return () => clearInterval(id);
  }, []);

  return (
    <section className="bg-white">
      <div className="max-w-7xl mx-auto px-6 lg:px-8 py-20">
        <SectionHeading eyebrow="Learner voices" title="What learners say after graduating" />

        <div className="mt-12 grid sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {TESTIMONIALS.map((t, i) => {
            const initials = t.name.split(" ").map((p) => p[0]).join("");
            const isActive = active === i;
            return (
              <div
                key={t.name}
                onMouseEnter={() => setActive(i)}
                className={
                  "group relative rounded-xl border bg-white p-7 overflow-hidden transition-all duration-500 hover:-translate-y-2 " +
                  (isActive
                    ? "border-gold-300 shadow-xl -translate-y-1"
                    : "border-border shadow-card hover:shadow-xl hover:border-primary-200")
                }
              >
                {/* top accent bar */}
                <span
                  className={
                    "absolute top-0 left-0 h-1 bg-gradient-to-r from-gold-400 to-gold-500 transition-all duration-500 " +
                    (isActive ? "w-full" : "w-10 group-hover:w-full")
                  }
                />

                {/* watermark quote */}
                <Quote
                  size={64}
                  className={
                    "absolute -top-2 -right-2 transition-all duration-500 " +
                    (isActive ? "text-gold-100 rotate-0" : "text-cream-100 rotate-6 group-hover:text-gold-100 group-hover:rotate-0")
                  }
                />

                <div className="relative flex gap-0.5 text-gold-500">
                  {Array.from({ length: 5 }).map((_, s) => (
                    <Star
                      key={s}
                      size={14}
                      fill="currentColor"
                      strokeWidth={0}
                      className="transition-transform duration-300 group-hover:scale-125"
                      style={{ transitionDelay: `${s * 40}ms` }}
                    />
                  ))}
                </div>

                <p className="relative text-sm text-ink-700 mt-4 leading-relaxed">"{t.quote}"</p>

                <div className="relative flex items-center gap-3 mt-6 pt-5 border-t border-border">
                  <div
                    className={
                      "h-10 w-10 shrink-0 rounded-full flex items-center justify-center font-display font-semibold text-xs transition-all duration-500 " +
                      (isActive ? "bg-primary-700 text-white scale-110 ring-2 ring-gold-300 ring-offset-2" : "bg-primary-50 text-primary-700")
                    }
                  >
                    {initials}
                  </div>
                  <div>
                    <p className="text-sm font-semibold text-ink-900">{t.name}</p>
                    <p className="text-xs text-ink-500">{t.role}</p>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}
/* ----------------------------------- FAQ ----------------------------------- */

function FAQ() {
  const [items, setItems] = useState([]);
  const [openIdx, setOpenIdx] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [newQ, setNewQ] = useState("");
  const [newA, setNewA] = useState("");
  const { notify } = useToast();

  useEffect(() => {
    const saved = localStorage.getItem("msh_public_faqs");
    if (saved) {
      setItems(JSON.parse(saved));
    } else {
      localStorage.setItem("msh_public_faqs", JSON.stringify(FAQS));
      setItems(FAQS);
    }
  }, []);

  const handleAddSave = (e) => {
    e.preventDefault();
    if (!newQ.trim() || !newA.trim()) return;
    const newItem = { q: newQ.trim(), a: newA.trim() };
    const updated = [...items, newItem];
    setItems(updated);
    localStorage.setItem("msh_public_faqs", JSON.stringify(updated));
    setNewQ("");
    setNewA("");
    setFormOpen(false);
    notify("New FAQ item added successfully.", { type: "success" });
  };

  const handleDelete = (index, event) => {
    event.stopPropagation();
    const target = items[index];
    const updated = items.filter((_, idx) => idx !== index);
    setItems(updated);
    localStorage.setItem("msh_public_faqs", JSON.stringify(updated));
    if (openIdx === index) {
      setOpenIdx(-1);
    }
    notify(`FAQ item "${target?.q}" deleted.`, { type: "warning" });
  };

  return (
    <section id="faq" className="bg-cream-100 border-y border-border">
      <div className="max-w-7xl mx-auto px-6 lg:px-8 py-20">
        <div className="flex items-center justify-between gap-4 flex-wrap border-b border-border pb-6">
          <SectionHeading eyebrow="FAQ" title="Frequently asked questions" />
          <Button size="sm" icon={Plus} onClick={() => { setNewQ(""); setNewA(""); setFormOpen(!formOpen); }}>
            {formOpen ? "Close Form" : "Add FAQ"}
          </Button>
        </div>

        {/* Add FAQ Inline Form */}
        {formOpen && (
          <div className="mt-6 p-6 rounded-xl border border-gold-300 bg-cream-50 max-w-3xl text-left shadow-md">
            <h4 className="text-sm font-semibold text-ink-900 mb-3">Add New Frequently Asked Question</h4>
            <form onSubmit={handleAddSave} className="flex flex-col gap-3">
              <input
                required
                type="text"
                placeholder="Enter FAQ Question..."
                value={newQ}
                onChange={(e) => setNewQ(e.target.value)}
                className="w-full rounded-lg border border-border px-4 py-2.5 text-sm text-ink-900 bg-white outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100"
              />
              <textarea
                required
                placeholder="Enter FAQ Answer..."
                rows={3}
                value={newA}
                onChange={(e) => setNewA(e.target.value)}
                className="w-full rounded-lg border border-border px-4 py-2.5 text-sm text-ink-900 bg-white outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 resize-none"
              />
              <div className="flex gap-2">
                <Button type="submit" size="sm">Save FAQ</Button>
                <Button size="sm" variant="secondary" onClick={() => setFormOpen(false)}>Cancel</Button>
              </div>
            </form>
          </div>
        )}

        <div className="mt-10 flex flex-col gap-3 max-w-3xl">
          {items.map((f, i) => {
            const isOpen = openIdx === i;
            return (
              <div
                key={i}
                className={
                  "relative rounded-xl border bg-white shadow-card overflow-hidden transition-all duration-300 " +
                  (isOpen ? "border-gold-300 shadow-lg" : "border-border hover:border-primary-200")
                }
              >
                <span
                  className={
                    "absolute left-0 top-0 bottom-0 w-1 bg-gold-400 transition-all duration-300 " +
                    (isOpen ? "opacity-100" : "opacity-0")
                  }
                />
                <div className="w-full flex items-center justify-between gap-4 p-5 pl-6 text-left transition-colors duration-200 hover:bg-cream-50">
                  <button
                    className="flex-1 text-left cursor-pointer"
                    onClick={() => setOpenIdx(isOpen ? -1 : i)}
                  >
                    <span className={"font-display font-semibold text-sm transition-colors duration-300 " + (isOpen ? "text-primary-700" : "text-ink-900")}>
                      {f.q}
                    </span>
                  </button>
                  <div className="flex items-center gap-3 shrink-0">
                    <button
                      onClick={(e) => handleDelete(i, e)}
                      className="text-ink-400 hover:text-error-600 p-1.5 rounded hover:bg-neutral-100 transition-colors cursor-pointer"
                      title="Delete FAQ"
                    >
                      <Trash2 size={15} />
                    </button>
                    <button
                      onClick={() => setOpenIdx(isOpen ? -1 : i)}
                      className="text-ink-400 hover:text-ink-700 p-1 cursor-pointer"
                    >
                      <ChevronDown
                        size={18}
                        className={"transition-transform duration-300 " + (isOpen ? "rotate-180 text-gold-500" : "")}
                      />
                    </button>
                  </div>
                </div>
                <div className={"grid transition-all duration-300 ease-in-out " + (isOpen ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0")}>
                  <div className="overflow-hidden">
                    <p className="px-6 pb-5 text-sm text-ink-500 leading-relaxed">{f.a}</p>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </section>
  );
}

/* --------------------------------- Contact --------------------------------- */
function Contact() {
  const [form, setForm] = useState({ name: "", email: "", phone: "", type: "", message: "" });
  const [sent, setSent] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError("");
    try {
      // POST /api/v1/leads/inbound — public, unauthenticated. Creates an unassigned lead a
      // lead-gen agent picks up in /leads/pipeline.
      await submitInboundLead(form);
      setSent(true);
    } catch (err) {
      setError(err?.message || "Something went wrong. Please email us instead.");
    } finally {
      setSubmitting(false);
    }
  };

  const inputClass =
    "w-full bg-white/5 border border-white/10 rounded-lg px-4 py-3 text-sm text-white placeholder:text-white/40 outline-none transition-all duration-200 focus:border-gold-400 focus:ring-1 focus:ring-gold-400 focus:bg-white/10";

  return (
    <section id="contact" className="bg-gradient-to-br from-primary-900 via-primary-800 to-indigo-950 text-white relative overflow-hidden py-24 border-b border-white/10">
      <div className="absolute top-0 right-0 h-[400px] w-[400px] rounded-full bg-gold-500/10 blur-[120px] pointer-events-none" />
      <div className="absolute bottom-0 left-0 h-[350px] w-[350px] rounded-full bg-primary-500/20 blur-[100px] pointer-events-none" />

      <div className="max-w-7xl mx-auto px-6 lg:px-8 relative z-10">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 items-center">
          {/* Left Column: Heading and Details */}
          <div className="lg:col-span-6 text-left flex flex-col gap-8">
            <div>
              <span className="text-gold-400 text-xs font-semibold uppercase tracking-wider bg-gold-400/10 px-3 py-1 rounded-full">
                Get in touch
              </span>
              <h2 className="text-3xl sm:text-4xl lg:text-5xl font-bold font-display text-white mt-4 leading-tight">
                Let's build something <span className="text-gold-400">extraordinary</span> together.
              </h2>
              <p className="text-white/60 text-sm mt-4 leading-relaxed max-w-lg">
                Have questions about our sprint curriculums, corporate pricing, or placement opportunities? Our support and partner relations teams are here to guide you.
              </p>
            </div>

            <div className="rounded-xl border border-gold-400/30 bg-gold-400/5 p-5 flex items-center justify-between gap-4 flex-wrap">
              <div>
                <h4 className="text-sm font-semibold text-white">Already know you want to join?</h4>
                <p className="text-xs text-white/60 mt-1 max-w-sm">
                  Skip the enquiry form — go straight to picking a track, choosing a plan, and paying to start immediately.
                </p>
              </div>
              <Link to="/register">
                <Button variant="gold" size="sm" icon={ArrowRight} iconPosition="right">
                  Enroll now
                </Button>
              </Link>
            </div>

            <div className="flex flex-col gap-4">
              <a href="mailto:support@moriahskillhub.com" className="group p-5 rounded-xl border border-white/10 bg-white/5 backdrop-blur-sm transition-all duration-300 hover:bg-white/10 hover:border-gold-400/40 flex items-start gap-4">
                <div className="h-10 w-10 rounded-lg bg-gold-500/10 flex items-center justify-center text-gold-400 shrink-0 group-hover:scale-110 transition-transform duration-300">
                  <Mail size={18} />
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-white tracking-wide">Email Support</h4>
                  <p className="text-sm text-gold-400 mt-0.5">support@moriahskillhub.com</p>
                  <p className="text-xs text-white/50 mt-1">For student queries, billing issues, and technical support.</p>
                </div>
              </a>

              <a href="mailto:partners@moriahskillhub.com" className="group p-5 rounded-xl border border-white/10 bg-white/5 backdrop-blur-sm transition-all duration-300 hover:bg-white/10 hover:border-gold-400/40 flex items-start gap-4">
                <div className="h-10 w-10 rounded-lg bg-gold-500/10 flex items-center justify-center text-gold-400 shrink-0 group-hover:scale-110 transition-transform duration-300">
                  <ShieldCheck size={18} />
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-white tracking-wide">Corporate Relations</h4>
                  <p className="text-sm text-gold-400 mt-0.5">partners@moriahskillhub.com</p>
                  <p className="text-xs text-white/50 mt-1">For organizations wishing to hire developers, co-host custom training pipelines, or sponsor project requirements.</p>
                </div>
              </a>

              <div className="group p-5 rounded-xl border border-white/10 bg-white/5 backdrop-blur-sm transition-all duration-300 hover:bg-white/10 hover:border-gold-400/40 flex items-start gap-4">
                <div className="h-10 w-10 rounded-lg bg-gold-500/10 flex items-center justify-center text-gold-400 shrink-0 transition-transform duration-300">
                  <MapPin size={18} />
                </div>
                <div className="text-white/80">
                  <h4 className="text-sm font-semibold text-white tracking-wide">Corporate Headquarters</h4>
                  <p className="text-xs mt-1 leading-relaxed text-white/60">
                    Moriah Tech Hub, 4th Floor, Phase 2, Electronic City, Bangalore, India - 560100
                  </p>
                  <div className="flex flex-wrap gap-x-4 gap-y-1.5 mt-2.5 pt-2 border-t border-white/5 text-xs text-white/50">
                    <span className="flex items-center gap-1"><Phone size={12} /> +91 (80) 4123-4567</span>
                    <span className="flex items-center gap-1"><Clock size={12} /> Mon-Fri, 9am - 6pm IST</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Right Column: Form Card */}
          <div className="lg:col-span-6">
            <form onSubmit={handleSubmit} className="rounded-2xl border border-white/10 bg-white/10 backdrop-blur-md shadow-2xl p-8 sm:p-10 transition-shadow duration-300 hover:shadow-gold-500/5 flex flex-col gap-5">
              {sent ? (
                <div className="text-center py-12 flex flex-col items-center justify-center gap-4 animate-fade-up">
                  <div className="h-16 w-16 rounded-full bg-gold-500/10 border border-gold-400/20 flex items-center justify-center text-gold-400 animate-pulse-slow">
                    <Check size={28} />
                  </div>
                  <div>
                    <h3 className="font-display font-semibold text-xl text-white">Message sent!</h3>
                    <p className="text-sm text-white/60 mt-2 max-w-xs mx-auto">
                      Thank you for reaching out. We will get back to you within one business day.
                    </p>
                  </div>
                </div>
              ) : (
                <>
                  <div className="flex flex-col gap-1.5 text-left mb-2">
                    <h3 className="text-xl font-semibold text-white">Not sure yet? Talk to us first</h3>
                    <p className="text-xs text-white/60">Still deciding on a track or plan? Fill this out and one of our counsellors will call you back.</p>
                  </div>

                  <div className="grid sm:grid-cols-2 gap-4 text-left">
                    <div className="flex flex-col gap-1.5">
                      <input
                        required
                        value={form.name}
                        onChange={(e) => setForm({ ...form, name: e.target.value })}
                        placeholder="Full name"
                        className={inputClass}
                      />
                    </div>
                    <div className="flex flex-col gap-1.5">
                      <input
                        required
                        type="email"
                        value={form.email}
                        onChange={(e) => setForm({ ...form, email: e.target.value })}
                        placeholder="Email address"
                        className={inputClass}
                      />
                    </div>
                  </div>

                  <div className="flex flex-col gap-1.5 text-left">
                    <input
                      required
                      type="tel"
                      value={form.phone}
                      onChange={(e) => setForm({ ...form, phone: e.target.value })}
                      placeholder="Phone number"
                      className={inputClass}
                    />
                  </div>

                  <div className="flex flex-col gap-1.5 text-left">
                    <select
  required
  value={form.type}
  onChange={(e) => setForm({ ...form, type: e.target.value })}
  className="w-full rounded-lg border border-slate-700 bg-slate-700/50 px-4 py-3 text-sm text-white outline-none transition-all focus:border-amber-400 focus:ring-1 focus:ring-amber-400 cursor-pointer"
>
  <option value="" disabled className="bg-slate-700 text-slate-400">
    Select inquiry type...
  </option>
  {INQUIRY_TYPES.map((t) => (
    <option key={t} value={t} className="bg-slate-700 text-white">
      {t}
    </option>
  ))}
</select>
                  </div>

                  <div className="flex flex-col gap-1.5 text-left">
                    <textarea
                      required
                      value={form.message}
                      onChange={(e) => setForm({ ...form, message: e.target.value })}
                      placeholder="Your message"
                      rows={5}
                      className={inputClass + " resize-none"}
                    />
                  </div>

                  {error && <p className="text-xs text-red-300 text-left -mt-2">{error}</p>}

                  <Button type="submit" variant="gold" size="lg" loading={submitting} icon={ArrowRight} iconPosition="right" className="mt-2 transition-all duration-300 hover:shadow-xl hover:shadow-gold-500/10">
                    Send message
                  </Button>
                </>
              )}
            </form>
          </div>
        </div>
      </div>
    </section>
  );
}
/* ----------------------------------- CTA ----------------------------------- */

function CTA() {
  return (
    <section className="max-w-7xl mx-auto px-6 lg:px-8 py-20">
      <div className="group relative overflow-hidden rounded-2xl bg-primary-800 text-white px-8 py-14 sm:px-14 text-center">
        <div className="absolute -right-16 -top-16 h-64 w-64 rounded-full bg-gold-500/10 transition-transform duration-700 group-hover:scale-125" />
        <div className="absolute -left-16 -bottom-16 h-64 w-64 rounded-full bg-white/5 transition-transform duration-700 group-hover:scale-125" />
        <div className="relative mx-auto h-14 w-14 rounded-full bg-white/10 flex items-center justify-center transition-transform duration-500 group-hover:scale-110">
          <Rocket className="text-gold-400 animate-float transition-transform duration-500 group-hover:-translate-y-1 group-hover:rotate-12" size={26} />
        </div>
        <h2 className="relative font-display text-3xl sm:text-4xl font-bold mt-5">Ready to start your sprint?</h2>
        <p className="relative text-white/70 mt-3 max-w-lg mx-auto">
          Create your student account, pick a plan, and get your first task assigned this week.
        </p>
        <div className="relative mt-8 flex justify-center gap-3 flex-wrap">
          <Link to="/register" className="group/btn">
            <Button variant="gold" size="lg" icon={ArrowRight} iconPosition="right" className="transition-all duration-300 group-hover/btn:-translate-y-0.5 group-hover/btn:shadow-xl group-hover/btn:shadow-gold-500/20">
              Create your account
            </Button>
          </Link>
          <Link to="/login">
            <Button variant="secondary" size="lg" className="bg-white/10 border-white/20 text-white transition-all duration-300 hover:bg-white/20 hover:-translate-y-0.5">
              Sign in
            </Button>
          </Link>
        </div>
      </div>
    </section>
  );
}

/* ---------------------------------- Footer --------------------------------- */

function Footer() {
  const [email, setEmail] = useState("");
  return (
    <footer className="bg-primary-900 text-white/60">
      <div className="max-w-7xl mx-auto px-6 lg:px-8 py-12 grid sm:grid-cols-2 lg:grid-cols-4 gap-8">
        <div>
          <img src={logoMark} alt="Moriah Skill Hub Logo" className="h-12 w-auto object-contain" />
          <p className="text-xs mt-3 leading-relaxed max-w-xs">
            Task-driven, sprint-oriented training combining real client simulations, mentor code reviews, and automated performance evaluation.
          </p>
          {/* Social Icons */}
          <div className="flex items-center gap-3.5 mt-4">
            <a href="#" className="text-white/40 hover:text-white transition-colors duration-200" aria-label="Facebook">
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 2h-3a5 5 0 0 0-5 5v3H7v4h3v8h4v-8h3l1-4h-4V7a1 1 0 0 1 1-1h3z" /></svg>
            </a>
            <a href="#" className="text-white/40 hover:text-white transition-colors duration-200" aria-label="Twitter">
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M22 4s-.7 2.1-2 3.4c1.6 10-9.4 17.3-18 11.6 2.2.1 4.4-.6 6-2C3 15.5.5 9.6 3 5c2.2 2.6 5.6 4.1 9 4-.9-4.2 4-6.6 7-3.8 1.1 0 3-1.2 3-1.2z" /></svg>
            </a>
            <a href="#" className="text-white/40 hover:text-white transition-colors duration-200" aria-label="LinkedIn">
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z" /><rect x="2" y="9" width="4" height="12" /><circle cx="4" cy="4" r="2" /></svg>
            </a>
            <a href="#" className="text-white/40 hover:text-white transition-colors duration-200" aria-label="GitHub">
              <svg className="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" /><path d="M9 18c-4.51 2-5-2-7-2" /></svg>
            </a>
          </div>
        </div>

        <div>
          <p className="text-xs font-semibold text-white uppercase tracking-wide">Platform</p>
          <ul className="mt-3 flex flex-col gap-2 text-sm">
            <li><a href="#how-it-works" className="transition-colors hover:text-white">How it works</a></li>
            <li><a href="#pricing" className="transition-colors hover:text-white">Pricing</a></li>
            <li><a href="#contact" className="transition-colors hover:text-white">Contact form</a></li>
          </ul>
        </div>

        <div>
          <p className="text-xs font-semibold text-white uppercase tracking-wide">Account</p>
          <ul className="mt-3 flex flex-col gap-2 text-sm">
            <li><Link to="/login" className="transition-colors hover:text-white">Sign in</Link></li>
            <li><Link to="/register" className="transition-colors hover:text-white">Create account</Link></li>
            <li><Link to="/forgot-password" className="transition-colors hover:text-white">Forgot password</Link></li>
          </ul>
        </div>

        <div>
          <p className="text-xs font-semibold text-white uppercase tracking-wide">Newsletter</p>
          <p className="text-xs mt-3">Stay updated with new sprints, features, and opportunities.</p>
          <form onSubmit={(e) => { e.preventDefault(); setEmail(""); }} className="mt-3 flex items-center gap-2">
            <div className="relative flex-1">
              <Mail size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-white/30" />
              <input
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                type="email"
                required
                placeholder="Enter your email"
                className="w-full rounded-lg bg-white/5 border border-white/10 pl-8 pr-3 py-2 text-xs text-white placeholder:text-white/30 outline-none transition-colors focus:border-gold-400"
              />
            </div>
            <button
              type="submit"
              aria-label="Subscribe"
              className="group shrink-0 h-8 w-8 rounded-lg bg-gold-500 text-primary-900 flex items-center justify-center transition-transform duration-200 hover:scale-110 active:scale-95"
            >
              <ArrowRight size={14} className="transition-transform duration-300 group-hover:translate-x-0.5" />
            </button>
          </form>
        </div>
      </div>

      <div className="border-t border-white/10">
        <div className="max-w-7xl mx-auto px-6 lg:px-8 py-5 flex flex-col sm:flex-row items-center justify-between gap-3">
          <p className="text-xs">© 2026 Moriah Skill Hub. All rights reserved.</p>
          <p className="text-xs flex items-center gap-1.5"><Check size={12} className="text-gold-400" /> Learn · Grow · Succeed</p>
        </div>
      </div>
    </footer>
  );
}

/* ---------------------------------- Home ----------------------------------- */

export default function Home() {
  return (
    <div className="bg-cream-50">
      <NavBar />
      <Hero />
      <HowItWorks />
      <Features />
      <Pricing />
      <Testimonials />
      <Contact />
      <CTA />
      <Footer />
    </div>
  );
}