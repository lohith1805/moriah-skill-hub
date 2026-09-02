import json, re, os, glob, textwrap

import pathlib
ROOT = str(pathlib.Path(__file__).resolve().parent.parent)
OPENAPI = os.path.join(ROOT, "docs", "openapi.json")
OUT = os.path.join(ROOT, "docs", "API-Documentation.md")

spec = json.load(open(OPENAPI, encoding="utf-8"))
schemas = spec.get("components", {}).get("schemas", {})

# ---------- example synthesis from a JSON schema ----------
def resolve(node):
    if isinstance(node, dict) and "$ref" in node:
        name = node["$ref"].split("/")[-1]
        return schemas.get(name, {}), name
    return node, None

def example(node, seen=None, depth=0):
    seen = seen or set()
    node, name = resolve(node)
    if name and name in seen:
        return {"...": "recursive"} if (node.get("type") == "object") else None
    if name:
        seen = seen | {name}
    if not isinstance(node, dict):
        return None
    if depth > 8:
        return None

    # Success envelope: show error as null, success as true (not a populated ErrorDetail).
    if name and name.startswith("ApiResponse"):
        props = node.get("properties", {})
        out = {"success": True}
        if "data" in props:
            out["data"] = example(props["data"], seen, depth + 1)
        out["error"] = None
        return out

    if "allOf" in node:
        merged = {}
        for part in node["allOf"]:
            v = example(part, seen, depth + 1)
            if isinstance(v, dict):
                merged.update(v)
        return merged or example(node.get("allOf", [{}])[0], seen, depth + 1)
    if "oneOf" in node or "anyOf" in node:
        return example((node.get("oneOf") or node.get("anyOf"))[0], seen, depth + 1)

    if "example" in node:
        return node["example"]
    if "default" in node and node.get("type") not in ("object", "array"):
        return node["default"]
    if "enum" in node and node["enum"]:
        return node["enum"][0]

    t = node.get("type")
    if t == "object" or ("properties" in node):
        out = {}
        for pname, pschema in node.get("properties", {}).items():
            out[pname] = example(pschema, seen, depth + 1)
        if not out and node.get("additionalProperties"):
            out = {"key": example(node["additionalProperties"], seen, depth + 1)}
        return out
    if t == "array":
        return [example(node.get("items", {}), seen, depth + 1)]
    if t == "string":
        fmt = node.get("format")
        return {
            "date-time": "2026-01-15T10:30:00Z",
            "date": "2026-01-15",
            "uuid": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            "email": "user@example.com",
            "binary": "<binary>",
            "byte": "base64string",
        }.get(fmt, "string")
    if t == "integer":
        return 0
    if t == "number":
        return 0.0
    if t == "boolean":
        return True
    return None

def jdump(v):
    return json.dumps(v, indent=2, ensure_ascii=False)

# ---------- @PreAuthorize / role map from the controllers ----------
auth_map = {}      # "METHOD /norm/path" -> preauthorize string
MAP_ANN = {
    "GetMapping": "GET", "PostMapping": "POST", "PutMapping": "PUT",
    "PatchMapping": "PATCH", "DeleteMapping": "DELETE",
}
def norm(p):
    p = re.sub(r"\{[^}]+\}", "{}", p)
    return "/" + "/".join(seg for seg in p.split("/") if seg)

PREAUTH = r'@PreAuthorize\(\s*"([^"]*(?:\\.[^"]*)*)"\s*\)'
for f in glob.glob(os.path.join(ROOT, "src", "main", "java", "**", "*Controller.java"), recursive=True):
    src = open(f, encoding="utf-8").read()
    m = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"', src)
    base = m.group(1) if m else ""

    # class-level @PreAuthorize: the last one that appears before "public class"/"class X"
    class_pos = re.search(r'\bclass\s+\w+', src)
    class_pos = class_pos.start() if class_pos else len(src)
    class_preauth = None
    for pm in re.finditer(PREAUTH, src):
        if pm.start() < class_pos:
            class_preauth = pm.group(1)

    pending_preauth = None
    for tok in re.finditer(
        PREAUTH +
        r'|@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\(([^)]*)\)',
        src):
        if tok.start() < class_pos:
            continue
        if tok.group(1) is not None:
            pending_preauth = tok.group(1)
            continue
        ann, args = tok.group(2), tok.group(3) or ""
        pm = re.search(r'"([^"]+)"', args)
        sub = pm.group(1) if pm else ""
        method = MAP_ANN.get(ann)
        if ann == "RequestMapping":
            mm = re.search(r'RequestMethod\.(\w+)', args)
            method = mm.group(1) if mm else "GET"
        full = norm((base + "/" + sub).replace("//", "/")) if sub else norm(base)
        auth_map[f"{method} {full}"] = pending_preauth or class_preauth or "(none — public)"
        pending_preauth = None

