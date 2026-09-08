import { useEffect, useState } from "react";
import { Layers, FolderGit, GitFork, ArrowRight, BookOpen } from "lucide-react";
import { Link } from "react-router-dom";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import LoadingSpinner from "../../components/ui/LoadingSpinner";
import EmptyState from "../../components/ui/EmptyState";
import { getMyProjects } from "../../services/studentService";

const DIFF_TONE = { Beginner: "success", Intermediate: "warning", Advanced: "error" };

export default function StudentProjects() {
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getMyProjects()
      .then(setProjects)
      .catch(() => setProjects([]))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div>
      <PageHeader
        title="Projects"
        subtitle="Your batch's practice projects — fork the starter repo, build the feature, then link your pull request"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Projects" }]}
      />

      {/* How the project → PR flow works (fork model) */}
      <Card className="mb-5 border-l-4 border-l-primary-500 bg-primary-50/40">
        <div className="flex items-start gap-3 p-1">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-100 text-primary-700">
            <GitFork size={17} />
          </div>
          <div className="text-sm text-ink-700 leading-relaxed">
            <p className="font-semibold text-ink-900">How to submit a project</p>
            <p className="mt-1">
              1. Open the <strong>starter repository</strong> and <strong>fork it into your own GitHub account</strong>
              {" "}(keep the fork public). 2. Build the feature on a branch and open a pull request in your fork.
              3. Paste the PR link under{" "}
              <Link to="/student/submissions" className="font-semibold text-primary-700 hover:underline">
                GitHub Submissions
              </Link>. Your trainer reviews the PR on GitHub and records the result there.
            </p>
          </div>
        </div>
      </Card>

      {loading ? (
        <div className="flex justify-center py-16"><LoadingSpinner label="Loading projects…" /></div>
      ) : projects.length === 0 ? (
        <EmptyState icon={Layers} title="No projects published yet" description="Practice projects show up here once a developer publishes them." />
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-5 text-left">
          {projects.map((p) => (
            <Card key={p.id} className="flex flex-col">
              <div className="flex items-start gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-primary-50 text-primary-700">
                  <Layers size={20} />
                </div>
                <div className="min-w-0">
                  <p className="font-display text-lg font-bold text-ink-900 leading-snug">{p.title}</p>
                  <div className="flex flex-wrap items-center gap-2 mt-1.5">
                    {p.difficulty && <Badge tone={DIFF_TONE[p.difficulty] || "neutral"}>{p.difficulty}</Badge>}
                    {p.domain && <span className="text-xs text-ink-500">{p.domain}</span>}
                  </div>
                </div>
              </div>

              {(p.stack || []).length > 0 && (
                <div className="flex flex-wrap gap-1.5 mt-4">
                  {p.stack.map((s) => <Badge key={s} tone="primary">{s}</Badge>)}
                </div>
              )}

              {p.description && <p className="text-sm text-ink-600 mt-4 leading-relaxed">{p.description}</p>}

              <div className="mt-auto pt-5 flex flex-wrap gap-3">
                {p.starterRepo ? (
                  <Button
                    as="a"
                    href={p.starterRepo}
                    target="_blank"
                    rel="noopener noreferrer"
                    size="lg"
                    icon={FolderGit}
                  >
                    Open starter repository
                  </Button>
                ) : (
                  <span className="inline-flex items-center gap-1.5 text-sm text-ink-400">
                    <FolderGit size={15} /> No starter repo linked yet
                  </span>
                )}
                <Button as={Link} to="/student/submissions" size="lg" variant="secondary" icon={ArrowRight} iconPosition="right">
                  Submit a PR
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}

      <p className="mt-6 text-xs text-ink-400 flex items-center gap-1.5">
        <BookOpen size={13} /> Looking for the debugging exercises? They moved to their own{" "}
        <Link to="/student/challenges" className="font-semibold text-primary-700 hover:underline">Bug Challenges</Link> page.
      </p>
    </div>
  );
}
