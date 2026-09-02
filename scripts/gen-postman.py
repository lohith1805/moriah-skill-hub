# Builds a Postman Collection v2.1 (role-aware) + a local environment from docs/openapi.json,
# and zips them to docs/postman/moriah-skillhub-postman.zip.
#
# Design:
#  - Auth folder has one Login request per seeded role; its test script stores the token in a
#    ROLE-SPECIFIC collection variable ({{studentAccessToken}}, {{pmAccessToken}}, ...).
#  - Admin / HR have a 3-step 2FA sub-folder; the "Verify 2FA" request computes the current TOTP
#    from {{adminTotpSecret}} in a pre-request script (CryptoJS is built into Postman).
#  - Every other request pins the RIGHT role token via a request-level Bearer auth override,
#    chosen from its controller @PreAuthorize.
#  - Seeded UUIDs are pre-filled collection variables.
import json, re, os, glob, pathlib, zipfile

ROOT = str(pathlib.Path(__file__).resolve().parent.parent)
OPENAPI = os.path.join(ROOT, "docs", "openapi.json")
OUTDIR = os.path.join(ROOT, "docs", "postman")
os.makedirs(OUTDIR, exist_ok=True)
spec = json.load(open(OPENAPI, encoding="utf-8"))
schemas = spec.get("components", {}).get("schemas", {})

# ---------- schema -> example ----------
def resolve(node):
    if isinstance(node, dict) and "$ref" in node:
        n = node["$ref"].split("/")[-1]
        return schemas.get(n, {}), n
    return node, None

def example(node, seen=None, depth=0):
    seen = seen or set()
    node, name = resolve(node)
    if name and name in seen:
        return {}
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

def norm(p):
    return "/" + "/".join(s for s in re.sub(r"\{[^}]+\}", "{}", p).split("/") if s)

# ---------- @PreAuthorize map ----------
SRC = os.path.join(ROOT, "src", "main", "java", "com", "moriah", "skillhub")
PRE = r'@PreAuthorize\(\s*"([^"]*(?:\\.[^"]*)*)"\s*\)'
MAP = {"GetMapping": "GET", "PostMapping": "POST", "PutMapping": "PUT", "PatchMapping": "PATCH", "DeleteMapping": "DELETE"}
auth_map = {}
for f in glob.glob(os.path.join(SRC, "**", "*Controller.java"), recursive=True):
    s = open(f, encoding="utf-8").read()
    bm = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"', s)
    base = bm.group(1) if bm else ""
    cpos = re.search(r'\bclass\s+\w+', s).start()
    cpre = None
    for pm in re.finditer(PRE, s):
        if pm.start() < cpos:
            cpre = pm.group(1)
    for hm in re.finditer(r'\n\s{4}public\s+[\w<>,\.\?\[\]\s@]+?\s+(\w+)\s*\(', s):
        pre = s[max(0, s.rfind("\n\n", 0, hm.start())):hm.start()]
        mp = re.search(r'@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)(?:\(([^)]*)\))?', pre)
        if not mp:
            continue
        sub = re.search(r'"([^"]+)"', mp.group(2) or "")
        full = norm((base + "/" + sub.group(1)) if sub else base)
        pm2 = re.search(PRE, pre)
        auth_map[f"{MAP[mp.group(1)]} {full}"] = (pm2.group(1) if pm2 else cpre) or "(none)"

PUBLIC_PREFIX = ("/api/v1/auth/", "/api/v1/webhooks/", "/api/v1/portfolio/", "/api/v1/certificates/verify/")
def is_public(path):
    p = norm(path)
    return p == "/api/v1/plans" or any(p.startswith(x) for x in PUBLIC_PREFIX)

ROLE_TOKEN = {
    "STUDENT": "studentAccessToken", "TRAINER_PM": "pmAccessToken", "DEVELOPER": "devAccessToken",
    "LEAD_GEN": "salesAccessToken", "HR_MANAGER": "hrAccessToken", "BUSINESS_ANALYST": "baAccessToken",
    "ADMIN": "adminAccessToken", "CLIENT": "clientAccessToken",
}