PUBLIC_PREFIXES = ("/api/v1/auth/", "/api/v1/webhooks/", "/api/v1/portfolio/",
                   "/api/v1/certificates/verify/")
PUBLIC_EXACT = ("/api/v1/plans",)

def is_public(path):
    p = norm(path)
    return p in PUBLIC_EXACT or any(p.startswith(x) or p == x.rstrip("/") for x in PUBLIC_PREFIXES)

def pretty_auth(a, path=""):
    if a == "(none — public)" or a is None:
        if is_public(path):
            return "Public — no token required"
        return "Authenticated (any logged-in user) — no explicit role check on the route"
    a = a.strip()
    if "isAuthenticated()" in a:
        return "Authenticated (any logged-in user)"
    if "permitAll()" in a:
        return "Public — no token required"
    m = re.search(r'has(Any)?Role\(([^)]*)\)', a)
    if m:
        roles = [r.strip().strip("'\"") for r in m.group(2).split(",")]
        return "Role: " + " or ".join(roles)
    return "`" + a + "`"

# ---------- render ----------
tag_order = []
by_tag = {}
for path, ops in spec["paths"].items():
    for method, op in ops.items():
        if method not in ("get", "post", "put", "patch", "delete"):
            continue
        tag = (op.get("tags") or ["Other"])[0]
        by_tag.setdefault(tag, [])
        if tag not in tag_order:
            tag_order.append(tag)
        by_tag[tag].append((path, method.upper(), op))

tag_order.sort()

L = []
def w(s=""):
    L.append(s)

w("# Moriah Skill Hub — API Documentation")
w()
w(f"> Generated from `docs/openapi.json` (OpenAPI {spec.get('openapi')}). "
  f"{sum(len(v) for v in by_tag.values())} endpoints across {len(tag_order)} groups. "
  "Auth/role column is read from each controller's `@PreAuthorize`.")
w()
w("## Conventions")
w()
w("**Base URL** — `/api/v1` (prefix shown in full on every route below). Dev: `http://localhost:8080`.")
w()
w("**Auth** — Bearer JWT access token in the `Authorization` header on every non-public route:")
w()
w("```")
w("Authorization: Bearer <accessToken>")
w("```")
w()
w("Obtain a token from `POST /api/v1/auth/login` (or the OAuth2 flow). Access tokens last 60 min; "
  "refresh with `POST /api/v1/auth/refresh`. ADMIN / HR_MANAGER accounts must complete a 2FA "
  "challenge on login before a token pair is issued.")
w()
w("**Response envelope** — every endpoint returns this wrapper (never a bare object or list):")
w()
w("```json")
w(jdump({
    "success": True,
    "data": {"...": "the payload described per-endpoint below"},
    "error": None,
    "timestamp": "2026-01-15T10:30:00Z",
}))
w("```")
w()
w("On error, `success` is `false`, `data` is `null`, and `error` is populated "
  "(`fieldErrors` is present only for validation failures):")
w()
w("```json")
w(jdump({
    "success": False,
    "data": None,
    "error": {"code": "VALIDATION_FAILED", "message": "Human-readable reason",
              "fieldErrors": [{"field": "email", "message": "must not be blank"}]},
    "timestamp": "2026-01-15T10:30:00Z",
}))
w("```")
w()
w("Common error codes: `VALIDATION_FAILED` (400), `INVALID_CREDENTIALS` (401), `UNAUTHENTICATED` "
  "(401), `INSUFFICIENT_ROLE` / `NOT_RESOURCE_OWNER` (403), `RESOURCE_NOT_FOUND` (404), "
  "`RATE_LIMIT_EXCEEDED` (429), `INTERNAL_ERROR` (500).")
w()
w("**Pagination** — list endpoints accept `?page=0&size=20&sort=field,asc` (max `size` = 100) "
  "and return:")
w()
w("```json")
w(jdump({
    "success": True,
    "data": {
        "content": [{"...": "row"}],
        "page": 0, "size": 20, "totalElements": 137, "totalPages": 7, "last": False,
    },
    "error": None,
    "timestamp": "2026-01-15T10:30:00Z",
}))
w("```")
w()
w("**Rate limits** — `POST /api/v1/auth/**`: 10/min per client IP. All other routes: 300/min "
  "per bearer token (per client IP when unauthenticated).")
