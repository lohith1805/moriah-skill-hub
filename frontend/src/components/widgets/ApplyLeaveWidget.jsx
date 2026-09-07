import { useEffect, useState } from "react";
import { CalendarPlus, CalendarClock } from "lucide-react";
import Card, { CardHeader } from "../ui/Card";
import Badge from "../ui/Badge";
import Button from "../ui/Button";
import Modal from "../ui/Modal";
import { Input, Select, Textarea } from "../ui/FormField";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { getMyLeaveRequests, submitLeaveRequest, LEAVE_TYPES } from "../../services/hrService";
import { validateForm, required } from "../../utils/validators";

// Drop this into any staff dashboard (Trainer, Developer, BA, Lead Gen…) to
// let that person submit a leave request against POST /api/v1/hr/leaves and
// see their own via GET /api/v1/hr/leaves. Requires the caller to have an
// employees record — the backend rejects the submit otherwise.
export default function ApplyLeaveWidget({ role }) {
  const { user } = useAuth();
  const { notify } = useToast();
  const [myLeaves, setMyLeaves] = useState([]);
  const [loading, setLoading] = useState(true);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [values, setValues] = useState({ type: "CASUAL", from: "", to: "", reason: "" });
  const [errors, setErrors] = useState({});
  const [notEnrolled, setNotEnrolled] = useState(false);

  const load = () => {
    if (!user?.uuid) return;
    setLoading(true);
    getMyLeaveRequests(user.uuid)
      .then(setMyLeaves)
      .catch(() => setMyLeaves([]))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, [user]);

  const openModal = () => {
    setValues({ type: "CASUAL", from: "", to: "", reason: "" });
    setErrors({});
    setModalOpen(true);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const validation = validateForm(values, { from: [required], to: [required] });
    if (values.from && values.to && values.to < values.from) {
      validation.to = "The end date can't be before the start date.";
    }
    setErrors(validation);
    if (Object.keys(validation).length) return;

    setSubmitting(true);
    try {
      await submitLeaveRequest({
        leaveType: values.type,
        from: values.from,
        to: values.to,
        reason: values.reason,
      });
      notify("Leave request submitted — HR will review it shortly.", { type: "success", title: "Leave Requested" });
      setModalOpen(false);
      load();
    } catch (err) {
      if (err?.status === 404) {
        // Backend: EMPLOYEE_NOT_FOUND — no employees row is linked to this account.
        setNotEnrolled(true);
        setModalOpen(false);
        notify("No employee record is linked to your account yet — ask HR to add you.", { type: "error" });
      } else {
        notify(err.message || "Could not submit the leave request.", { type: "error" });
      }
    } finally {
      setSubmitting(false);
    }
  };

  const pendingCount = myLeaves.filter((l) => l.status === "Pending").length;

  return (
    <>
      <Card>
        <div className="flex items-center justify-between mb-4">
          <CardHeader title="My Leave Requests" subtitle="Apply for time off — HR will approve or reject it" />
          <Button size="sm" icon={CalendarPlus} onClick={openModal} disabled={notEnrolled}>Apply for Leave</Button>
        </div>
        {notEnrolled && (
          <p className="text-xs text-warning-700 bg-warning-50 border border-warning-100 rounded-md px-3 py-2 mb-3">
            No employee record is linked to your account yet — ask HR to add you on the Employees screen before you can apply for leave.
          </p>
        )}
        {loading ? (
          <p className="text-sm text-ink-400 py-6 text-center">Loading…</p>
        ) : myLeaves.length === 0 ? (
          <p className="text-sm text-ink-400 py-6 text-center">You haven't applied for any leave yet.</p>
        ) : (
          <div className="flex flex-col divide-y divide-border">
            {myLeaves.slice(0, 5).map((l) => (
              <div key={l.id} className="flex items-center justify-between py-3 text-left gap-3">
                <div className="min-w-0">
                  <p className="text-sm font-medium text-ink-900 flex items-center gap-1.5">
                    <CalendarClock size={13} className="text-ink-400 shrink-0" /> {l.type}
                  </p>
                  <p className="text-xs text-ink-500">{l.from} → {l.to}{l.reason ? ` · ${l.reason}` : ""}</p>
                </div>
                <Badge tone={l.status === "Approved" ? "success" : l.status === "Pending" ? "warning" : "error"}>{l.status}</Badge>
              </div>
            ))}
          </div>
        )}
        {pendingCount > 0 && (
          <p className="text-xs text-warning-600 mt-2">{pendingCount} request{pendingCount > 1 ? "s" : ""} awaiting HR approval.</p>
        )}
      </Card>

      <Modal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title="Apply for Leave"
        description="Submit a leave request for HR to review and approve."
        footer={
          <>
            <Button variant="secondary" onClick={() => setModalOpen(false)}>Cancel</Button>
            <Button icon={CalendarPlus} loading={submitting} onClick={handleSubmit}>Submit Request</Button>
          </>
        }
      >
        <form className="flex flex-col gap-4 text-left font-sans" onSubmit={handleSubmit}>
          <Select
            label="Leave Type"
            required
            options={LEAVE_TYPES}
            value={values.type}
            onChange={(e) => setValues((v) => ({ ...v, type: e.target.value }))}
          />
          <div className="grid sm:grid-cols-2 gap-4">
            <Input
              label="From"
              type="date"
              required
              value={values.from}
              onChange={(e) => setValues((v) => ({ ...v, from: e.target.value }))}
              error={errors.from}
            />
            <Input
              label="To"
              type="date"
              required
              value={values.to}
              onChange={(e) => setValues((v) => ({ ...v, to: e.target.value }))}
              error={errors.to}
            />
          </div>
          <Textarea
            label="Reason (optional)"
            rows={3}
            placeholder="Briefly explain the reason for your leave..."
            value={values.reason}
            onChange={(e) => setValues((v) => ({ ...v, reason: e.target.value }))}
          />
        </form>
      </Modal>
    </>
  );
}
