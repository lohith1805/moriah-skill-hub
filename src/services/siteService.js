// Public marketing-site data — the landing page (pages/public/Home.jsx) talks to the
// unauthenticated endpoints: GET /api/v1/public/stats, GET /api/v1/plans, and
// POST /api/v1/leads/inbound for the contact form.
import { apiClient } from "./apiClient";

export async function getPublicStats() {
  // { graduates, activeLearners, activeBatches, placements, certificatesIssued, hiringPartners }
  return apiClient.get("/public/stats");
}

// Curated marketing copy per plan code — the backend plan carries the real price and the
// entitlement flags, but no tagline or feature bullets for a sales page.
const PLAN_COPY = {
  STARTER: {
    model: "Self-paced foundations",
    features: ["Curated learning path", "Community access", "Practice projects", "Progress tracking"],
  },
  PROFESSIONAL: {
    model: "Guided skill-building",
    features: ["Everything in Starter", "Structured assignments", "Auto-graded quizzes", "Certificate on completion"],
  },
  PROJECT_BASED: {
    model: "Live client simulations",
    features: [
      "Sprint-based real briefs",
      "1-on-1 mentor code reviews",
      "Standups + velocity tracking",
      "PIP safety net",
      "Verified certificate",
    ],
  },
};

const flagFeatures = (p) =>
  [
    p.allowsBatch && "Cohort batch enrolment",
    p.allowsSprints && "Sprint task delivery",
    p.mentorSupport && "Mentor support",
    p.allowsPip && "Performance-improvement plan",
    p.allowsClientProject && "Client project access",
    p.allowsInternshipLetter && "Internship letter",
  ].filter(Boolean);

export async function getPublicPlans() {
  const rows = await apiClient.get("/plans");
  return (Array.isArray(rows) ? rows : [])
    .map((p) => ({
      code: p.code,
      name: p.name,
      price: Number(p.priceInr ?? p.price ?? 0),
      tierRank: p.tierRank ?? 99,
      model: PLAN_COPY[p.code]?.model || "Subscription plan",
      features: PLAN_COPY[p.code]?.features || flagFeatures(p),
    }))
    .sort((a, b) => a.tierRank - b.tierRank);
}

export async function submitInboundLead({ name, email, phone, message, type }) {
  const body = {
    name: (name || "").trim(),
    email: (email || "").trim(),
    phone: (phone || "").trim(),
    message: message ? message.trim() : null,
    leadType: type === "Corporate hiring partner" ? "B2B" : "B2C",
    source: "LANDING_PAGE",
  };
  return apiClient.post("/leads/inbound", body);
}