w()
w("**OAuth2 login (not in this list / not in `openapi.json`)** — the Google & GitHub login "
  "endpoints are handled by Spring Security's filter chain, not a controller, so springdoc "
  "cannot document them. Browser flow:")
w()
w("| Step | Endpoint |")
w("|---|---|")
w("| Start | `GET /api/v1/auth/oauth2/authorize/{google\\|github}` → 302 to the provider |")
w("| Provider redirects back | `GET /api/v1/auth/oauth2/callback/{google\\|github}?code=…&state=…` |")
w()
w("On success the callback returns the **same `LoginResponse` envelope as `POST /api/v1/auth/login`** "
  "(token pair, or a 2FA challenge). Register the redirect URI "
  "`{baseUrl}/api/v1/auth/oauth2/callback/{google|github}` with the provider and set the client "
  "id/secret in `.env` (see `docs/required-integrations.md`).")
w()
w("---")
w()
w("## Contents")
w()
for tag in tag_order:
    anchor = tag.lower().replace(" ", "-").replace("/", "")
    w(f"- [{tag}](#{anchor}) — {len(by_tag[tag])} endpoints")
w()
w("---")
w()

for tag in tag_order:
    anchor = tag.lower().replace(" ", "-").replace("/", "")
    w(f"## {tag}")
    w()
    # stable order: by path then method
    order = {"GET": 0, "POST": 1, "PUT": 2, "PATCH": 3, "DELETE": 4}
    for path, method, op in sorted(by_tag[tag], key=lambda x: (x[0], order.get(x[1], 9))):
        w(f"### `{method}` `{path}`")
        w()
        if op.get("summary"):
            w(f"{op['summary']}")
            w()
        key = f"{method} {norm(path)}"
        w(f"**Auth:** {pretty_auth(auth_map.get(key), path)}")
        w()

        params = op.get("parameters", [])
        path_p = [p for p in params if p.get("in") == "path"]
        query_p = [p for p in params if p.get("in") == "query"]
        if path_p:
            w("**Path parameters:**")
            w()
            w("| name | type | description |")
            w("|---|---|---|")
            for p in path_p:
                w(f"| `{p['name']}` | {p.get('schema',{}).get('type','string')} | {p.get('description','')} |")
            w()
        real_q = [p for p in query_p if p["name"] != "pageable"]
        has_pageable = any(p["name"] == "pageable" for p in query_p)
        if real_q:
            w("**Query parameters:**")
            w()
            w("| name | type | required | description |")
            w("|---|---|---|---|")
            for p in real_q:
                w(f"| `{p['name']}` | {p.get('schema',{}).get('type','string')} | "
                  f"{'yes' if p.get('required') else 'no'} | {p.get('description','')} |")
            w()
        if has_pageable:
            w("**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.")
            w()

        rb = op.get("requestBody")
        if rb:
            content = rb.get("content", {})
            js = content.get("application/json") or next(iter(content.values()), {})
            if "multipart/form-data" in content:
                w("**Request body:** `multipart/form-data`")
                w()
                mp = content["multipart/form-data"].get("schema", {})
                mp, _ = resolve(mp)
                for fn, fs in mp.get("properties", {}).items():
                    fs0, _ = resolve(fs)
                    w(f"- `{fn}` — {fs0.get('type','string')}"
                      + (f" ({fs0.get('format')})" if fs0.get("format") else ""))
                w()
            elif js.get("schema"):
                w("**Request body:**")
                w()
                w("```json")
                w(jdump(example(js["schema"])))
                w("```")
                w()

        resp = op.get("responses", {})
        ok_key = next((k for k in ("200", "201", "204") if k in resp), None)
        if ok_key and ok_key != "204":
            r = resp[ok_key]
            rc = r.get("content", {})
            rs = (rc.get("application/json") or rc.get("*/*") or next(iter(rc.values()), {})).get("schema")
            if rs:
                w(f"**Response `{ok_key}`:**")
                w()
                w("```json")
                w(jdump(example(rs)))
                w("```")
                w()
        elif ok_key == "204":
            w("**Response `204`:** no body")
            w()
        codes = ", ".join(f"`{c}`" for c in sorted(resp.keys()))
        w(f"**Status codes:** {codes}")
        w()
        w("---")
        w()

open(OUT, "w", encoding="utf-8").write("\n".join(L))
print("wrote", OUT, os.path.getsize(OUT), "bytes;",
      sum(len(v) for v in by_tag.values()), "endpoints;", len(tag_order), "groups")
