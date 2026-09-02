# Builds docs/PROJECT-REFERENCE.md — the single deep reference:
# project narrative + modules + every endpoint's controller->service->repository->table
# call chain + request/response shapes + the audit (issues found and how they were fixed).
import json, re, os, glob, pathlib

ROOT = str(pathlib.Path(__file__).resolve().parent.parent)
HERE = pathlib.Path(__file__).parent
OPENAPI = os.path.join(ROOT, "docs", "openapi.json")
TRACE = json.load(open(os.path.join(HERE, "callgraph-fragments.json"), encoding="utf-8"))
FRAG = TRACE["fragments"]
OUT = os.path.join(ROOT, "docs", "PROJECT-REFERENCE.md")

spec = json.load(open(OPENAPI, encoding="utf-8"))
schemas = spec.get("components", {}).get("schemas", {})

# ---------- schema -> example (same approach as gen-api-doc.py) ----------
def resolve(node):
    if isinstance(node, dict) and "$ref" in node:
        n = node["$ref"].split("/")[-1]
        return schemas.get(n, {}), n
    return node, None

def example(node, seen=None, depth=0):
    seen = seen or set()
    node, name = resolve(node)
    if name and name in seen:
        return {"...": "recursive"}
    if name:
        seen = seen | {name}
    if not isinstance(node, dict) or depth > 8:
        return None
    if name and name.startswith("ApiResponse"):
        p = node.get("properties", {})
        o = {"success": True}
        if "data" in p:
            o["data"] = example(p["data"], seen, depth + 1)
        o["error"] = None
        return o
    if "allOf" in node:
        m = {}
        for part in node["allOf"]:
            v = example(part, seen, depth + 1)
            if isinstance(v, dict):
                m.update(v)
        return m
    if "example" in node:
        return node["example"]
    if "enum" in node and node["enum"]:
        return node["enum"][0]
    t = node.get("type")
    if t == "object" or "properties" in node:
        return {k: example(v, seen, depth + 1) for k, v in node.get("properties", {}).items()}
    if t == "array":
        return [example(node.get("items", {}), seen, depth + 1)]
    if t == "string":
        return {"date-time": "2026-01-15T10:30:00Z", "date": "2026-01-15",
                "uuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6", "email": "user@example.com",
                "binary": "<binary>"}.get(node.get("format"), "string")
    if t == "integer":
        return 0
    if t == "number":
        return 0.0
    if t == "boolean":
        return True
    return None

def jd(v):
    return json.dumps(v, indent=2, ensure_ascii=False)

def norm(p):
    return "/" + "/".join(s for s in re.sub(r"\{[^}]+\}", "{}", p).split("/") if s)

# ---------- auth map (from controllers) ----------
auth_map = {}
MAP = {"GetMapping": "GET", "PostMapping": "POST", "PutMapping": "PUT", "PatchMapping": "PATCH", "DeleteMapping": "DELETE"}
SRC = os.path.join(ROOT, "src", "main", "java", "com", "moriah", "skillhub")
PRE = r'@PreAuthorize\(\s*"([^"]*(?:\\.[^"]*)*)"\s*\)'
for f in glob.glob(os.path.join(SRC, "**", "*Controller.java"), recursive=True):
    s = open(f, encoding="utf-8").read()
    bm = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"', s)
    base = bm.group(1) if bm else ""
    cpos = re.search(r'\bclass\s+\w+', s).start()
    cpre = None
    for pm in re.finditer(PRE, s):
        if pm.start() < cpos:
            cpre = pm.group(1)
    for hm in re.finditer(r'\n(\s{4})public\s+[\w<>,\.\?\[\]\s@]+?\s+(\w+)\s*\(', s):
        pre_start = s.rfind("\n\n", 0, hm.start())
        pre = s[max(0, pre_start):hm.start()]
        mp = re.search(r'@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)(?:\(([^)]*)\))?', pre)
        if not mp:
            continue
        sub = re.search(r'"([^"]+)"', mp.group(2) or "")
        full = norm((base + "/" + sub.group(1)) if sub else base)
        pm2 = re.search(PRE, pre)
        auth_map[f"{MAP[mp.group(1)]} {full}"] = (pm2.group(1) if pm2 else cpre) or "(none)"