def roles_for(key):
    a = auth_map.get(key, "(none)")
    m = re.search(r'has(Any)?Role\(([^)]*)\)', a)
    if m:
        return [r.strip().strip("'\"") for r in m.group(2).split(",")]
    return []  # isAuthenticated() / none -> any logged-in user

def auth_block(key, path):
    if is_public(path):
        return {"type": "noauth"}, "Public — no token."
    rs = roles_for(key)
    if not rs:
        return ({"type": "bearer", "bearer": [{"key": "token", "value": "{{studentAccessToken}}", "type": "string"}]},
                "Any logged-in user. Using {{studentAccessToken}} — swap for another role's token var if needed.")
    tok = ROLE_TOKEN.get(rs[0], "studentAccessToken")
    note = "Roles: " + " / ".join(f"{r} ({{{{{ROLE_TOKEN.get(r,'?')}}}}})" for r in rs) + \
           f".  Using {{{{{tok}}}}} — run 'Auth / Login — {rs[0].replace('_',' ').title()}' first."
    return {"type": "bearer", "bearer": [{"key": "token", "value": "{{" + tok + "}}", "type": "string"}]}, note

# ---------- url / body ----------
def url_obj(path, params):
    raw = re.sub(r"\{(\w+)\}", r":\1", path)
    segs = [x for x in raw.split("/") if x]
    q = []
    for p in params:
        if p.get("in") != "query" or p["name"] == "pageable":
            continue
        q.append({"key": p["name"], "value": "", "disabled": not p.get("required", False),
                  "description": p.get("description", "")})
    if any(pp.get("name") == "pageable" for pp in params):
        q += [{"key": "page", "value": "0", "disabled": True},
              {"key": "size", "value": "20", "disabled": True},
              {"key": "sort", "value": "id,desc", "disabled": True}]
    pv = [{"key": p["name"], "value": "1", "description": p.get("description", "")}
          for p in params if p.get("in") == "path"]
    u = {"raw": "{{baseUrl}}/" + "/".join(segs), "host": ["{{baseUrl}}"], "path": segs}
    if q:
        u["query"] = q
    if pv:
        u["variable"] = pv
    return u

def body_for(op):
    rb = op.get("requestBody")
    if not rb:
        return None
    c = rb.get("content", {})
    if "multipart/form-data" in c:
        sch, _ = resolve(c["multipart/form-data"].get("schema", {}))
        fd = []
        for fn, fs in sch.get("properties", {}).items():
            fs0, _ = resolve(fs)
            if fs0.get("format") == "binary":
                fd.append({"key": fn, "type": "file", "src": []})
            else:
                fd.append({"key": fn, "type": "text", "value": ""})
        return {"mode": "formdata", "formdata": fd}
    js = c.get("application/json") or next(iter(c.values()), {})
    sch, _ = resolve(js.get("schema", {}))
    if sch.get("type") == "string" and "properties" not in sch:
        return {"mode": "raw",
                "raw": '{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_TEST1","order_id":"{{razorpayOrderId}}","amount":1499900,"currency":"INR","status":"captured"}}}}',
                "options": {"raw": {"language": "json"}}}
    if js.get("schema"):
        return {"mode": "raw", "raw": json.dumps(example(js["schema"]), indent=2),
                "options": {"raw": {"language": "json"}}}
    return None

