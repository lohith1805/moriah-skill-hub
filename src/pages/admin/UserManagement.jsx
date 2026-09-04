import { useEffect, useState } from "react";
import {
  MoreVertical, Ban, CheckCircle2, Edit, UserPlus,
  Building2, Copy, Check, XCircle, Mail, VolumeX, Trash2
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Avatar from "../../components/ui/Avatar";
import Dropdown from "../../components/ui/Dropdown";
import Modal from "../../components/ui/Modal";
import Button from "../../components/ui/Button";
import Tabs from "../../components/ui/Tabs";
import EmptyState from "../../components/ui/EmptyState";
import ConfirmDialog from "../../components/ui/ConfirmDialog";
import { Input, Select } from "../../components/ui/FormField";
import { getAllUsers, updateUserRecord, deleteUserRecord } from "../../services/adminService";
import { getPendingClients, setClientApproval, inviteStaffMember } from "../../services/authService";
import { ROLES, ROLE_LABELS } from "../../utils/constants";
import { useToast } from "../../context/ToastContext";
import { useDebounce } from "../../hooks/useDebounce";
import { validateForm, required, isEmail } from "../../utils/validators";

const INVITABLE_ROLES = [
  ROLES.TRAINER, ROLES.DEVELOPER, ROLES.LEAD_GENERATOR, ROLES.HR, ROLES.BUSINESS_ANALYST, ROLES.ADMIN,
].map((r) => ({ value: r, label: ROLE_LABELS[r] }));

export default function AdminUserManagement() {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const debouncedSearch = useDebounce(search, 250);
  const { notify } = useToast();

  // Edit Account state
  const [editOpen, setEditOpen] = useState(false);
  const [editingUserId, setEditingUserId] = useState(null);
  const [editValues, setEditValues] = useState({ name: "", email: "", role: "", status: "Active" });

  const [errors, setErrors] = useState({});

  // Delete Account state
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  // Pending Corporate Client approvals
  const [pendingClients, setPendingClients] = useState([]);
  const [pendingLoading, setPendingLoading] = useState(true);
  const [decidingId, setDecidingId] = useState(null);

  // Invite Staff modal
  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteValues, setInviteValues] = useState({ name: "", email: "", role: "" });
  const [inviteErrors, setInviteErrors] = useState({});
  const [inviteSending, setInviteSending] = useState(false);
  const [inviteLink, setInviteLink] = useState(null);
  const [linkCopied, setLinkCopied] = useState(false);

  const loadPendingClients = () => {
    setPendingLoading(true);
    getPendingClients()
      .then(setPendingClients)
      .finally(() => setPendingLoading(false));
  };

  useEffect(() => {
    loadPendingClients();
  }, []);

  const decideClient = async (client, approve) => {
    setDecidingId(client.id);
    try {
      await setClientApproval(client.id, approve);
      notify(
        approve
          ? `${client.company || client.name} approved — they can now sign in.`
          : `${client.company || client.name}'s request was rejected.`,
        { type: approve ? "success" : "warning" }
      );
      setPendingClients((list) => list.filter((c) => c.id !== client.id));
      loadUsers();
    } catch (err) {
      notify(err.message || "Couldn't update this request. Please try again.", { type: "error" });
    } finally {
      setDecidingId(null);
    }
  };

  const openInvite = () => {
    setInviteValues({ name: "", email: "", role: "" });
    setInviteErrors({});
    setInviteLink(null);
    setLinkCopied(false);
    setInviteOpen(true);
  };

  const sendInvite = async (e) => {
    e.preventDefault();
    const validation = validateForm(inviteValues, { name: [required], email: [required, isEmail], role: [required] });
    setInviteErrors(validation);
    if (Object.keys(validation).length) return;

    setInviteSending(true);
    try {
      const { inviteLink: link } = await inviteStaffMember(inviteValues);
      setInviteLink(link);
      notify(`Invite sent to ${inviteValues.email}.`, { type: "success", title: "Invite created" });
      loadUsers();
    } catch (err) {
      notify(err.message || "Couldn't send the invite. Please try again.", { type: "error" });
    } finally {
      setInviteSending(false);
    }
  };

  const copyInviteLink = () => {
    if (!inviteLink) return;
    navigator.clipboard?.writeText(inviteLink).then(() => {
      setLinkCopied(true);
      setTimeout(() => setLinkCopied(false), 2000);
    });
  };

  const loadUsers = () => {
    setLoading(true);
    getAllUsers().then((merged) => {
      setUsers(merged);
      setLoading(false);
    });
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const persistUsers = (data) => {
    setUsers(data);
    // Only persist the admin-editable status override — identity fields
    // (name/email/role) always come fresh from the registered-user list on
    // next load, so they can't go stale here.
    localStorage.setItem(
      "msh_users_list",
      JSON.stringify(data.map((u) => ({ id: u.id, status: u.status })))
    );
  };

  const filtered = users.filter((u) => {
    const matchesSearch = u.name.toLowerCase().includes(debouncedSearch.toLowerCase()) || u.email.toLowerCase().includes(debouncedSearch.toLowerCase());
    const matchesRole = !roleFilter || u.role === roleFilter;
    return matchesSearch && matchesRole;
  });

  const toggleStatus = async (user, targetStatus) => {
    const updated = users.map((u) => (u.id === user.id ? { ...u, status: targetStatus } : u));
    persistUsers(updated);
    notify(`${user.name}'s account status set to ${targetStatus}.`, { type: targetStatus === "Suspended" ? "warning" : "success" });
  };

  // Deleting is only offered once an account is Suspended — it's a
  // destructive, unrecoverable action, so it's gated behind an explicit
  // confirm dialog rather than living directly in the row menu.
  const handleDeleteConfirm = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    await deleteUserRecord(deleteTarget.id);
    const updated = users.filter((u) => u.id !== deleteTarget.id);
    setUsers(updated);
    notify(`${deleteTarget.name}'s account has been permanently deleted.`, { type: "success" });
    setDeleting(false);
    setDeleteTarget(null);
  };

  const openEdit = (user) => {
    setEditingUserId(user.id);
    setEditValues({
      name: user.name,
      email: user.email,
      role: user.role,
      status: user.status || "Active"
    });
    setErrors({});
    setEditOpen(true);
  };

  const handleEditSave = async (e) => {
    e.preventDefault();
    const validation = validateForm(editValues, { name: [required], email: [required], role: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    // Name/email/role are identity fields on the real registered-user
    // record, so they need to persist there — otherwise the next
    // getAllUsers() refresh would silently discard them.
    await updateUserRecord(editingUserId, {
      name: editValues.name,
      email: editValues.email,
      role: editValues.role,
    });

    const updated = users.map((u) => {
      if (u.id === editingUserId) {
        return {
          ...u,
          name: editValues.name,
          email: editValues.email,
          role: editValues.role,
          status: editValues.status
        };
      }
      return u;
    });

    persistUsers(updated);
    notify("User account details updated.", { type: "success" });
    setEditOpen(false);
    setEditingUserId(null);
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="User Management"
        subtitle="Staff invites, role and profile edits, account activations, mutes, and suspensions (MSH-FR-ADM-03)"
        breadcrumbs={[{ label: "Dashboard", to: "/admin/dashboard" }, { label: "Users" }]}
        action={<Button icon={UserPlus} onClick={openInvite}>Invite Staff</Button>}
      />

      <Card>
        <Tabs
          tabs={[
            { key: "all", label: `All Users (${users.length})` },
            { key: "pending", label: `Pending Client Approvals${pendingClients.length ? ` (${pendingClients.length})` : ""}` },
          ]}
        >
          {(active) => active === "all" ? (
            <div>
              <div className="flex flex-col sm:flex-row gap-3 mb-4">
                <Input placeholder="Search by name or email…" value={search} onChange={(e) => setSearch(e.target.value)} className="sm:max-w-xs" />
                <Select
                  placeholder="All roles"
                  options={Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label }))}
                  value={roleFilter}
                  onChange={(e) => setRoleFilter(e.target.value)}
                  className="sm:max-w-xs"
                />
              </div>
              <Table
                loading={loading}
                data={filtered}
                columns={[
                  { key: "name", header: "User", className: "text-left", render: (r) => (
                    <div className="flex items-center gap-2.5">
                      <Avatar name={r.name} color={r.avatarColor} size={32} />
                      <div className="text-left">
                        <p className="text-ink-900 font-semibold leading-tight">{r.name}</p>
                        <p className="text-xs text-ink-400">{r.email}</p>
                      </div>
                    </div>
                  ) },
                  { key: "role", header: "Role", className: "text-left", render: (r) => <Badge tone="primary">{ROLE_LABELS[r.role] || r.role}</Badge> },
                  { key: "status", header: "Account Status", className: "text-left", render: (r) => (
                    <Badge tone={r.status === "Suspended" ? "error" : r.status === "Muted" ? "warning" : "success"}>
                      {r.status || "Active"}
                    </Badge>
                  ) },
                  { key: "actions", header: "", className: "text-right", render: (r) => (
                    <Dropdown
                      trigger={<button className="text-ink-400 hover:text-ink-700 p-1 cursor-pointer"><MoreVertical size={16} /></button>}
                      items={[
                        { label: "Edit account", icon: Edit, onClick: () => openEdit(r) },
                        { divider: true },
                        r.status !== "Active" && { label: "Activate account", icon: CheckCircle2, onClick: () => toggleStatus(r, "Active") },
                        r.status !== "Muted" && { label: "Mute (Read-Only)", icon: VolumeX, onClick: () => toggleStatus(r, "Muted") },
                        r.status !== "Suspended" && { label: "Suspend account", icon: Ban, danger: true, onClick: () => toggleStatus(r, "Suspended") },
                        r.status === "Suspended" && { divider: true },
                        r.status === "Suspended" && { label: "Delete account", icon: Trash2, danger: true, onClick: () => setDeleteTarget(r) },
                      ].filter(Boolean)}
                    />
                  ) },
                ]}
              />
            </div>
          ) : (
            <div>
              {pendingLoading ? (
                <p className="text-sm text-ink-400 py-8 text-center">Loading pending requests…</p>
              ) : pendingClients.length === 0 ? (
                <EmptyState
                  icon={Building2}
                  title="No pending Client requests"
                  description="Corporate Client sign-ups will appear here for review before they can access their dashboard."
                />
              ) : (
                <div className="flex flex-col gap-3">
                  {pendingClients.map((client) => (
                    <div key={client.id} className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 rounded-lg border border-border p-4">
                      <div className="flex items-center gap-3">
                        <Avatar name={client.name} color={client.avatarColor} size={36} />
                        <div>
                          <p className="text-sm font-semibold text-ink-900 flex items-center gap-2">
                            {client.company || client.name}
                            <Badge tone="warning">Pending review</Badge>
                          </p>
                          <p className="text-xs text-ink-400 mt-0.5 flex items-center gap-1"><Mail size={11} /> {client.email} · Contact: {client.name}</p>
                        </div>
                      </div>
                      <div className="flex gap-2 shrink-0">
                        <Button size="sm" variant="secondary" icon={XCircle} loading={decidingId === client.id} onClick={() => decideClient(client, false)}>Reject</Button>
                        <Button size="sm" icon={CheckCircle2} loading={decidingId === client.id} onClick={() => decideClient(client, true)}>Approve</Button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </Tabs>
      </Card>

      {/* Invite Staff Modal */}
      <Modal
        open={inviteOpen}
        onClose={() => setInviteOpen(false)}
        title="Invite a Staff Member"
        description="Trainer, Developer, Lead Generator, HR, BA and Admin accounts are created via an invite token."
        footer={
          inviteLink ? (
            <Button onClick={() => setInviteOpen(false)}>Done</Button>
          ) : (
            <>
              <Button variant="secondary" onClick={() => setInviteOpen(false)}>Cancel</Button>
              <Button onClick={sendInvite} loading={inviteSending} icon={UserPlus}>Send Invite</Button>
            </>
          )
        }
      >
        {inviteLink ? (
          <div className="flex flex-col gap-3 text-left font-sans">
            <p className="text-sm text-ink-600">
              Invite generated for <strong className="text-ink-900">{inviteValues.name}</strong> as{" "}
              <strong className="text-ink-900">{ROLE_LABELS[inviteValues.role]}</strong>:
            </p>
            <div className="flex items-center gap-2 rounded-lg border border-border bg-cream-50 p-3">
              <code className="text-xs text-ink-700 flex-1 break-all">{inviteLink}</code>
              <button type="button" onClick={copyInviteLink} className="shrink-0 text-ink-400 hover:text-primary-700 p-1">
                {linkCopied ? <Check size={16} className="text-success-600" /> : <Copy size={16} />}
              </button>
            </div>
          </div>
        ) : (
          <form className="flex flex-col gap-4 text-left font-sans" onSubmit={sendInvite}>
            <Input label="Full Name" required value={inviteValues.name} onChange={(e) => setInviteValues((v) => ({ ...v, name: e.target.value }))} error={inviteErrors.name} />
            <Input label="Email Address" required type="email" value={inviteValues.email} onChange={(e) => setInviteValues((v) => ({ ...v, email: e.target.value }))} error={inviteErrors.email} />
            <Select label="Role" required placeholder="Select role" options={INVITABLE_ROLES} value={inviteValues.role} onChange={(e) => setInviteValues((v) => ({ ...v, role: e.target.value }))} error={inviteErrors.role} />
          </form>
        )}
      </Modal>

      {/* Edit Account Modal */}
      <Modal
        open={editOpen}
        onClose={() => setEditOpen(false)}
        title="Edit User Account & Status"
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditOpen(false)}>Cancel</Button>
            <Button onClick={handleEditSave}>Save Details</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleEditSave}>
          <Input label="Full Name" required value={editValues.name} onChange={(e) => setEditValues((v) => ({ ...v, name: e.target.value }))} error={errors.name} />
          <Input label="Email Address" required type="email" value={editValues.email} onChange={(e) => setEditValues((v) => ({ ...v, email: e.target.value }))} error={errors.email} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Select label="Role" required options={Object.entries(ROLE_LABELS).map(([value, label]) => ({ value, label }))} value={editValues.role} onChange={(e) => setEditValues((v) => ({ ...v, role: e.target.value }))} />
            <Select label="Status" options={[{ value: "Active", label: "Active" }, { value: "Muted", label: "Muted (Read-Only)" }, { value: "Suspended", label: "Suspended" }]} value={editValues.status} onChange={(e) => setEditValues((v) => ({ ...v, status: e.target.value }))} />
          </div>
        </form>
      </Modal>

      {/* Delete Account confirmation */}
      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={handleDeleteConfirm}
        title="Delete this account?"
        description={deleteTarget ? `This permanently deletes ${deleteTarget.name}'s (${deleteTarget.email}) account. This cannot be undone.` : ""}
        confirmLabel="Delete account"
        tone="danger"
        loading={deleting}
      />
    </div>
  );
}