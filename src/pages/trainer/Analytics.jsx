import { useEffect, useState } from "react";
import { BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, LineChart, Line, CartesianGrid } from "recharts";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Table from "../../components/ui/Table";
import Badge from "../../components/ui/Badge";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import { BarChart3 } from "lucide-react";
import { Select } from "../../components/ui/FormField";
import { getBatches, getAnalytics } from "../../services/trainerService";

export default function TrainerAnalytics() {
  const [batches, setBatches] = useState([]);
  const [batchId, setBatchId] = useState("");
  const [data, setData] = useState({ velocity: [], quizTrend: [], studentRows: [] });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getBatches().then((b) => { setBatches(b); if (b.length) setBatchId(b[0].id); });
  }, []);

  useEffect(() => {
    setLoading(true);
    getAnalytics(batchId || undefined).then((d) => { setData(d); setLoading(false); });
  }, [batchId]);

  const activeBatchName = batches.find((b) => b.id === batchId)?.name;

  return (
    <div>
      <PageHeader
        title="Performance Analytics"
        subtitle="Sprint velocity, quiz trends, and student completion rates — computed from real batch, sprint, task, and assessment data"
        breadcrumbs={[{ label: "Dashboard", to: "/trainer/dashboard" }, { label: "Analytics" }]}
        action={
          batches.length > 0 && (
            <Select
              value={batchId}
              onChange={(e) => setBatchId(e.target.value)}
              options={[{ value: "", label: "All batches" }, ...batches.map((b) => ({ value: b.id, label: b.name }))]}
              className="w-52"
            />
          )
        }
      />

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Crunching analytics…" /></div>
      ) : batches.length === 0 ? (
        <EmptyState
          icon={BarChart3}
          title="No batches yet"
          description="Analytics fills in once you create a batch, run sprints, and students start completing tasks and assessments."
        />
      ) : (
        <>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <Card>
              <CardHeader title="Sprint Velocity" subtitle={`Story points completed per sprint${activeBatchName ? ` · ${activeBatchName}` : ""}`} />
              {data.velocity.length === 0 ? (
                <p className="text-sm text-ink-400 py-10 text-center">No completed sprint tasks yet.</p>
              ) : (
                <ResponsiveContainer width="100%" height={220}>
                  <BarChart data={data.velocity}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#E4E1D8" vertical={false} />
                    <XAxis dataKey="sprint" tick={{ fontSize: 12, fill: "#5B6472" }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fontSize: 12, fill: "#5B6472" }} axisLine={false} tickLine={false} />
                    <Tooltip contentStyle={{ borderRadius: 10, border: "1px solid #E4E1D8" }} />
                    <Bar dataKey="points" fill="#0D2845" radius={[6, 6, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              )}
            </Card>

            <Card>
              <CardHeader title="Quiz Score Trend" subtitle="Weekly average across submitted assessment attempts" />
              {data.quizTrend.length === 0 ? (
                <p className="text-sm text-ink-400 py-10 text-center">No assessments submitted yet.</p>
              ) : (
                <ResponsiveContainer width="100%" height={220}>
                  <LineChart data={data.quizTrend}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#E4E1D8" vertical={false} />
                    <XAxis dataKey="week" tick={{ fontSize: 12, fill: "#5B6472" }} axisLine={false} tickLine={false} />
                    <YAxis tick={{ fontSize: 12, fill: "#5B6472" }} axisLine={false} tickLine={false} />
                    <Tooltip contentStyle={{ borderRadius: 10, border: "1px solid #E4E1D8" }} />
                    <Line type="monotone" dataKey="avg" stroke="#EBB80D" strokeWidth={3} dot={{ fill: "#EBB80D" }} />
                  </LineChart>
                </ResponsiveContainer>
              )}
            </Card>
          </div>

          <Card className="mt-4">
            <CardHeader title="Individual Student Performance" subtitle={activeBatchName || "All batches"} />
            <Table
              data={data.studentRows}
              rowKey="name"
              columns={[
                { key: "name", header: "Student" },
                { key: "completion", header: "Task Completion", render: (r) => `${r.completion}%` },
                { key: "quiz", header: "Quiz Avg.", render: (r) => `${r.quiz}%` },
                { key: "flag", header: "Status", render: (r) => {
                  const isAtRisk = (r.hasTasks && r.completion < 70) || (r.hasQuizzes && r.quiz < 70);
                  return (
                    <Badge tone={r.onPip ? "error" : isAtRisk ? "warning" : "success"}>
                      {r.onPip ? "On PIP" : isAtRisk ? "At Risk" : "On Track"}
                    </Badge>
                  );
                } },
              ]}
            />
          </Card>
        </>
      )}
    </div>
  );
}