# ---------- module folders (everything except /api/v1/auth/**) ----------
order = {"GET": 0, "POST": 1, "PUT": 2, "PATCH": 3, "DELETE": 4}
buckets = {}
for path, ops in spec["paths"].items():
    if norm(path).startswith("/api/v1/auth/"):
        continue
    for method, op in ops.items():
        if method.lower() not in ("get", "post", "put", "patch", "delete"):
            continue
        M = method.upper()
        tag = (op.get("tags") or ["Other"])[0]
        key = f"{M} {norm(path)}"
        auth, note = auth_block(key, path)
        body = body_for(op)
        headers = [{"key": "Content-Type", "value": "application/json"}] if (body and body.get("mode") == "raw") else []
        req = {"method": M, "header": headers, "url": url_obj(path, op.get("parameters", [])),
               "auth": auth,
               "description": (op.get("summary", "") + "\n\n" + note).strip()}
        if body:
            req["body"] = body
        item = {"name": f"{M} {path}", "request": req, "response": []}
        ev = []
        np = norm(path)
        if np == "/api/v1/webhooks/razorpay":
            ev.append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": [
                "var b = (pm.request.body && pm.request.body.raw) || '';",
                "var sig = CryptoJS.HmacSHA256(b, pm.collectionVariables.get('webhookSecret')).toString(CryptoJS.enc.Hex);",
                "pm.request.headers.upsert({ key: 'X-Razorpay-Signature', value: sig });"]}})
        if np == "/api/v1/webhooks/stripe":
            ev.append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": [
                "var b = (pm.request.body && pm.request.body.raw) || '';",
                "var t = Math.floor(Date.now()/1000);",
                "var sig = CryptoJS.HmacSHA256(t + '.' + b, pm.collectionVariables.get('webhookSecret')).toString(CryptoJS.enc.Hex);",
                "pm.request.headers.upsert({ key: 'Stripe-Signature', value: 't=' + t + ',v1=' + sig });"]}})
        if ev:
            item["event"] = ev
        buckets.setdefault(tag, []).append((path, order[M], item))

module_folders = []
for tag in sorted(buckets):
    kids = [it for _, _, it in sorted(buckets[tag], key=lambda x: (x[0], x[1]))]
    module_folders.append({"name": tag, "item": kids})

# ---------- Auth folder (hand-built, role-aware) ----------
def login_req(email, role_prefix):
    return {
        "name": f"Login — {email.split('@')[0]}",
        "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
            "var d = pm.response.json().data || {};",
            "if (d.tokens && d.tokens.accessToken) {",
            f"  pm.collectionVariables.set('{role_prefix}AccessToken', d.tokens.accessToken);",
            f"  pm.collectionVariables.set('{role_prefix}RefreshToken', d.tokens.refreshToken);",
            f"  console.log('{role_prefix}AccessToken set');",
            "}",
            "if (d.challengeToken) {",
            f"  pm.collectionVariables.set('{role_prefix}ChallengeToken', d.challengeToken);",
            f"  console.log('{role_prefix} needs 2FA — challengeToken set');",
            "}"]}}],
        "request": {"method": "POST", "header": [{"key": "Content-Type", "value": "application/json"}],
                    "auth": {"type": "noauth"},
                    "url": {"raw": "{{baseUrl}}/api/v1/auth/login", "host": ["{{baseUrl}}"],
                            "path": ["api", "v1", "auth", "login"]},
                    "body": {"mode": "raw", "options": {"raw": {"language": "json"}},
                             "raw": json.dumps({"email": email, "password": "Password123!"}, indent=2)}},
        "response": []}

