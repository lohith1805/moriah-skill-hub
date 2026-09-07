#!/usr/bin/env python3
"""End-to-end integration flow against the live backend on :8080.
Walks: public -> new-student registration+verify -> student session -> seeded
active-subscription student -> trainer/PM -> lead-gen -> admin (2FA setup).
Prints STEP n  PASS/FAIL and exits non-zero on any critical failure.
Secrets (mysql root pw) are read from .env and never printed.
"""
import json, sys, time, subprocess, re, urllib.request, urllib.error, hmac, hashlib, base64, struct, pathlib

BASE = "http://localhost:8080/api/v1"
ROOT = pathlib.Path(__file__).resolve().parent.parent  # repo root (this file lives in scripts/)
STAMP = time.strftime("%Y%m%d%H%M%S")

env = {}
for line in (ROOT / ".env").read_text().splitlines():
    if "=" in line and not line.strip().startswith("#"):
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip()
MYSQL_PW = env.get("MYSQL_ROOT_PASSWORD", "")

results = []
def rec(step, ok, detail=""):
    results.append((step, ok, detail))
    print(f"  {'PASS' if ok else 'FAIL'}  {step}" + (f"   [{detail}]" if detail else ""))
    if not ok:
        print(f"        >>> {detail}")

def call(method, path, token=None, body=None, expect=(200, 201), raw=False):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            code, txt = r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        code, txt = e.code, e.read().decode()
    except Exception as e:
        return None, None, f"{type(e).__name__}: {e}"
    try:
        parsed = json.loads(txt)
    except Exception:
        parsed = txt
    payload = parsed if raw else (parsed.get("data") if isinstance(parsed, dict) and "data" in parsed else parsed)
    okc = code in (expect if isinstance(expect, (tuple, list)) else (expect,))
    return code, payload, (None if okc else f"HTTP {code}: {txt[:300]}")

def mysql(sql):
    p = subprocess.run(
        ["docker", "exec", "-i", "skillhub-mysql", "mysql", "-uroot", f"-p{MYSQL_PW}",
         "-N", "-B", "moriah_skillhub", "-e", sql],
        capture_output=True, text=True)
    if p.returncode != 0:
        raise RuntimeError(p.stderr.strip())
    return p.stdout.strip()

