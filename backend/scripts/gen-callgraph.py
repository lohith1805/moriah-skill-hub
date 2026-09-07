"""Builds a controller -> service -> repository -> table call trace for every endpoint,
and emits it as markdown fragments. Static regex analysis, best-effort but accurate for
this codebase's consistent style."""
import json, re, os, glob, pathlib

ROOT = str(pathlib.Path(__file__).resolve().parent.parent)
SRC = os.path.join(ROOT, "src", "main", "java", "com", "moriah", "skillhub")

def read(p):
    return open(p, encoding="utf-8").read()

# ---------- entity -> table ----------
entity_table = {}
for p in glob.glob(os.path.join(SRC, "**", "entity", "*.java"), recursive=True):
    s = read(p)
    cls = re.search(r'public class (\w+)', s)
    tbl = re.search(r'@Table\(\s*name\s*=\s*"([^"]+)"', s)
    if cls:
        name = cls.group(1)
        if tbl:
            entity_table[name] = tbl.group(1)
        else:
            # snake_case fallback + naive pluralise
            sn = re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()
            entity_table[name] = sn + ("" if sn.endswith("s") else "s")

# ---------- repository -> {entity, methods{name: sql|'derived'}} ----------
repos = {}
for p in glob.glob(os.path.join(SRC, "**", "repository", "*.java"), recursive=True):
    s = read(p)
    m = re.search(r'interface (\w+)\s+extends\s+\w*Repository<\s*(\w+)', s)
    if not m:
        m2 = re.search(r'interface (\w+)', s)
        if not m2:
            continue
        rname, ent = m2.group(1), None
    else:
        rname, ent = m.group(1), m.group(2)
    methods = {}
    # each method decl: `<ReturnType> name(` ending in ';' — look back at its annotation block for @Query
    for qm in re.finditer(r'\n\s{4}(?:@[\w.]+(?:\([^;{]*?\))?\s*)*'
                          r'[\w<>,\.\?\[\]@ ]+?\s+(\w+)\s*\([^;{]*?\)\s*;', s, re.S):
        name = qm.group(1)
        if name in ("of", "for", "if", "and", "or"):
            continue
        block = qm.group(0)
        qq = re.search(r'@Query\(\s*(?:value\s*=\s*)?(?:"""(.*?)"""|"((?:[^"\\]|\\.)*)")', block, re.S)
        sql = None
        if qq:
            sql = re.sub(r'\s+', ' ', (qq.group(1) or qq.group(2))).strip()
        native = "nativeQuery = true" in block or "nativeQuery=true" in block
        methods[name] = (("[native] " if native else "") + sql) if sql else "derived"
    repos[rname] = {"entity": ent, "table": entity_table.get(ent, ent), "methods": methods}