TOTP_PREREQ = [
    "// Compute the current TOTP from the stored base32 secret (CryptoJS is built into Postman).",
    "function b32bytes(s){var A='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567',bits='',out=[];",
    "  s=(s||'').replace(/=+$/,'').toUpperCase().replace(/\\s/g,'');",
    "  for(var i=0;i<s.length;i++){var v=A.indexOf(s[i]); if(v<0)continue; bits+=('00000'+v.toString(2)).slice(-5);}",
    "  for(var j=0;j+8<=bits.length;j+=8){out.push(parseInt(bits.substr(j,8),2));} return out;}",
    "var sec = pm.collectionVariables.get('%SECVAR%');",
    "if(!sec){ console.log('set %SECVAR% first (run step 2 / paste the secret)'); return; }",
    "var keyHex = b32bytes(sec).map(function(b){return ('0'+b.toString(16)).slice(-2);}).join('');",
    "var ctr = Math.floor(Date.now()/1000/30);",
    "var ctrHex = ('0000000000000000'+ctr.toString(16)).slice(-16);",
    "var h = CryptoJS.HmacSHA1(CryptoJS.enc.Hex.parse(ctrHex), CryptoJS.enc.Hex.parse(keyHex)).toString(CryptoJS.enc.Hex);",
    "var off = parseInt(h.substr(38,2),16) & 15;",
    "var bin = parseInt(h.substr(off*2,8),16) & 0x7fffffff;",
    "var otp = ('000000'+(bin%1000000)).slice(-6);",
    "pm.collectionVariables.set('%CODEVAR%', otp);",
    "console.log('%CODEVAR% =', otp);",
]

def twofa_folder(email, rp):
    sec, code, ch = f"{rp}TotpSecret", f"{rp}TotpCode", f"{rp}ChallengeToken"
    prereq = [ln.replace("%SECVAR%", sec).replace("%CODEVAR%", code) for ln in TOTP_PREREQ]
    return {"name": f"Login — {email.split('@')[0]} (2FA)", "item": [
        {**login_req(email, rp), "name": "1 · Login (get challengeToken)"},
        {"name": "2 · Enable 2FA (get secret)",
         "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": [
             "var d = pm.response.json().data || {};",
             f"if (d.secret) {{ pm.collectionVariables.set('{sec}', d.secret); console.log('{sec} stored'); }}",
             f"if (d.provisioningUri) pm.collectionVariables.set('{rp}OtpauthUri', d.provisioningUri);"]}}],
         "request": {"method": "POST", "header": [{"key": "Content-Type", "value": "application/json"}],
                     "auth": {"type": "noauth"},
                     "url": {"raw": "{{baseUrl}}/api/v1/auth/2fa/enable", "host": ["{{baseUrl}}"],
                             "path": ["api", "v1", "auth", "2fa", "enable"]},
                     "body": {"mode": "raw", "options": {"raw": {"language": "json"}},
                              "raw": json.dumps({"challengeToken": "{{" + ch + "}}"}, indent=2)},
                     "description": "Only needed on the FIRST 2FA login (twoFactorSetupRequired=true). "
                                    "Stores the base32 secret so step 3 can auto-generate the code."},
         "response": []},
        {"name": "3 · Verify 2FA  → " + rp + "AccessToken",
         "event": [
             {"listen": "prerequest", "script": {"type": "text/javascript", "exec": prereq}},
             {"listen": "test", "script": {"type": "text/javascript", "exec": [
                 "var d = pm.response.json().data || {};",
                 "if (d.tokens && d.tokens.accessToken) {",
                 f"  pm.collectionVariables.set('{rp}AccessToken', d.tokens.accessToken);",
                 f"  pm.collectionVariables.set('{rp}RefreshToken', d.tokens.refreshToken);",
                 f"  console.log('{rp}AccessToken set'); }}"]}}],
         "request": {"method": "POST", "header": [{"key": "Content-Type", "value": "application/json"}],
                     "auth": {"type": "noauth"},
                     "url": {"raw": "{{baseUrl}}/api/v1/auth/2fa/verify", "host": ["{{baseUrl}}"],
                             "path": ["api", "v1", "auth", "2fa", "verify"]},
                     "body": {"mode": "raw", "options": {"raw": {"language": "json"}},
                              "raw": json.dumps({"challengeToken": "{{" + ch + "}}", "totpCode": "{{" + code + "}}"}, indent=2)},
                     "description": "Pre-request script auto-fills {{" + code + "}} from {{" + sec + "}}. "
                                    "If it's blank, paste a code from `python -c \"import pyotp; print(pyotp.TOTP('SECRET').now())\"` "
                                    "into the " + code + " variable. Must run within 5 min of step 1."},
         "response": []},
    ]}

