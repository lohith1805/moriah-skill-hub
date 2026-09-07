# `scripts/` — documentation generators

These regenerate the human-readable docs in `docs/` from **one source of truth**:
`docs/openapi.json` (the OpenAPI 3.1 spec the running app publishes) plus a static scan of
`src/main/java`. They exist so the docs don't drift when the API changes.

All are **plain Python 3, standard library only** — no `pip install`, no network. Run them from
anywhere (`ROOT` is derived from the script's own location).

`scripts/backup/` is unrelated (a DB backup helper); it is not part of this.

---

## The source: `docs/openapi.json`

The machine-readable spec — every controller endpoint: path, method, params, request-body schema,
response schema, tag. Produced by springdoc from the **running app** (dev profile only):

```bash
# terminal 1
mvn spring-boot:run
# terminal 2 (once "Started SkillHubApplication" appears)
curl http://localhost:8080/v3/api-docs -o docs/openapi.json
```

Filter-handled endpoints (the OAuth2 login redirects) are **not** in it — springdoc only sees
`@RestController` methods. The generators add those back as hardcoded notes.

---

## The generators

| Script | Reads | Writes | What it is |
|---|---|---|---|
| `gen-api-doc.py` | `docs/openapi.json` + greps `**/*Controller.java` for `@PreAuthorize` | `docs/API-Documentation.md` | quick per-endpoint reference: auth/role, request-body example, response example, status codes |
| `gen-callgraph.py` | `src/main/java/**` (regex scan) | `scripts/callgraph-fragments.json` *(intermediate — not a deliverable)* | the `Controller → Service → Repository → table` call chain per endpoint, with the SQL for `@Query` / `JdbcTemplate` methods |
| `gen-project-ref.py` | `docs/openapi.json` + `callgraph-fragments.json` + `project-ref-narrative.md` + `project-ref-audit-appendix.md` | `docs/PROJECT-REFERENCE.md` | the deep single doc: project narrative + schema + every endpoint's call chain + shapes + the audit appendix |
| `gen-postman.py` | `docs/openapi.json` + `@PreAuthorize` greps | `docs/postman/` → `Moriah-Skill-Hub.postman_collection.json`, `Moriah-Local.postman_environment.json`, `README.md`, `moriah-skillhub-postman.zip` | importable Postman collection (login auto-captures the token; webhooks self-sign) |

### Prose parts (hand-written, edit these)

- `project-ref-narrative.md` — Part 1 (project) + Part 2 (architecture) + schema of `PROJECT-REFERENCE.md`
- `project-ref-audit-appendix.md` — Part 4 (issues found & how they were fixed) + Part 5 (running/testing)

`gen-project-ref.py` sandwiches the auto-generated endpoint section (Part 3) between these two.
Change the narrative/audit wording here — the endpoint list itself always comes from
`openapi.json`.

---

## Regenerate everything (after an API change)

```bash
# 1. refresh the spec (see above)
curl http://localhost:8080/v3/api-docs -o docs/openapi.json

# 2. run in this order — callgraph must run before project-ref
python scripts/gen-api-doc.py
python scripts/gen-callgraph.py
python scripts/gen-project-ref.py
python scripts/gen-postman.py
```

Only changed controller annotations, not endpoint shapes? `gen-callgraph.py` +
`gen-project-ref.py` is enough.

---

## Caution

- These **overwrite** `docs/API-Documentation.md`, `docs/PROJECT-REFERENCE.md`, and
  `docs/postman/*`. Do **not** hand-edit those files — put persistent prose in the two
  `project-ref-*.md` files (or the generators' hardcoded strings).
- **Hand-written docs with no generator** (safe to edit directly): `docs/testing-flow.md`,
  `docs/webhooks.md`, `docs/required-integrations.md`, `docs/audit-2026-08-31.md`,
  `docs/runbook-dr.md`.
- `gen-callgraph.py` is best-effort static regex analysis — accurate for this codebase's
  consistent style, but a heavily reflective or dynamically-dispatched path may not fully expand.
