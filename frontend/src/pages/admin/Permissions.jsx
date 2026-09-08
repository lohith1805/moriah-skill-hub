import { Fragment, useEffect, useMemo, useState } from "react";
import { ShieldCheck, Save, Info } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Button from "../../components/ui/Button";
import Badge from "../../components/ui/Badge";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import { useToast } from "../../context/ToastContext";
import { getPermissionMatrix, setRolePermissions } from "../../services/adminService";
import { BACKEND_ROLE_TO_FE, ROLE_LABELS } from "../../utils/constants";

// Order roles most-privileged first; hide the ones that never carry staff
// capabilities so the grid stays readable.
const ROLE_ORDER = ["ADMIN", "HR_MANAGER", "TRAINER_PM", "BUSINESS_ANALYST", "LEAD_GEN", "DEVELOPER", "CLIENT", "STUDENT"];
const roleLabel = (code) => ROLE_LABELS[BACKEND_ROLE_TO_FE[code]] || code;

export default function AdminPermissions() {
  const { notify } = useToast();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [permissions, setPermissions] = useState([]);
  // { [roleCode]: Set<code> } — the working copy the checkboxes mutate
  const [grants, setGrants] = useState({});
  const [saved, setSaved] = useState({}); // last-persisted copy, for the dirty check
  const [savingRole, setSavingRole] = useState(null);

  const applyMatrix = (m) => {
    setPermissions(m.permissions);
    const next = {};
    for (const r of m.roles) next[r.role] = new Set(r.permissionCodes);
    setGrants(next);
    setSaved(next);
  };

  useEffect(() => {
    getPermissionMatrix()
      .then(applyMatrix)
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  }, []);

  const roles = useMemo(
    () => ROLE_ORDER.filter((rc) => grants[rc] !== undefined),
    [grants]
  );

  const byModule = useMemo(() => {
    const map = new Map();
    for (const p of permissions) {
      if (!map.has(p.category)) map.set(p.category, []);
      map.get(p.category).push(p);
    }
    return [...map.entries()];
  }, [permissions]);

  const isDirty = (rc) => {
    const a = grants[rc] || new Set();
    const b = saved[rc] || new Set();
    return a.size !== b.size || [...a].some((x) => !b.has(x));
  };

  const toggle = (rc, code) => {
    setGrants((g) => {
      const next = new Set(g[rc]);
      next.has(code) ? next.delete(code) : next.add(code);
      return { ...g, [rc]: next };
    });
  };

  const save = async (rc) => {
    setSavingRole(rc);
    try {
      const m = await setRolePermissions(rc, [...(grants[rc] || [])]);
      applyMatrix(m);
      notify(`${roleLabel(rc)} permissions updated.`, { type: "success", title: "Saved" });
    } catch (e) {
      notify(e?.message || "Could not save permissions.", { type: "error" });
    } finally {
      setSavingRole(null);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Role Permissions"
        subtitle="Fine-grained capability grants per role. Framework only — not yet enforced on any endpoint (every route still gates on its role)."
        breadcrumbs={[{ label: "Dashboard", to: "/admin/dashboard" }, { label: "Role Permissions" }]}
      />

      <Card className="flex items-start gap-2.5 bg-info-50/60 border-info-200">
        <Info size={16} className="text-info-600 mt-0.5 shrink-0" />
        <p className="text-xs text-ink-600 leading-relaxed">
          Editing a role here records an audit entry and takes effect immediately for any future
          <code className="mx-1 rounded bg-cream-100 px-1">@perms.has(&hellip;)</code>check. No endpoint
          uses those checks yet, so today this is configuration for later. ADMIN keeps every
          capability regardless of what's ticked.
        </p>
      </Card>

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading permissions…" /></div>
      ) : error ? (
        <Card><p className="text-sm text-ink-500 py-8 text-center">Couldn't load the permission matrix.</p></Card>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm border-separate border-spacing-0">
            <thead>
              <tr>
                <th className="sticky left-0 z-10 bg-cream-50 text-left font-semibold text-ink-700 px-3 py-2 border-b border-border">
                  Capability
                </th>
                {roles.map((rc) => (
                  <th key={rc} className="px-3 py-2 border-b border-border text-center align-bottom">
                    <div className="font-semibold text-ink-800">{roleLabel(rc)}</div>
                    <div className="mt-1.5">
                      <Button
                        size="sm"
                        variant={isDirty(rc) ? "primary" : "secondary"}
                        icon={Save}
                        loading={savingRole === rc}
                        disabled={!isDirty(rc)}
                        onClick={() => save(rc)}
                      >
                        Save
                      </Button>
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {byModule.map(([module, perms]) => (
                <Fragment key={`m-${module}`}>
                  <tr>
                    <td
                      colSpan={roles.length + 1}
                      className="bg-cream-100/70 px-3 py-1.5 text-[11px] font-bold uppercase tracking-wide text-ink-500 border-b border-border"
                    >
                      {module}
                    </td>
                  </tr>
                  {perms.map((p) => (
                    <tr key={p.code} className="hover:bg-cream-50/60">
                      <td className="sticky left-0 z-10 bg-white px-3 py-2 border-b border-border/70">
                        <div className="text-ink-800">{p.label}</div>
                        <div className="text-[11px] text-ink-400 font-mono">{p.code}</div>
                      </td>
                      {roles.map((rc) => {
                        const on = grants[rc]?.has(p.code);
                        const locked = rc === "ADMIN";
                        return (
                          <td key={rc} className="px-3 py-2 border-b border-border/70 text-center">
                            <input
                              type="checkbox"
                              className="h-4 w-4 accent-primary-600 disabled:opacity-40"
                              checked={locked ? true : !!on}
                              disabled={locked}
                              onChange={() => toggle(rc, p.code)}
                            />
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </Fragment>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