def simple_auth_req(name, path_tail, body, desc="", auth=None, ev=None):
    it = {"name": name, "request": {
        "method": "POST", "header": [{"key": "Content-Type", "value": "application/json"}],
        "auth": auth or {"type": "noauth"},
        "url": {"raw": "{{baseUrl}}/api/v1/auth/" + path_tail, "host": ["{{baseUrl}}"],
                "path": ["api", "v1", "auth"] + path_tail.split("/")},
        "body": {"mode": "raw", "options": {"raw": {"language": "json"}}, "raw": json.dumps(body, indent=2)},
        "description": desc}, "response": []}
    if ev:
        it["event"] = ev
    return it

refresh_ev = lambda rp: [{"listen": "test", "script": {"type": "text/javascript", "exec": [
    "var d = pm.response.json().data || {};",
    f"if (d.accessToken) pm.collectionVariables.set('{rp}AccessToken', d.accessToken);",
    f"if (d.refreshToken) pm.collectionVariables.set('{rp}RefreshToken', d.refreshToken);"]}}]

auth_folder = {"name": "Auth", "item": [
    login_req("student1@moriah.test", "student"),
    login_req("student3@moriah.test", "student3"),   # STARTER — for 403 entitlement tests
    login_req("pm@moriah.test", "pm"),
    login_req("dev@moriah.test", "dev"),
    login_req("sales@moriah.test", "sales"),
    login_req("ba@moriah.test", "ba"),
    login_req("client@moriah.test", "client"),
    twofa_folder("admin@moriah.test", "admin"),
    twofa_folder("hr@moriah.test", "hr"),
    {"name": "Session", "item": [
        simple_auth_req("Refresh — student", "refresh", {"refreshToken": "{{studentRefreshToken}}"},
                        "Rotates the token; both values are replaced.", ev=refresh_ev("student")),
        simple_auth_req("Refresh — pm", "refresh", {"refreshToken": "{{pmRefreshToken}}"}, "", ev=refresh_ev("pm")),
        simple_auth_req("Logout — student", "logout", {"refreshToken": "{{studentRefreshToken}}"}),
        simple_auth_req("Logout-all — student", "logout-all", {"refreshToken": "{{studentRefreshToken}}"},
                        "Bumps token_version — every access token for that user dies on its next request."),
    ]},
    {"name": "Other", "item": [
        simple_auth_req("Register", "register",
                        {"fullName": "New Tester", "email": "new@moriah.test", "phone": "919812345678",
                         "password": "Password123!", "githubUsername": "new-tester"},
                        "Creates a PENDING_VERIFICATION user; needs email verification before login."),
        simple_auth_req("Verify email", "verify-email", {"token": "«from the email»"}),
        simple_auth_req("Forgot password", "password/forgot", {"email": "student1@moriah.test"}),
        simple_auth_req("Reset password", "password/reset", {"token": "«from the email»", "newPassword": "Password123!"}),
        simple_auth_req("Disable 2FA", "2fa/disable", {"totpCode": "{{adminTotpCode}}"},
                        "Requires a currently-valid code AND an authenticated caller.",
                        auth={"type": "bearer", "bearer": [{"key": "token", "value": "{{adminAccessToken}}", "type": "string"}]}),
    ]},
]}

# OAuth2 browser-flow folder
oauth_folder = {"name": "OAuth2 login (browser flow — read the description)", "item": [
    {"name": f"GET /api/v1/auth/oauth2/authorize/{prov}",
     "request": {"method": "GET", "header": [], "auth": {"type": "noauth"},
                 "url": {"raw": f"{{{{baseUrl}}}}/api/v1/auth/oauth2/authorize/{prov}", "host": ["{{baseUrl}}"],
                         "path": ["api", "v1", "auth", "oauth2", "authorize", prov]},
                 "description": "BROWSER ONLY — open this URL in a browser. It 302-redirects to "
                                f"{prov}; after sign-in the provider hits the callback, which returns the same "
                                "LoginResponse JSON as POST /api/v1/auth/login."}, "response": []}
    for prov in ("google", "github")]}