def totp(secret_b32, when=None):
    key = base64.b32decode(secret_b32 + "=" * (-len(secret_b32) % 8), casefold=True)
    ctr = struct.pack(">Q", int((when or time.time()) // 30))
    h = hmac.new(key, ctr, hashlib.sha1).digest()
    o = h[-1] & 0x0F
    return f"{(struct.unpack('>I', h[o:o+4])[0] & 0x7FFFFFFF) % 1_000_000:06d}"

def login(email, pw="Password123!"):
    time.sleep(1)  # auth endpoints are IP-rate-limited — run backend with AUTH_RATE_LIMIT_PER_MIN raised
    c, d, err = call("POST", "/auth/login", body={"email": email, "password": pw})
    if err:
        return None, None, err
    if d.get("twoFactorRequired"):
        return None, d, "2FA"
    t = d["tokens"]
    return t["accessToken"], d, None

print("\n=== PART A — public / unauthenticated ===")
c, d, err = call("GET", "/../actuator/health".replace("/../", "/"), raw=True)  # not used
c, d, err = call("GET", "/plans", raw=True)
rec("A1 GET /plans (Redis-cached, was 500-prone)", err is None and isinstance(d.get("data"), list) and len(d["data"]) >= 1,
    err or f'{len(d["data"])} plans: ' + ",".join(p["code"] for p in d["data"]))
plan_codes = [p["code"] for p in d["data"]] if err is None else []

c, d, err = call("GET", "/public/stats")
rec("A2 GET /public/stats", err is None and "graduates" in d, err or json.dumps(d))

print("\n=== PART B — new student: register -> resend -> verify -> login ===")
email = f"e2e.student.{STAMP}@example.com"
phone = "+91" + STAMP  # users.phone is UNIQUE — must differ per run
c, d, err = call("POST", "/auth/register", body={
    "fullName": "E2E Student", "email": email, "phone": phone,
    "password": "Password123!"}, expect=201)
rec("B1 POST /auth/register", err is None and d.get("email") == email, err or json.dumps(d))

c, d, err = call("POST", "/auth/resend-verification", body={"email": email})
rec("B2 POST /auth/resend-verification (new endpoint)", err is None, err or "200 (non-enumerating)")

try:
    payloads = mysql(
        "SELECT payload FROM notifications n JOIN users u ON u.id=n.user_id "
        f"WHERE u.email='{email}' ORDER BY n.id DESC")
    tok = None
    for row in payloads.splitlines():
        m = re.search(r"token=([A-Za-z0-9._\-]+)", row)
        if m:
            tok = m.group(1); break
    ok = bool(tok)
except Exception as e:
    tok, ok = None, False
    rec("B3 read verification token from notifications", False, str(e))
if tok:
    rec("B3 read verification token from notifications table", True, tok[:12] + "…")
    c, d, err = call("POST", "/auth/verify-email", body={"token": tok})
    rec("B4 POST /auth/verify-email", err is None, err or "200")

stu_token, stu_login, err = login(email)
rec("B5 POST /auth/login (verified new student)", stu_token is not None, err or "tokens issued")

print("\n=== PART C — new student session (no subscription = limited access) ===")
if stu_token:
    c, d, err = call("GET", "/users/me", stu_token)
    rec("C1 GET /users/me", err is None and d.get("email") == email,
        err or f'twoFactorEnabled={d.get("twoFactorEnabled")}, complete={d.get("isComplete")}')

    c, d, err = call("GET", "/subscriptions/me", stu_token, expect=(200, 404, 400))
    rec("C2 GET /subscriptions/me -> not found (no plan yet)", c in (404, 400), err or f"HTTP {c}")

    c, d, err = call("GET", "/subscriptions/me/invoices", stu_token)
    rec("C3 GET /subscriptions/me/invoices -> [] (new endpoint)", err is None and d == [], err or json.dumps(d))

    c, d, err = call("GET", "/batches", stu_token)
    rec("C4 GET /batches (student-scoped, none enrolled)", err is None, err or f'{len(d.get("content", []))} rows')

    c, d, err = call("GET", "/lessons", stu_token)
    rec("C5 GET /lessons (catalogue)", err is None, err or f'{len(d.get("content", d) if isinstance(d,dict) else d)} items')

    c, d, err = call("GET", "/notifications/unread-count", stu_token)
    rec("C6 GET /notifications/unread-count", err is None, err or json.dumps(d))

    pc = "PROJECT_BASED" if "PROJECT_BASED" in plan_codes else (plan_codes[0] if plan_codes else "PROJECT_BASED")
    c, d, err = call("POST", "/subscriptions/checkout", stu_token,
                     body={"planCode": pc, "gateway": "RAZORPAY", "trackCode": "FULL_STACK"},
                     expect=(200, 201, 502))
    rec("C7 POST /subscriptions/checkout", c in (200, 201, 502),
        (f"order created: {d.get('razorpayOrderId')}" if c in (200, 201)
         else f"HTTP {c} (needs real Razorpay test key — expected without one)"))

print("\n=== PART D — seeded student WITH an active PROJECT_BASED subscription ===")
s1_token, s1_login, err = login("student1@moriah.test")
rec("D1 login student1@moriah.test", s1_token is not None, err or "ok")
if s1_token:
    c, d, err = call("GET", "/subscriptions/me", s1_token)
    rec("D2 GET /subscriptions/me -> ACTIVE", err is None and d.get("status") == "ACTIVE",
        err or f'{d.get("planCode")} {d.get("status")} ends {d.get("endDate")}')
    c, d, err = call("GET", "/subscriptions/me/invoices", s1_token)
    rec("D3 GET /subscriptions/me/invoices", err is None,
        err or f'{len(d)} invoice row(s)' + (f' -> {d[0].get("invoiceStatus")}/{d[0].get("planName")}' if d else ""))
    c, d, err = call("GET", "/batches", s1_token)
    rec("D4 GET /batches (enrolled)", err is None, err or f'{len(d.get("content", []))} batch(es)')
    c, d, err = call("GET", "/certificates/me", s1_token)
    rec("D5 GET /certificates/me", err is None, err or f'{len(d.get("content", d) if isinstance(d,dict) else d)} cert(s)')
    c, d, err = call("GET", "/attendance/me", s1_token)
    rec("D6 GET /attendance/me", err is None, err or "ok")
    c, d, err = call("GET", "/pip/me", s1_token, expect=(200, 404))
    rec("D7 GET /pip/me", c in (200, 404), err or f"HTTP {c}")

print("\n=== PART E — trainer / PM (pm@moriah.test) ===")
pm_token, pm_login, err = login("pm@moriah.test")
rec("E1 login pm@moriah.test", pm_token is not None, err or "ok")
if pm_token:
    c, d, err = call("GET", "/batches", pm_token)
    batches = d.get("content", []) if err is None else []
    rec("E2 GET /batches (full list)", err is None and len(batches) >= 1, err or f"{len(batches)} batches")
    if batches:
        bid = batches[0]["id"]
        c, d, err = call("GET", f"/batches/{bid}", pm_token)
        rec(f"E3 GET /batches/{bid}", err is None, err or d.get("name"))
        c, d, err = call("GET", f"/batches/{bid}/students", pm_token)
        rec(f"E4 GET /batches/{bid}/students (roster)", err is None,
            err or f'{len(d)} student(s)')
        c, d, err = call("GET", f"/sprints?batchId={bid}", pm_token)
        rec("E5 GET /sprints?batchId=", err is None, err or f'{len(d.get("content", d) if isinstance(d,dict) else d)} sprint(s)')
        c, d, err = call("GET", f"/standups?batchId={bid}", pm_token)
        rec("E6 GET /standups?batchId=", err is None, err or f'{len(d.get("content", []))} standup(s)')
        c, d, err = call("GET", "/standups", pm_token, expect=(400,))
        rec("E6b GET /standups (no batchId) -> 400 not 500 (handler fix)", c == 400, err or "HTTP 400 VALIDATION_FAILED")
    c, d, err = call("GET", "/reviews/queue", pm_token)
    rec("E7 GET /reviews/queue", err is None, err or "ok")

print("\n=== PART F — lead generation (sales@moriah.test) ===")
lg_token, lg_login, err = login("sales@moriah.test")
rec("F1 login sales@moriah.test", lg_token is not None, err or "ok")
if lg_token:
    c, d, err = call("GET", "/leads", lg_token)
    rec("F2 GET /leads (pipeline)", err is None, err or f'{len(d.get("content", []))} lead(s)')
    c, d, err = call("GET", "/leads/targets/leaderboard", lg_token)
    rec("F3 GET /leads/targets/leaderboard (new endpoint)", err is None, err or f'{len(d)} row(s)')
    c, d, err = call("GET", "/leads/targets/me", lg_token, expect=(200, 404))
    rec("F4 GET /leads/targets/me", c in (200, 404), err or f"HTTP {c}")
    c, d, err = call("GET", "/leads/campaigns", lg_token)
    rec("F5 GET /leads/campaigns", err is None, err or "ok")

c, d, err = call("POST", "/leads/inbound", body={
    "name": "E2E Inbound", "email": f"inbound.{STAMP}@example.com",
    "phone": "+919811111111", "message": "Interested in the full-stack track",
    "leadType": "B2C"}, expect=(200, 201))
rec("F6 POST /leads/inbound (public marketing form)", c in (200, 201), err or f"HTTP {c}")

print("\n=== PART G — admin (admin@moriah.test, mandatory 2FA setup) ===")
try:
    mysql("UPDATE users SET two_factor_enabled=0, two_factor_secret=NULL WHERE email='admin@moriah.test'")
    print("  (reset admin 2FA to setup-required for a reproducible run)")
except Exception as e:
    print(f"  (could not reset admin 2FA: {e})")
c, d, err = call("POST", "/auth/login", body={"email": "admin@moriah.test", "password": "Password123!"})
adm_token = None
if err is None and d.get("twoFactorRequired"):
    ch = d["challengeToken"]
    if d.get("twoFactorSetupRequired"):
        c, s, err = call("POST", "/auth/2fa/enable", body={"challengeToken": ch}, expect=(200, 201))
        secret = s.get("secret") if err is None else None
        if secret:
            for attempt in range(3):
                code = totp(secret)
                c, v, err = call("POST", "/auth/2fa/verify",
                                 body={"challengeToken": ch, "totpCode": code}, expect=(200, 201))
                if err is None and isinstance(v, dict):
                    adm_token = (v.get("tokens") or {}).get("accessToken") or v.get("accessToken")
                    break
                time.sleep(2)
        rec("G1 admin 2FA setup (enable -> TOTP -> verify)", adm_token is not None, err or "session issued")
    else:
        rec("G1 admin already has 2FA — cannot complete headless", False, "skipped")
elif err is None:
    adm_token = d["tokens"]["accessToken"]
    rec("G1 admin login (no 2FA challenge)", True, "session issued")
else:
    rec("G1 admin login", False, err)

if adm_token:
    for step, path in [
        ("G2 GET /admin/metrics/overview", "/admin/metrics/overview"),
        ("G3 GET /admin/metrics/revenue", "/admin/metrics/revenue"),
        ("G4 GET /admin/users", "/admin/users"),
        ("G5 GET /admin/payments/summary", "/admin/payments/summary"),
        ("G6 GET /admin/coupons", "/admin/coupons"),
        ("G7 GET /admin/client-requests", "/admin/client-requests"),
        ("G8 GET /admin/audit", "/admin/audit"),
    ]:
        c, d, err = call("GET", path, adm_token)
        rec(step, err is None, err or "ok")
    c, d, err = call("GET", "/admin/plans", adm_token, expect=(405,))
    rec("G9 GET /admin/plans (POST/PUT/DELETE-only) -> 405 not 500 (handler fix)", c == 405, err or "HTTP 405")

print("\n" + "=" * 60)
passed = sum(1 for _, ok, _ in results if ok)
failed = [s for s, ok, _ in results if not ok]
print(f"TOTAL: {passed}/{len(results)} passed")
if failed:
    print("FAILED steps: " + ", ".join(failed))
    sys.exit(1)
print("ALL STEPS PASSED")
sys.exit(0)