PUBLIC_PREFIX = ("/api/v1/auth/", "/api/v1/webhooks/", "/api/v1/portfolio/", "/api/v1/certificates/verify/")

def auth_label(key, path):
    a = auth_map.get(key, "(none)")
    if a == "(none)":
        p = norm(path)
        if p == "/api/v1/plans" or any(p.startswith(x) for x in PUBLIC_PREFIX):
            return "Public — no token"
        return "Authenticated (any logged-in user; further gated in the service layer)"
    if "isAuthenticated()" in a:
        return "Authenticated (any logged-in user)"
    if "permitAll()" in a:
        return "Public — no token"
    m = re.search(r'has(Any)?Role\(([^)]*)\)', a)
    if m:
        return "Role — " + " / ".join(r.strip().strip("'\"") for r in m.group(2).split(","))
    return "`" + a + "`"

# ---------- module metadata (hand-curated) ----------
MODULE_INFO = {
 "Auth": ("auth/", "Features 03, 05", "Registration, login, JWT issue/refresh/rotation, email verification, "
          "password reset, Google/GitHub OAuth2, and TOTP 2FA (mandatory for ADMIN & HR_MANAGER).\n\n"
          "> **OAuth2 login endpoints are filter-handled, not controllers**, so they are absent from "
          "`openapi.json` and the endpoint list below. Browser flow: "
          "`GET /api/v1/auth/oauth2/authorize/{google|github}` → 302 to the provider → the provider "
          "redirects to `GET /api/v1/auth/oauth2/callback/{google|github}?code=…&state=…`, which "
          "returns the **same `LoginResponse` envelope as `POST /api/v1/auth/login`** (token pair, "
          "or a 2FA challenge). `OAuth2AuthenticationSuccessHandler` / `…FailureHandler` write the "
          "JSON directly (they run before Spring MVC). Redirect URI to register with the provider: "
          "`{baseUrl}/api/v1/auth/oauth2/callback/{google|github}`.",
          "users, roles, user_roles, refresh_tokens, password_reset_tokens, email_verification_tokens"),
 "Users": ("user/", "Feature 09", "Profile fields, server-computed completion %, resume upload/download, "
           "and the public portfolio (name/title/skills/certs only — no PII).",
           "users, user_profiles"),
 "Plans": ("subscription/", "Feature 07", "Public, Redis-cached catalogue of the 5 subscription tiers.",
           "subscription_plans"),
 "Subscriptions": ("subscription/", "Features 07, 22", "Checkout order creation, the caller's subscription history, "
                   "and the nightly SubscriptionExpiryJob.", "user_subscriptions, payments, coupon_redemptions"),
 "Checkout": ("payment/", "Feature 07", "Creates a Razorpay/Stripe order + a CREATED payments row; amount is always "
              "re-read from subscription_plans server-side.", "payments, coupons, coupon_redemptions"),
 "Webhooks": ("payment/, crm/webhook/", "Features 07, 18", "Signature-verified, idempotent gateway callbacks: "
              "Razorpay/Stripe payment & refund, and WhatsApp inbound messages. Raw body captured before parsing; "
              "webhook_events.event_id is the idempotency guarantee.", "webhook_events, payments, user_subscriptions, invoices, lead_activities"),
 "Batches": ("batch/", "Features 10, 20", "PM/Admin batch CRUD, manual student add/remove, the automatic "
             "BatchAllocationService (called by the payment webhook), and graduation sign-off.",
             "batches, batch_students, pending_batch_allocations"),
 "Sprints": ("sprint/", "Feature 11", "Sprint CRUD + activation (one ACTIVE sprint per batch), and weekly assignment windows.",
             "sprints, assignment_windows, tasks"),
 "Tasks": ("sprint/", "Feature 11", "Task CRUD, PM assignment, student self-pull (blocked by an open PROJECT_DELAY PIP), "
           "and the BACKLOG→ASSIGNED→IN_PROGRESS→IN_REVIEW→COMPLETED|REJECTED state machine.", "tasks, sprints"),
 "Submissions": ("submission/", "Feature 12", "Student GitHub PR submission, GitHub REST verification (PR exists, "
                 "author matches github_username, state open), commit metadata, retry job on GitHub outage.",
                 "task_submissions, tasks"),
 "Reviews": ("submission/", "Feature 12", "PM review queue, per-submission code review (score 1–10, verdict), "
             "and weekly code-defence reviews (the REVIEW_FAILED PIP data source).",
             "code_reviews, weekly_reviews, task_submissions"),
 "Standups": ("attendance/", "Feature 13", "PM schedules a standup with a late cutoff; students self check-in "
              "(PRESENT/LATE); PM can record/override attendance. AttendanceFinalisationJob writes ABSENT rows nightly.",
              "standups, attendance"),
 "Attendance": ("attendance/", "Feature 13", "The caller's own attendance and a batch attendance roster. "
                "Percentages are never computed here — they come from student_metrics.", "attendance, standups"),
 "Assessments": ("assessment/", "Feature 14", "Quiz authoring, timed attempts (server-enforced duration, correct "
                 "answers never sent), auto-grading of MCQ/MULTI_SELECT, CODE answers flagged PENDING_MANUAL_GRADING.",
                 "quizzes, quiz_questions, quiz_attempts, quiz_answers"),
 "Projects": ("project/", "Feature 15", "DEVELOPER-authored projects, assets (S3 key XOR external URL), "
              "DRAFT→PUBLISHED→ARCHIVED. Only PUBLISHED attaches to a task.", "projects, project_assets, bug_challenges"),
 "Bug Challenges": ("project/", "Feature 15", "Bug-fix challenges attached to a project (broken code, expected "
                    "behaviour, test script).", "bug_challenges"),
 "PIP": ("pip/, pip/engine/", "Feature 17", "The caller's PIP status, PM/Admin PIP browsing, milestone completion, "
         "the day-15 exit review (clearance verified server-side against student_metrics), and ADMIN threshold config. "
         "PipEvaluationJob runs nightly at 02:00.", "pip_rules, pip_records, pip_milestones, student_metrics"),
 "Certificates": ("certificate/", "Feature 20", "Certificate issuance (requires GRADUATED, no open PIP, all sprints "
                  "closed), the caller's certificates, revocation, and the PUBLIC unauthenticated QR verification "
                  "endpoint.", "certificates, batch_students"),
 "CRM": ("crm/", "Feature 18", "Lead ingestion with SHA-256 email+phone dedup, pipeline stage transitions "
         "(forward-skip blocked, backward allowed with a reason), activity logging, monthly targets.",
         "leads, lead_activities, sales_targets"),
 "HR": ("hr/", "Feature 19", "Employee records, KYC document upload + verification, leave workflow routed to the "
        "reporting manager, payroll computation (BigDecimal, unique per employee+month), and letter generation.",
        "employees, hr_documents, leave_requests, payroll_records"),
 "BA": ("ba/", "Feature 21", "BUSINESS_ANALYST requirement documents (BRD/SRS/FRS, versioned, DRAFT→IN_REVIEW→APPROVED) "
        "and resource allocations mapping students/batches to a client project.",
        "requirement_documents, resource_allocations"),
 "Clients": ("client/", "Feature 21", "ADMIN-provisioned CLIENT users submit project scope and view burndown/milestone "
             "progress for their own project only.", "clients, client_projects, resource_allocations"),
 "Admin": ("admin/", "Feature 22", "Cached KPI overview, revenue by range (read replica), user status/role management "
           "(status/role change bumps token_version → instant revocation), plan pricing config, read-only audit query, "
           "and async XLSX exports.", "student_metrics, v_revenue_monthly, v_lead_funnel, users, user_roles, subscription_plans, audit_logs"),
}