# ---------- service -> {method: {calls: [...], repos: [...], flags: set}} ----------
def split_methods(src):
    """find `<mods> <ReturnType> name(...args...) {` at 4-space indent, then brace-match the body."""
    out = {}
    for m in re.finditer(r'\n {4}(?:public|private|protected)\s+'
                         r'(?:static\s+|final\s+|<[^>]+>\s+)*'
                         r'[\w.<>,?\[\]]+(?:\s*<[^;{]*?>)?\s+(\w+)\s*\(', src):
        name = m.group(1)
        # opening brace: the next '{' that isn't inside the param list
        p = src.find("(", m.end() - 2)
        depth = 0
        k = p
        while k < len(src):
            if src[k] == "(":
                depth += 1
            elif src[k] == ")":
                depth -= 1
                if depth == 0:
                    break
            k += 1
        i = src.find("{", k)
        semi = src.find(";", k)
        if i < 0 or (semi != -1 and semi < i):
            continue  # abstract/interface decl, no body
        depth, j = 0, i
        while j < len(src):
            if src[j] == "{":
                depth += 1
            elif src[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        # include preamble back to the previous '}' or blank line so a method-level
        # @Transactional / @Async is visible, without bleeding into the previous method
        lo = m.start()
        for _ in range(12):
            nl = src.rfind("\n", 0, lo - 1)
            if nl < 0:
                break
            ln = src[nl + 1:lo].strip()
            if ln == "" or ln.endswith("}") or ln.endswith(";"):
                break
            lo = nl
        out.setdefault(name, "")
        out[name] += "\n" + src[lo:j + 1]
    return out

services = {}
for p in glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True):
    if os.sep + "entity" + os.sep in p or os.sep + "dto" + os.sep in p or p.endswith("Controller.java"):
        continue
    src = read(p)
    if "@Service" not in src and "@Component" not in src:
        continue
    cls = re.search(r'public class (\w+)', src)
    if not cls:
        continue
    cname = cls.group(1)
    m = {}
    for name, body in split_methods(src).items():
        repo_calls = sorted(set(re.findall(r'\b(\w+Repository)\.(\w+)\(', body)))
        svc_calls = sorted(set(re.findall(r'\b(\w+Service)\.(\w+)\(', body)) - {(cname, name)})
        # raw JdbcTemplate SQL (replica reads, webhook idempotency, exports, metrics)
        raw_sql = []
        for jm in re.finditer(r'\b(\w*[jJ]dbcTemplate)\.(query|queryForObject|queryForList|update|batchUpdate)\(\s*'
                              r'(?:"""(.*?)"""|"((?:[^"\\]|\\.)*)")', body, re.S):
            tmpl, op, tb, pl = jm.groups()
            sql = re.sub(r'\s+', ' ', (tb or pl)).strip()
            repl = "replica" in tmpl.lower()
            raw_sql.append((("replica " if repl else "") + op, sql[:170] + ("…" if len(sql) > 170 else "")))
        flags = []
        if re.search(r'@Transactional', body):
            flags.append("@Transactional")
        if "storageService." in body or "s3" in body.lower():
            flags.append("S3")
        if "eventPublisher.publishEvent" in body or "publishEvent(" in body:
            flags.append("publishes event")
        if "enqueueAfterCommit" in body or "notificationService." in body:
            flags.append("enqueues notification")
        if "auditLogService.record" in body:
            flags.append("writes audit_logs")
        if "restClient" in body or "RestClient" in body or "githubPrFetcher" in body:
            flags.append("external HTTP")
        if "redisTemplate" in body or "@Cacheable" in body:
            flags.append("Redis")
        m[name] = {"repos": repo_calls, "svc": svc_calls, "flags": flags, "raw_sql": raw_sql}
    services[cname] = m

# ---------- controllers ----------
MAP = {"GetMapping": "GET", "PostMapping": "POST", "PutMapping": "PUT",
       "PatchMapping": "PATCH", "DeleteMapping": "DELETE"}

def norm(p):
    return "/" + "/".join(s for s in re.sub(r"\{[^}]+\}", "{}", p).split("/") if s)