# ---------- collection variables (seeded UUIDs prefilled; carry-vars blank) ----------
UUIDS = {"adminUuid": "01", "pmUuid": "02", "devUuid": "03", "salesUuid": "04", "hrUuid": "05",
         "baUuid": "06", "clientUuid": "07", "student1Uuid": "08", "student2Uuid": "09", "student3Uuid": "10"}
cvars = [{"key": "baseUrl", "value": "http://localhost:8080", "type": "string"}]
for k, tail in UUIDS.items():
    cvars.append({"key": k, "value": "11111111-0000-0000-0000-0000000000" + tail, "type": "string"})
for rp in ["student", "student3", "pm", "dev", "sales", "ba", "client", "admin", "hr"]:
    cvars += [{"key": rp + "AccessToken", "value": "", "type": "string"},
              {"key": rp + "RefreshToken", "value": "", "type": "string"}]
for rp in ["student", "admin", "hr"]:
    cvars.append({"key": rp + "ChallengeToken", "value": "", "type": "string"})
for rp in ["admin", "hr"]:
    cvars += [{"key": rp + "TotpSecret", "value": "", "type": "string"},
              {"key": rp + "TotpCode", "value": "", "type": "string"},
              {"key": rp + "OtpauthUri", "value": "", "type": "string"}]
cvars.append({"key": "webhookSecret", "value": "local_test_secret", "type": "string"})
for k in ["batchId", "sprintId", "taskId", "backlogTaskId", "submissionId", "standupId", "quizId",
          "attemptId", "leadId", "employeeId", "docId", "leaveId", "pipId", "milestoneId",
          "paymentId", "razorpayOrderId", "certId", "verificationCode"]:
    cvars.append({"key": k, "value": "", "type": "string"})

collection = {
    "info": {
        "name": "Moriah Skill Hub API (role-aware)",
        "description": "Generated by scripts/gen-postman.py from docs/openapi.json.\n\n"
                       "## Quick start\n"
                       "1. `Auth / Login — student1` (and any other role you need). Each stores a "
                       "ROLE-SPECIFIC token: {{studentAccessToken}}, {{pmAccessToken}}, {{devAccessToken}}, "
                       "{{salesAccessToken}}, {{baAccessToken}}, {{clientAccessToken}}.\n"
                       "2. Admin / HR: run the 3 steps in `Auth / Login — admin (2FA)` — step 3's pre-request "
                       "script auto-computes the TOTP from the secret step 2 stored. -> {{adminAccessToken}}, {{hrAccessToken}}.\n"
                       "3. Every other request already pins the correct role token (see each request's "
                       "description for which roles are allowed and which var it uses).\n"
                       "4. Seeded user UUIDs are pre-filled variables: {{student1Uuid}} ... {{adminUuid}}. "
                       "Carry-over IDs ({{batchId}}, {{sprintId}}, {{submissionId}}, ...) are blank — set them "
                       "from earlier responses (see docs/testing-flow.md).\n"
                       "5. Webhooks self-sign with {{webhookSecret}} (match your .env).\n"
                       "6. OAuth2 login is a browser flow, not a request — see its folder.\n",
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    },
    "event": [],
    "variable": cvars,
    "item": [oauth_folder, auth_folder] + module_folders,
}

environment = {"name": "Moriah Local", "_postman_variable_scope": "environment",
               "values": [{"key": v["key"], "value": v["value"], "enabled": True} for v in cvars]}

