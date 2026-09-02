import { useEffect, useState } from "react";
import {
  UserPlus, FileCheck, Edit, Trash2, Package, Truck, CheckCircle2,
  AlertCircle, ShieldCheck, Eye, Upload
} from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import Modal from "../../components/ui/Modal";
import FileUpload from "../../components/ui/FileUpload";
import { Input, Select } from "../../components/ui/FormField";
import { useToast } from "../../context/ToastContext";
import { validateForm, required } from "../../utils/validators";

const INITIAL_ONBOARDINGS = [];

export default function HrOnboarding() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);

  // Create modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [values, setValues] = useState({
    name: "",
    email: "",
    type: "Intern (Student Track)",
    kyc: "Pending",
    education: "Pending",
    nda: "Pending",
    bgCheck: "Pending",
    courier: "BlueDart",
    trackingId: "",
    files: []
  });

  // Edit modal state
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingId, setEditingId] = useState(null);
  const [editValues, setEditValues] = useState({
    name: "",
    email: "",
    type: "",
    kyc: "Pending",
    education: "Pending",
    nda: "Pending",
    bgCheck: "Pending",
    kitStatus: "Dispatch Pending",
    courier: "",
    trackingId: "",
    status: "In Progress",
    files: []
  });

  const [errors, setErrors] = useState({});
  const { notify } = useToast();

  useEffect(() => {
    const saved = localStorage.getItem("msh_hr_onboardings");
    if (saved) {
      setItems(JSON.parse(saved));
    } else {
      localStorage.setItem("msh_hr_onboardings", JSON.stringify(INITIAL_ONBOARDINGS));
      setItems(INITIAL_ONBOARDINGS);
    }
    setLoading(false);
  }, []);

  const persist = (data) => {
    setItems(data);
    localStorage.setItem("msh_hr_onboardings", JSON.stringify(data));
  };

  const handleCreate = (e) => {
    e.preventDefault();
    const validation = validateForm(values, { name: [required], type: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const hasDocs = values.files && values.files.length > 0;
    const created = {
      id: `o_${Date.now()}`,
      name: values.name,
      email: values.email || "",
      type: values.type,
      kyc: hasDocs ? "Verified" : values.kyc,
      education: hasDocs ? "Verified" : values.education,
      nda: values.nda || "Pending",
      bgCheck: values.bgCheck || "Pending",
      welcomeKit: {
        status: values.trackingId ? "Dispatched" : "Dispatch Pending",
        courier: values.courier || "BlueDart",
        trackingId: values.trackingId || "BD-" + Math.floor(1000000 + Math.random() * 9000000)
      },
      status: "In Progress",
      joinedDate: new Date().toISOString().slice(0, 10),
      files: (values.files || []).map((f) => ({ name: f.name, size: f.size }))
    };

    const updated = [created, ...items];
    persist(updated);
    notify("Onboarding workflow initiated and welcome kit scheduled for dispatch.", { type: "success", title: "Workflow Started" });
    setModalOpen(false);
    setValues({ name: "", email: "", type: "Intern (Student Track)", kyc: "Pending", education: "Pending", nda: "Pending", bgCheck: "Pending", courier: "BlueDart", trackingId: "", files: [] });
  };

  const openEdit = (item) => {
    setEditingId(item.id);
    setEditValues({
      name: item.name,
      email: item.email || "",
      type: item.type,
      kyc: item.kyc,
      education: item.education,
      nda: item.nda || "Pending",
      bgCheck: item.bgCheck || "Pending",
      kitStatus: item.welcomeKit?.status || "Dispatch Pending",
      courier: item.welcomeKit?.courier || "BlueDart",
      trackingId: item.welcomeKit?.trackingId || "",
      status: item.status,
      files: item.files || []
    });
    setErrors({});
    setEditModalOpen(true);
  };

  const handleEditSave = (e) => {
    e.preventDefault();
    const validation = validateForm(editValues, { name: [required], type: [required] });
    setErrors(validation);
    if (Object.keys(validation).length) return;

    const allVerified =
      editValues.kyc === "Verified" &&
      editValues.education === "Verified" &&
      editValues.nda === "Signed" &&
      editValues.kitStatus === "Delivered";

    const updated = items.map((item) => {
      if (item.id === editingId) {
        return {
          ...item,
          name: editValues.name,
          email: editValues.email,
          type: editValues.type,
          kyc: editValues.kyc,
          education: editValues.education,
          nda: editValues.nda,
          bgCheck: editValues.bgCheck,
          welcomeKit: {
            status: editValues.kitStatus,
            courier: editValues.courier,
            trackingId: editValues.trackingId
          },
          status: allVerified ? "Complete" : editValues.status,
          files: editValues.files || []
        };
      }
      return item;
    });

    persist(updated);
    notify("Onboarding record updated.", { type: "success" });
    setEditModalOpen(false);
  };

  const handleDelete = (id) => {
    const target = items.find((i) => i.id === id);
    const updated = items.filter((i) => i.id !== id);
    persist(updated);
    notify(`Onboarding record for "${target?.name}" removed.`, { type: "success" });
  };

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Employee & Student Onboarding"
        subtitle="Automated documentation verification (KYC, Education, NDA) and welcome kit dispatch tracking"
        breadcrumbs={[{ label: "Dashboard", to: "/hr/dashboard" }, { label: "Onboarding" }]}
        action={
          <Button icon={UserPlus} onClick={() => { setErrors({}); setModalOpen(true); }}>
            Start Onboarding
          </Button>
        }
      />

      <Card>
        <Table
          loading={loading}
          data={items}
          columns={[
            {
              key: "name",
              header: "Candidate / Employee",
              className: "text-left font-medium text-ink-900",
              render: (r) => (
                <div>
                  <p className="font-semibold text-ink-900">{r.name}</p>
                  <p className="text-xs text-ink-500">{r.type} · Joined: {r.joinedDate || "Recent"}</p>
                </div>
              )
            },
            {
              key: "kyc",
              header: "KYC / ID Proof",
              className: "text-left",
              render: (r) => <Badge tone={r.kyc === "Verified" ? "success" : "warning"}>{r.kyc}</Badge>
            },
            {
              key: "education",
              header: "Education Docs",
              className: "text-left",
              render: (r) => <Badge tone={r.education === "Verified" ? "success" : "warning"}>{r.education}</Badge>
            },
            {
              key: "nda",
              header: "Signed NDA",
              className: "text-left",
              render: (r) => <Badge tone={r.nda === "Signed" ? "success" : "neutral"}>{r.nda || "Pending"}</Badge>
            },
            {
              key: "welcomeKit",
              header: "Welcome Kit Dispatch",
              className: "text-left",
              render: (r) => (
                <div className="flex items-center gap-1.5">
                  <Package size={14} className="text-ink-400" />
                  <div>
                    <Badge tone={r.welcomeKit?.status === "Delivered" ? "success" : r.welcomeKit?.status === "In Transit" ? "primary" : "warning"}>
                      {r.welcomeKit?.status || "Dispatch Pending"}
                    </Badge>
                    {r.welcomeKit?.trackingId && r.welcomeKit?.trackingId !== "--" && (
                      <p className="text-[10px] text-ink-400 font-mono mt-0.5">{r.welcomeKit.courier}: {r.welcomeKit.trackingId}</p>
                    )}
                  </div>
                </div>
              )
            },
            {
              key: "status",
              header: "Overall",
              className: "text-left",
              render: (r) => <Badge tone={r.status === "Complete" ? "success" : "gold"}>{r.status}</Badge>
            },
            {
              key: "action",
              header: "",
              className: "text-right",
              render: (r) => (
                <div className="flex gap-2 justify-end">
                  <Button size="sm" variant="secondary" icon={Edit} onClick={() => openEdit(r)}>Edit</Button>
                  <Button size="sm" variant="danger" icon={Trash2} onClick={() => handleDelete(r.id)}>Delete</Button>
                </div>
              )
            }
          ]}
        />
      </Card>

      {/* Start Onboarding Modal */}
      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Start Onboarding Workflow (MSH-FR-HR-01)"
        description="Verify candidate credentials, assign welcome kit, and dispatch joining credentials."
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button icon={FileCheck} onClick={handleCreate}>Start Onboarding</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleCreate}>
          <Input label="Full Name" required value={values.name} onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))} error={errors.name} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Email Address" type="email" value={values.email} onChange={(e) => setValues((v) => ({ ...v, email: e.target.value }))} />
            <Select
              label="Role / Onboarding Track"
              required
              options={[
                { value: "Intern (Student Track)", label: "Intern (Student Track)" },
                { value: "Trainer / PM", label: "Trainer / PM" },
                { value: "Internal Staff", label: "Internal Staff" }
              ]}
              value={values.type}
              onChange={(e) => setValues((v) => ({ ...v, type: e.target.value }))}
            />
          </div>

          <div className="grid sm:grid-cols-3 gap-3">
            <Select
              label="KYC Status"
              options={[{ value: "Pending", label: "Pending" }, { value: "Verified", label: "Verified" }]}
              value={values.kyc}
              onChange={(e) => setValues((v) => ({ ...v, kyc: e.target.value }))}
            />
            <Select
              label="Education Docs"
              options={[{ value: "Pending", label: "Pending" }, { value: "Verified", label: "Verified" }]}
              value={values.education}
              onChange={(e) => setValues((v) => ({ ...v, education: e.target.value }))}
            />
            <Select
              label="NDA Agreement"
              options={[{ value: "Pending", label: "Pending" }, { value: "Signed", label: "Signed" }]}
              value={values.nda}
              onChange={(e) => setValues((v) => ({ ...v, nda: e.target.value }))}
            />
          </div>

          <div className="p-3 bg-cream-50 rounded-xl border border-border">
            <p className="text-xs font-semibold text-ink-800 mb-2">Welcome Kit Dispatch Details</p>
            <div className="grid sm:grid-cols-2 gap-3">
              <Select
                label="Courier Partner"
                options={[{ value: "BlueDart", label: "BlueDart Express" }, { value: "DTDC", label: "DTDC" }, { value: "Delhivery", label: "Delhivery" }]}
                value={values.courier}
                onChange={(e) => setValues((v) => ({ ...v, courier: e.target.value }))}
              />
              <Input
                label="Tracking AWB Number"
                placeholder="e.g. BD-89123471"
                value={values.trackingId}
                onChange={(e) => setValues((v) => ({ ...v, trackingId: e.target.value }))}
              />
            </div>
          </div>

          <FileUpload
            label="Upload Identity & Degree Credentials"
            hint="Aadhaar card, PAN card, Degree Certificate (PDF/PNG)"
            multiple
            accept=".pdf,.png,.jpg"
            onChange={(uploaded) => setValues((v) => ({ ...v, files: uploaded }))}
          />
        </form>
      </Modal>

      {/* Edit Onboarding Details Modal */}
      <Modal
        open={editModalOpen}
        onClose={() => setEditModalOpen(false)}
        title="Edit Onboarding & Verification Checklist"
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditModalOpen(false)}>Cancel</Button>
            <Button onClick={handleEditSave}>Save Changes</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleEditSave}>
          <Input label="Candidate Name" required value={editValues.name} onChange={(e) => setEditValues((v) => ({ ...v, name: e.target.value }))} error={errors.name} />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input label="Email" value={editValues.email} onChange={(e) => setEditValues((v) => ({ ...v, email: e.target.value }))} />
            <Select
              label="Track"
              options={[
                { value: "Intern (Student Track)", label: "Intern (Student Track)" },
                { value: "Trainer / PM", label: "Trainer / PM" },
                { value: "Internal Staff", label: "Internal Staff" }
              ]}
              value={editValues.type}
              onChange={(e) => setEditValues((v) => ({ ...v, type: e.target.value }))}
            />
          </div>

          <div className="grid sm:grid-cols-4 gap-2">
            <Select
              label="KYC"
              options={[{ value: "Pending", label: "Pending" }, { value: "Verified", label: "Verified" }]}
              value={editValues.kyc}
              onChange={(e) => setEditValues((v) => ({ ...v, kyc: e.target.value }))}
            />
            <Select
              label="Education"
              options={[{ value: "Pending", label: "Pending" }, { value: "Verified", label: "Verified" }]}
              value={editValues.education}
              onChange={(e) => setEditValues((v) => ({ ...v, education: e.target.value }))}
            />
            <Select
              label="NDA"
              options={[{ value: "Pending", label: "Pending" }, { value: "Signed", label: "Signed" }]}
              value={editValues.nda}
              onChange={(e) => setEditValues((v) => ({ ...v, nda: e.target.value }))}
            />
            <Select
              label="BG Check"
              options={[{ value: "Pending", label: "Pending" }, { value: "In Progress", label: "In Progress" }, { value: "Passed", label: "Passed" }]}
              value={editValues.bgCheck}
              onChange={(e) => setEditValues((v) => ({ ...v, bgCheck: e.target.value }))}
            />
          </div>

          <div className="p-3 bg-cream-50 rounded-xl border border-border">
            <p className="text-xs font-semibold text-ink-800 mb-2">Welcome Kit Dispatch Status</p>
            <div className="grid sm:grid-cols-3 gap-3">
              <Select
                label="Kit Status"
                options={[
                  { value: "Dispatch Pending", label: "Dispatch Pending" },
                  { value: "Dispatched", label: "Dispatched" },
                  { value: "In Transit", label: "In Transit" },
                  { value: "Delivered", label: "Delivered" }
                ]}
                value={editValues.kitStatus}
                onChange={(e) => setEditValues((v) => ({ ...v, kitStatus: e.target.value }))}
              />
              <Input
                label="Courier"
                value={editValues.courier}
                onChange={(e) => setEditValues((v) => ({ ...v, courier: e.target.value }))}
              />
              <Input
                label="Tracking ID"
                value={editValues.trackingId}
                onChange={(e) => setEditValues((v) => ({ ...v, trackingId: e.target.value }))}
              />
            </div>
          </div>
        </form>
      </Modal>
    </div>
  );
}