endpoints = []  # dicts
for p in sorted(glob.glob(os.path.join(SRC, "**", "*Controller.java"), recursive=True)):
    s = read(p)
    cls = re.search(r'public class (\w+)', s).group(1)
    base_m = re.search(r'@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]+)"', s)
    base = base_m.group(1) if base_m else ""
    tag_m = re.search(r'@Tag\(name\s*=\s*"([^"]+)"', s)
    tag = tag_m.group(1) if tag_m else cls
    cls_pre = None
    cpos = re.search(r'\bclass\s+\w+', s).start()
    for pm in re.finditer(r'@PreAuthorize\(\s*"([^"]*(?:\\.[^"]*)*)"\s*\)', s):
        if pm.start() < cpos:
            cls_pre = pm.group(1)

    # Find each handler method: a `public <Return> name(...) {` declaration, then brace-match.
    for hm in re.finditer(r'\n(\s{4})public\s+[\w<>,\.\?\[\]\s@]+?\s+(\w+)\s*\(', s):
        handler = hm.group(2)
        # preamble = text from the previous '}' / annotation block up to this decl
        pre_start = s.rfind("\n\n", 0, hm.start())
        preamble = s[max(0, pre_start):hm.start()]
        mapping = re.search(r'@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)(?:\(([^)]*)\))?', preamble)
        if not mapping:
            continue
        ann, args = mapping.group(1), mapping.group(2) or ""
        sub = re.search(r'"([^"]+)"', args)
        sub = sub.group(1) if sub else ""
        method = MAP[ann]
        full = norm((base + "/" + sub) if sub else base)
        prem = re.search(r'@PreAuthorize\(\s*"([^"]*(?:\\.[^"]*)*)"\s*\)', preamble)
        pre = prem.group(1) if prem else cls_pre
        # brace-match the body
        bi = s.find("{", hm.end())
        depth, j = 0, bi
        while j < len(s) and j >= 0:
            if s[j] == "{":
                depth += 1
            elif s[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        body = s[bi:j+1] if bi >= 0 else ""
        svc_calls = sorted(set(re.findall(r'\b(\w+Service)\.(\w+)\(', body)))
        guard_calls = sorted(set(re.findall(r'\b(\w+Guard)\.(\w+)\(', body)))
        endpoints.append({
            "tag": tag, "method": method, "path": full, "controller": cls,
            "handler": handler, "preauthorize": pre,
            "service_calls": svc_calls, "guard_calls": guard_calls,
        })

# ---------- render trace fragments ----------
def repo_line(rc):
    rfield, mname = rc
    rname = rfield[0].upper() + rfield[1:]
    r = repos.get(rname, {})
    tbl = r.get("table", "?")
    sql = r.get("methods", {}).get(mname, "derived")
    if sql == "derived":
        detail = "derived query"
    else:
        detail = "`" + (sql[:160] + ("…" if len(sql) > 160 else "")) + "`"
    return f"`{rname}.{mname}()` → table **`{tbl}`** ({detail})"

def render(ep):
    L = []
    key = f"{ep['method']} {ep['path']}"
    L.append(f"**Handler:** `{ep['controller']}.{ep['handler']}(…)`")
    # service layer
    seen_repo = []
    seen_flags = []
    lines = []
    for sc_field, sm in ep["service_calls"]:
        sc = sc_field[0].upper() + sc_field[1:]
        meth = services.get(sc, {}).get(sm)
        if meth is None:
            lines.append(f"- `{sc}.{sm}(…)`")
            continue
        sub = []
        for rc in meth["repos"]:
            sub.append("    - " + repo_line(rc))
            seen_repo.append(rc)
        for op, sql in meth.get("raw_sql", []):
            sub.append(f"    - `JdbcTemplate.{op}` → `{sql}`")
        for nsc_f, nsm in meth["svc"]:
            nsc = nsc_f[0].upper() + nsc_f[1:]
            nmeth = services.get(nsc, {}).get(nsm)
            sub.append(f"    - `{nsc}.{nsm}(…)`" +
                       (f"  _[{', '.join(nmeth['flags'])}]_" if (nmeth and nmeth['flags']) else ""))
            if nmeth:
                for rc in nmeth["repos"]:
                    sub.append("        - " + repo_line(rc))
                for op, sql in nmeth.get("raw_sql", []):
                    sub.append(f"        - `JdbcTemplate.{op}` → `{sql}`")
                seen_flags += nmeth["flags"]
        fl = ", ".join(meth["flags"])
        lines.append(f"- `{sc}.{sm}(…)`" + (f"  _[{fl}]_" if fl else ""))
        lines.extend(sub)
        seen_flags += meth["flags"]
    for gc in ep["guard_calls"]:
        lines.append(f"- `{gc[0]}.{gc[1]}(…)` — authorization check")
    if lines:
        L.append("**Call chain:**")
        L.extend(lines)
    else:
        L.append("**Call chain:** _(thin handler — see the service class for detail)_")
    if seen_flags:
        L.append("**Side effects:** " + ", ".join(sorted(set(seen_flags))))
    return "\n".join(L)

frag = {}
for ep in endpoints:
    frag[f"{ep['method']} {ep['path']}"] = render(ep)

out = os.path.join(pathlib.Path(__file__).parent, "callgraph-fragments.json")
json.dump({"fragments": frag,
           "repos": {k: v["table"] for k, v in repos.items()},
           "endpoint_count": len(endpoints)},
          open(out, "w", encoding="utf-8"), indent=1)
print("endpoints traced:", len(endpoints), "| repos:", len(repos), "| entities:", len(entity_table))
print("keys sample:", sorted(frag.keys())[:5])