col_path = os.path.join(OUTDIR, "Moriah-Skill-Hub.postman_collection.json")
env_path = os.path.join(OUTDIR, "Moriah-Local.postman_environment.json")
readme_path = os.path.join(OUTDIR, "README.md")
json.dump(collection, open(col_path, "w", encoding="utf-8"), indent=2)
json.dump(environment, open(env_path, "w", encoding="utf-8"), indent=2)
open(readme_path, "w", encoding="utf-8").write(
    "# Moriah Skill Hub — Postman (role-aware)\n\n"
    "Import `Moriah-Skill-Hub.postman_collection.json` (the environment file is optional — every "
    "variable also lives on the collection).\n\n"
    "## Logging in per role\n"
    "`Auth /` has one **Login** request per seeded account. Running it stores a role-specific token:\n\n"
    "| Run this | Sets |\n|---|---|\n"
    "| Login — student1 | `{{studentAccessToken}}` |\n"
    "| Login — student3 (STARTER) | `{{student3AccessToken}}` — for `403 ENTITLEMENT_REQUIRED` tests |\n"
    "| Login — pm | `{{pmAccessToken}}` |\n"
    "| Login — dev | `{{devAccessToken}}` |\n"
    "| Login — sales | `{{salesAccessToken}}` |\n"
    "| Login — ba | `{{baAccessToken}}` |\n"
    "| Login — client | `{{clientAccessToken}}` |\n"
    "| Login — admin (2FA) → steps 1,2,3 | `{{adminAccessToken}}` |\n"
    "| Login — hr (2FA) → steps 1,2,3 | `{{hrAccessToken}}` |\n\n"
    "Every endpoint in the module folders already uses the right one (its description lists which "
    "roles are allowed). Password for all: **Password123!**.\n\n"
    "## Admin / HR 2FA\n"
    "1. **1 · Login** → stores `{{adminChallengeToken}}` (valid 5 min).\n"
    "2. **2 · Enable 2FA** → stores `{{adminTotpSecret}}` (first login only).\n"
    "3. **3 · Verify 2FA** → a pre-request script computes the current 6-digit code from "
    "`{{adminTotpSecret}}` and submits it → `{{adminAccessToken}}`.\n"
    "   If the code is blank, paste one from `python -c \"import pyotp; print(pyotp.TOTP('SECRET').now())\"` "
    "into the `adminTotpCode` variable and resend.\n\n"
    "## Seeded UUIDs (pre-filled variables)\n"
    "`{{student1Uuid}}`=…0008, `{{student2Uuid}}`=…0009, `{{student3Uuid}}`=…0010, `{{pmUuid}}`=…0002, "
    "`{{devUuid}}`=…0003, `{{hrUuid}}`=…0005, `{{adminUuid}}`=…0001, etc.\n\n"
    "## Carry-over IDs\n"
    "`{{batchId}}`, `{{sprintId}}`, `{{submissionId}}`, `{{paymentId}}`, `{{verificationCode}}`, … "
    "are blank — set them from earlier responses. Full sequence: `docs/testing-flow.md`.\n\n"
    "## Webhooks\n"
    "`/webhooks/razorpay` and `/webhooks/stripe` have a pre-request script that signs the body with "
    "`{{webhookSecret}}` — set it to your `.env` `RAZORPAY_WEBHOOK_SECRET` / `STRIPE_WEBHOOK_SECRET`.\n\n"
    "Regenerate: `python scripts/gen-postman.py` (after re-exporting `docs/openapi.json`).\n"
)

zip_path = os.path.join(OUTDIR, "moriah-skillhub-postman.zip")
with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
    z.write(col_path, "Moriah-Skill-Hub.postman_collection.json")
    z.write(env_path, "Moriah-Local.postman_environment.json")
    z.write(readme_path, "README.md")

n = sum(len(v) for v in buckets.values())
print(f"wrote {zip_path}")
print(f"  module requests: {n} in {len(buckets)} folders")
print(f"  auth folder: 7 role logins + 2x 2FA subfolders + Session + Other")