# ---------- assemble ----------
by_tag = {}
order = {"GET": 0, "POST": 1, "PUT": 2, "PATCH": 3, "DELETE": 4}
for path, ops in spec["paths"].items():
    for method, op in ops.items():
        if method.lower() not in ("get", "post", "put", "patch", "delete"):
            continue
        tag = (op.get("tags") or ["Other"])[0]
        by_tag.setdefault(tag, []).append((path, method.upper(), op))

L = []
def w(s=""):
    L.append(s)

# ============ header + narrative ============
w(open(os.path.join(HERE, "project-ref-narrative.md"), encoding="utf-8").read().rstrip())
w()
w("---")
w()
w("# Part 3 — Modules & Endpoints")
w()
w("Every endpoint below shows: the **auth/role** required, **what it does**, the **request body** "
  "(shape — field names/types exact, values illustrative), the **call chain** "
  "(`Controller.handler` → `Service.method` → `Repository.method` → **table**, with the SQL for "
  "`@Query` methods and the transaction / side-effect markers), the **success response** shape, and "
  "the **errors** it can return. The call chain is a 2–3 level static trace; a private helper "
  "inside a service may not fully expand — open the named service class for the last mile.")
w()

for tag in sorted(by_tag):
    slug = tag.lower().replace(" ", "-")
    w(f"## Module: {tag}")
    w()
    info = MODULE_INFO.get(tag)
    if info:
        pkg, feat, desc, tables = info
        w(f"- **Package:** `com.moriah.skillhub.{pkg}`")
        w(f"- **Build-plan:** {feat}")
        w(f"- **Tables:** `{tables}`")
        w()
        w(desc)
        w()
    for path, method, op in sorted(by_tag[tag], key=lambda x: (x[0], order[x[1]])):
        key = f"{method} {norm(path)}"
        w(f"### `{method}` `{path}`")
        w()
        if op.get("summary"):
            w("_" + op["summary"].strip() + "_")
            w()
        w(f"- **Auth:** {auth_label(key, path)}")
        params = op.get("parameters", [])
        pp = [p for p in params if p.get("in") == "path"]
        qp = [p for p in params if p.get("in") == "query" and p["name"] != "pageable"]
        if pp:
            w("- **Path params:** " + ", ".join(f"`{p['name']}` ({p.get('schema',{}).get('type','string')})" for p in pp))
        if qp:
            w("- **Query params:** " + ", ".join(
                f"`{p['name']}`{'*' if p.get('required') else ''} ({p.get('schema',{}).get('type','string')})" for p in qp))
        if any(p["name"] == "pageable" for p in params):
            w("- **Paginated:** `page`, `size` (max 100), `sort=field,asc|desc`")
        w()

        rb = op.get("requestBody")
        if rb:
            c = rb.get("content", {})
            if "multipart/form-data" in c:
                mp, _ = resolve(c["multipart/form-data"].get("schema", {}))
                w("**Request body** — `multipart/form-data`: " +
                  ", ".join(f"`{fn}`" for fn in mp.get("properties", {})))
                w()
            else:
                js = c.get("application/json") or next(iter(c.values()), {})
                sch, _ = resolve(js.get("schema", {}))
                if sch.get("type") == "string" and "properties" not in sch:
                    w("**Request body:** raw text (the webhook payload — the HMAC signature is "
                      "verified against these exact bytes before any parsing).")
                    w()
                elif js.get("schema"):
                    w("**Request body:**")
                    w()
                    w("```json")
                    w(jd(example(js["schema"])))
                    w("```")
                    w()

        w(FRAG.get(key, "_(call chain not resolved — see the controller)_"))
        w()

        resp = op.get("responses", {})
        ok = next((k for k in ("200", "201") if k in resp), None)
        if ok:
            rc = resp[ok].get("content", {})
            rs = (rc.get("application/json") or rc.get("*/*") or next(iter(rc.values()), {})).get("schema")
            if rs:
                w(f"**Response `{ok}`:**")
                w()
                w("```json")
                w(jd(example(rs)))
                w("```")
                w()
        elif "204" in resp:
            w("**Response `204`:** no body")
            w()
        errs = [f"`{c}`" for c in sorted(resp) if c not in ("200", "201", "204")]
        w("**Errors:** " + (", ".join(errs) if errs else "— (envelope `error` on any failure; see §2.3)"))
        w()
        w("---")
        w()

w(open(os.path.join(HERE, "project-ref-audit-appendix.md"), encoding="utf-8").read().rstrip())
w()

open(OUT, "w", encoding="utf-8").write("\n".join(L))
print("wrote", OUT, os.path.getsize(OUT), "bytes")
