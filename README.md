# Moriah Skill Hub — Frontend

A production-ready React frontend for **Moriah Skill Hub**, an enterprise EdTech, Agile
Project-Simulation, and Workforce Readiness platform, built strictly from the provided
SRS and FRS documents.

Brand colors (navy `#0D2845`, gold `#EBB80D`, cream `#F7F5F0`) were extracted directly
from the supplied logo and used as the foundation of the design system — no colors were
invented.

---

## 1. Getting Started Locally

```bash
cd moriah-skill-hub
npm install
npm run dev        # starts Vite dev server on http://localhost:5173
```

Other scripts:

```bash
npm run build       # production build to /dist
npm run preview      # serve the production build locally
```

No environment variables are required to run the frontend in mock mode (default).
To point at a real backend, set:

```bash
# .env.local
VITE_API_BASE_URL=https://api.your-backend.com/v1
```

...and flip `USE_MOCKS` to `false` in `src/services/apiClient.js` — every service module
already calls through `apiClient`, so no page or component code needs to change.

### Trying it out

The login screen includes a **"Sign in as"** role selector (demo-mode only) so you can
preview any of the 8 portals — email/password are not checked against a real backend yet.

---

## 2. Folder Structure

```
src/
├── app/                  # Redux store + auth slice
├── assets/                # Logo assets extracted from the brand PDF
├── components/
│   ├── ui/                 # Design-system primitives (Button, Input, Table, Modal, …)
│   ├── layout/              # Sidebar, Header, DashboardLayout, AuthLayout, PageHeader
│   └── widgets/              # StatCard, KanbanBoard
├── context/                # AuthContext, ToastContext
├── hooks/                   # usePagination, useDebounce
├── pages/
│   ├── auth/                 # Login, Register, ForgotPassword
│   ├── student/               # 8 screens
│   ├── trainer/                # 8 screens
│   ├── developer/               # 5 screens
│   ├── leadgen/                   # 4 screens
│   ├── hr/                         # 6 screens
│   ├── ba/                          # 5 screens
│   ├── admin/                        # 6 screens
│   ├── client/                        # 4 screens
│   └── shared/                         # NotFound, Unauthorized
├── routes/                  # AppRoutes, ProtectedRoute, navConfig (per-role sidebar)
├── services/                 # API client + one module per domain (mocked, backend-ready)
└── utils/                     # constants, validators, formatters, roleAccess
```

---

## 3. Implemented Screens (45 total)

| Module | Screens |
|---|---|
| **Auth** | Login (role-aware), Register, Forgot Password |
| **Student Portal** | Dashboard, Profile & Resume, Subscription & Billing, Sprint Board (Kanban), GitHub Submissions, Assessments, PIP & Performance, Certificates |
| **Trainer / PM Portal** | Dashboard, Batches, Sprint Planning, Standups & Attendance, Code Review, Performance Analytics, PIP Management, Graduation Approval |
| **Developer Portal** | Dashboard, Projects, Bug Challenges, Assessment Bank, Resource Library |
| **Lead Generator / CRM** | Dashboard, Lead Pipeline (Kanban), Campaigns, Targets & Leaderboard |
| **HR Portal** | Dashboard, Onboarding, Attendance & Leave, Payroll, Letters & Certificates, Exit Management |
| **Business Analyst Portal** | Dashboard, Requirements Authoring, Sprint & Resource Planning, Client Project Review, Meeting Coordination |
| **Super Admin Portal** | Executive Dashboard, User & Role Management, Subscription & Pricing, Transactions & Refunds, Audit & Security Logs, Reporting Engine |
| **Corporate Client Portal** | Dashboard, Project Requirements, Talent Pool, Sprint Demo Reviews |
| **Shared** | 404, Unauthorized (RBAC redirect target) |

---

## 4. Reusable Component Library

`src/components/ui`: Button, Input / Select / Textarea / Checkbox (via `FormField.jsx`),
FileUpload (drag & drop), Card, Badge, Avatar, Modal, ConfirmDialog, Table (loading /
empty states built-in), Pagination, Tabs, Dropdown, ProgressBar, Breadcrumbs,
LoadingSpinner, EmptyState.

`src/components/widgets`: StatCard, KanbanBoard (drag-and-drop, used by Student Sprint
Board and CRM Lead Pipeline).

`src/components/layout`: Sidebar (role-aware nav from `routes/navConfig.js`), Header
(search, notifications, profile menu), DashboardLayout, AuthLayout, PageHeader.

All form inputs support `error` / `hint` / `required` states; all list views handle
loading, empty, and populated states; all destructive actions (refunds, PIP clearing,
account suspension) go through `ConfirmDialog`.

---

## 5. Route Structure & RBAC

Every dashboard route is wrapped in `<ProtectedRoute allowedRoles={[...]}>` — an
authenticated user whose role isn't in the allowed list is redirected to their own
dashboard; an unauthenticated user is redirected to `/login`.

| Role | Base path | Home route |
|---|---|---|
| Student | `/student/*` | `/student/dashboard` |
| Trainer / PM | `/trainer/*` | `/trainer/dashboard` |
| Developer | `/developer/*` | `/developer/dashboard` |
| Lead Generator | `/leads/*` | `/leads/dashboard` |
| HR Specialist | `/hr/*` | `/hr/dashboard` |
| Business Analyst | `/ba/*` | `/ba/dashboard` |
| System Admin | `/admin/*` | `/admin/dashboard` |
| Corporate Client | `/client/*` | `/client/dashboard` |

The full route tree lives in `src/routes/AppRoutes.jsx`; per-role sidebar items live in
`src/routes/navConfig.js` — add a route in both places to add a new screen.

---

## 6. API Integration Points

The frontend is fully decoupled from data-fetching via a **services layer**
(`src/services/*.js`) — 9 domain modules (`auth`, `student`, `trainer`, `developer`,
`crm`, `hr`, `ba`, `admin`, `client`) plus `notificationService`. Every module currently
resolves against in-memory mock data (`mockData.js`) through `mockRequest()`, simulating
network latency and error states.

To connect a real backend (per SRS §5.1 — Laravel/Spring Boot, JWT + OAuth2, MySQL,
Razorpay/Stripe, GitHub API, WhatsApp/SendGrid):

1. Implement the matching REST endpoints (suggested prefix `/api/v1/...`, one resource
   per current mock function name — e.g. `getBatches()` → `GET /batches`).
2. Set `VITE_API_BASE_URL` and flip `USE_MOCKS = false` in `apiClient.js`.
3. Replace each service function's `mockRequest(...)` call with
   `apiClient.request('/path', { method, body })` — the function signatures used
   throughout the UI do not need to change.
4. `apiClient.js` already attaches a `Bearer` token from `localStorage` and centralizes
   error handling via `ApiError`, matching the JWT/OAuth2 model in the SRS.

No business logic is embedded in components — validation rules live in
`utils/validators.js`, formatting in `utils/formatters.js`, and role/plan/PIP constants
in `utils/constants.js`, so backend and frontend engineers can work independently.

---

## 7. Notes on Scope

This build implements the full breadth of screens and workflows specified in the SRS
and FRS — every one of the 8 RBAC personas, all 8 functional modules, the 5 subscription
tiers, and the automated PIP trigger/recovery lifecycle — with realistic mock data,
complete form validation, and all required UI states (loading, empty, error, success,
confirmation, disabled). Payment gateway checkout, GitHub OAuth, and WhatsApp/email
delivery are represented as UI flows with mocked responses, ready to be wired to real
provider SDKs.
