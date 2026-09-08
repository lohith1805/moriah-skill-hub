import { useState, useEffect } from "react";
import { Download, GitFork, Link2, ExternalLink, Save, Camera, FileText, Layers, GraduationCap, Award, AwardIcon, Sparkles } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card, { CardHeader } from "../../components/ui/Card";
import Tabs from "../../components/ui/Tabs";
import { Input, Textarea, Select } from "../../components/ui/FormField";
import Button from "../../components/ui/Button";
import Badge from "../../components/ui/Badge";
import Avatar from "../../components/ui/Avatar";
import FileUpload from "../../components/ui/FileUpload";
import SignatureCard from "../../components/widgets/SignatureCard";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import Modal from "../../components/ui/Modal";
import { getCertificates, getPerformanceSummary, saveResumeFile, getResumeStatus } from "../../services/studentService";
import { updateProfile } from "../../services/authService";
import { downloadPdf } from "../../utils/pdf";

// "https://github.com/foo" / "github.com/foo" / "foo" -> "foo"
const ghUsername = (v) => (v || "").trim().replace(/^https?:\/\/(www\.)?github\.com\//i, "").replace(/\/.*$/, "");

const formatExternalUrl = (url, type) => {
  if (!url) return "#";
  const trimmed = url.trim();
  if (!trimmed) return "#";

  if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
    return trimmed;
  }

  if (type === "github" && !trimmed.includes("github.com")) {
    return `https://github.com/${trimmed}`;
  }
  if (type === "linkedin" && !trimmed.includes("linkedin.com")) {
    return `https://linkedin.com/in/${trimmed}`;
  }

  return `https://${trimmed}`;
};

// user_profiles.education is a structured array; the form edits its first entry.
const eduToText = (edu) => {
  const e = Array.isArray(edu) ? edu[0] : null;
  if (!e) return "";
  return [e.institution, e.degree].filter(Boolean).join(" — ") + (e.endYear ? ` (${e.endYear})` : "");
};

const EXPERIENCE_LEVELS = ["", "JUNIOR", "MID", "SENIOR", "LEAD"];

export default function StudentProfile() {
  const { user, refreshUser } = useAuth();
  const { notify } = useToast();
  const [saving, setSaving] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [photoError, setPhotoError] = useState("");
  const [certs, setCerts] = useState([]);
  const [stats, setStats] = useState({ attendance: 0, taskCompletion: 0, quizAverage: 0 });

  const [values, setValues] = useState({
    bio: "", currentTitle: "", location: "", experienceLevel: "", yearsExperience: "",
    skills: "", github: "", linkedin: "",
    eduInstitution: "", eduDegree: "", eduYear: "",
    education: "", resume: null, resumeFile: null, certFiles: [],
  });

  // Hydrate the form from the REAL profile (GET /users/me fields, top-level on
  // `user`) — not `user.profileDetails`, which the API never populates. Re-runs
  // whenever `user` changes (initial null -> loaded, and after refreshUser()).
  useEffect(() => {
    if (!user) return;
    const e0 = Array.isArray(user.education) ? user.education[0] || {} : {};
    setValues((v) => ({
      ...v,
      bio: user.bio || "",
      currentTitle: user.currentTitle || "",
      location: user.location || "",
      experienceLevel: user.experienceLevel || "",
      yearsExperience: user.yearsExperience ?? "",
      skills: Array.isArray(user.skills) ? user.skills.join(", ") : (user.skills || ""),
      github: user.githubUsername || "",
      linkedin: user.linkedinUrl || "",
      eduInstitution: e0.institution || "",
      eduDegree: e0.degree || "",
      eduYear: e0.endYear ?? "",
      education: eduToText(user.education),
    }));
  }, [user]);

  useEffect(() => {
    if (user) {
      getCertificates().then(setCerts);
      getPerformanceSummary().then(setStats);
    }
  }, [user]);

  const set = (field) => (e) => setValues((v) => ({ ...v, [field]: e.target.value }));

  const handleResumeChange = async (files) => {
    const file = files[0] || null;

    if (!file) {
      // Student removed their resume via the "X" button — clear it
      // everywhere, including the Talent Pool eligibility gate.
      setValues((v) => ({ ...v, resumeFile: null, resume: null }));
      if (user) {
        const updatedUser = { ...user, profileDetails: { ...(user.profileDetails || {}), resume: null } };
        localStorage.setItem("msh_user", JSON.stringify(updatedUser));
        const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
        if (rawList) {
          const list = JSON.parse(rawList);
          const idx = list.findIndex((u) => u.id === user.id);
          if (idx > -1) {
            list[idx] = updatedUser;
            localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(list));
          }
        }
      }
      return;
    }

    // Ignore the placeholder 0-byte File FileUpload is re-initialized with
    // (reconstructed from a previously-saved resume just so it has a name
    // to display) — only persist a genuinely new selection.
    if (file.size === 0) {
      setValues((v) => ({ ...v, resumeFile: file }));
      return;
    }

    try {
      // Shared with the Student Dashboard's resume reminder banner, so a
      // resume saved from either place stores real file content the same
      // way and stays in sync with the HR/Client eligibility gate.
      const { resumeMeta } = await saveResumeFile(user, file);
      setValues((v) => ({ ...v, resumeFile: file, resume: resumeMeta }));
      notify("Resume saved.", { type: "success", title: "Resume updated" });
    } catch (e) {
      notify("Could not save your resume. Please try again.", { type: "error" });
    }
  };

  const handleCertsChange = (files) => {
    const certsMeta = files.map(f => ({ name: f.name, size: f.size }));
    setValues(v => ({
      ...v,
      certFiles: files,
      certs: certsMeta
    }));

    if (user) {
      const updatedUser = { 
        ...user, 
        profileDetails: { 
          ...(user.profileDetails || {}), 
          certs: certsMeta 
        } 
      };
      localStorage.setItem("msh_user", JSON.stringify(updatedUser));
      const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
      if (rawList) {
        const list = JSON.parse(rawList);
        const idx = list.findIndex((u) => u.id === user.id);
        if (idx > -1) {
          list[idx] = updatedUser;
          localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(list));
        }
      }
    }
  };

  const handlePhotoChange = (e) => {
    const file = e.target.files?.[0];
    if (file) {
      const isFormatValid = ["image/png", "image/jpeg", "image/jpg"].includes(file.type) ||
                            file.name.toLowerCase().endsWith(".png") ||
                            file.name.toLowerCase().endsWith(".jpg") ||
                            file.name.toLowerCase().endsWith(".jpeg");

      if (!isFormatValid) {
        setPhotoError("Photo must be in JPG or PNG format.");
        notify("Photo must be in JPG or PNG format.", { type: "error", title: "Invalid format" });
        return;
      }

      if (file.size > 500 * 1024) {
        setPhotoError("Image is too large - please keep it under 500KB.");
        notify("Photo must be less than 500KB.", { type: "error", title: "File too large" });
        return;
      }
      setPhotoError("");
      const reader = new FileReader();
      reader.onloadend = () => {
        const base64 = reader.result;
        if (user) {
          const updatedUser = { ...user, photo: base64 };
          localStorage.setItem("msh_user", JSON.stringify(updatedUser));
          const rawList = localStorage.getItem("mORIAH_REGISTERED_USERS");
          if (rawList) {
            const list = JSON.parse(rawList);
            const idx = list.findIndex((u) => u.id === user.id);
            if (idx > -1) {
              list[idx] = updatedUser;
              localStorage.setItem("mORIAH_REGISTERED_USERS", JSON.stringify(list));
            }
          }
          notify("Profile photo updated successfully.", { type: "success", title: "Photo Updated" });
          setTimeout(() => {
            window.location.reload();
          }, 800);
        }
      };
      reader.readAsDataURL(file);
    }
  };

  const downloadResume = () => {
    const resumeMeta = values.resume;
    if (resumeMeta?.fileData) {
      try {
        const [header, base64] = resumeMeta.fileData.split(",");
        const mimeMatch = header.match(/data:(.*?);base64/);
        const mime = mimeMatch ? mimeMatch[1] : "application/pdf";
        const binary = atob(base64);
        const array = new Uint8Array(binary.length);
        for (let i = 0; i < binary.length; i++) array[i] = binary.charCodeAt(i);
        const blob = new Blob([array], { type: mime });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = resumeMeta.name || "resume.pdf";
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
        return;
      } catch (e) {
        // Fall through to the generated printable resume sheet below.
      }
    }
    {
      const skills = values.skills ? values.skills.split(",").map((s) => s.trim()).filter(Boolean) : [];
      downloadPdf(`resume_${(user?.name || "student").toLowerCase().replace(/\s+/g, "_")}`, user?.name || "Resume", [
        {
          keyValues: [
            ["Track", user?.track || "Software Trainee"],
            ["Email", user?.email || ""],
            ["Phone", user?.phone || ""],
            ...(values.github ? [["GitHub", values.github]] : []),
            ...(values.linkedin ? [["LinkedIn", values.linkedin]] : []),
          ],
        },
        { heading: "Professional Summary", lines: [values.bio || "No summary provided."] },
        { heading: "Education", lines: [values.education || "Not specified."] },
        { heading: "Technical Expertise", lines: [skills.length ? skills.join(", ") : "Not specified."] },
        {
          heading: "Verified Credentials",
          lines: certs.length
            ? certs.map((c) => `${c.title} — verified ${c.issuedOn} (code ${c.verifyCode})`)
            : ["Apprenticeship graduation credentials pending final review."],
        },
      ]);
    }
  };

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      // education is a structured array server-side — send the one entry the
      // form edits (only if both required parts are filled). `linkedin` has no
      // write path on UpdateProfileRequest, so it's display-only for now.
      const eduInst = (values.eduInstitution || "").trim();
      const eduDeg = (values.eduDegree || "").trim();
      const patch = {
        bio: values.bio,
        currentTitle: values.currentTitle,
        location: values.location,
        experienceLevel: values.experienceLevel || undefined,
        yearsExperience: values.yearsExperience,
        githubUsername: ghUsername(values.github),
        skills: (values.skills || "").split(",").map((s) => s.trim()).filter(Boolean),
      };
      // Only touch education when the entry is filled — otherwise leave whatever
      // is on file (this form only edits the first entry).
      if (eduInst && eduDeg) {
        patch.education = [{
          institution: eduInst,
          degree: eduDeg,
          fieldOfStudy: null,
          startYear: null,
          endYear: values.eduYear ? Number(values.eduYear) : null,
        }];
      }
      await updateProfile(patch);
      // Pull the refreshed profile into AuthContext so the page reflects the
      // save immediately (the read view and the form both derive from `user`).
      await refreshUser();
      notify("Your profile has been updated.", { type: "success", title: "Saved" });
      setIsEditing(false);
    } catch (err) {
      notify(err.message || "Could not save your profile.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <PageHeader
        title="Profile & Dynamic Portfolio"
        subtitle="Manage your public portfolio, resume sheets, and verified links"
        breadcrumbs={[{ label: "Dashboard", to: "/student/dashboard" }, { label: "Profile" }]}
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 text-left">
        {/* Left Column: Avatar & Basic links */}
        <Card className="lg:col-span-1 flex flex-col items-center text-center">
          <div className="relative group cursor-pointer" onClick={() => document.getElementById("avatar-upload-input")?.click()}>
            <Avatar name={user?.name} color={user?.avatarColor} size={72} src={user?.photo} />
            <div className="absolute inset-0 rounded-full bg-black/25 opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex items-center justify-center">
              <Camera className="text-white" size={20} />
            </div>
            <input
              id="avatar-upload-input"
              type="file"
              accept="image/png, image/jpeg, image/jpg"
              className="hidden"
              onChange={handlePhotoChange}
            />
          </div>
          <button
            onClick={() => document.getElementById("avatar-upload-input")?.click()}
            className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-primary-600 hover:text-primary-700 bg-primary-50 hover:bg-primary-100 px-3 py-1.5 rounded-lg border border-primary-200 transition-colors cursor-pointer"
          >
            <Camera size={13} />
            <span>{user?.photo ? "Replace Photo" : "Upload Photo"}</span>
          </button>
          {photoError && (
            <p className="mt-2 text-xs text-error-500 font-semibold max-w-[180px] text-center leading-normal">
              {photoError}
            </p>
          )}
          <h3 className="font-display font-semibold text-ink-900 mt-3">{user?.name}</h3>
          <p className="text-sm text-ink-500">{user?.track || "Track not assigned yet"}</p>
          {user?.batch && <Badge tone="gold" className="mt-2">{user.batch}</Badge>}

          <div className="w-full flex flex-col gap-2 mt-5">
            <Button variant="secondary" icon={Download} fullWidth onClick={downloadResume}>Export Resume Sheet</Button>
          </div>

          <div className="w-full mt-5 pt-5 border-t border-border flex flex-col gap-2 text-left">
            {values.github ? (
              <a
                href={formatExternalUrl(values.github, "github")}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2 text-sm text-ink-600 hover:text-primary-700 font-medium"
              >
                <GitFork size={15} /> {values.github}
              </a>
            ) : (
              <span className="flex items-center gap-2 text-xs text-ink-400">
                <GitFork size={15} /> No GitHub linked
              </span>
            )}
            {values.linkedin ? (
              <a
                href={formatExternalUrl(values.linkedin, "linkedin")}
                target="_blank"
                rel="noopener noreferrer"
                className="flex items-center gap-2 text-sm text-ink-600 hover:text-primary-700 font-medium"
              >
                <Link2 size={15} /> {values.linkedin}
              </a>
            ) : (
              <span className="flex items-center gap-2 text-xs text-ink-400">
                <Link2 size={15} /> No LinkedIn linked
              </span>
            )}
          </div>
        </Card>

        {/* Right Column Tabs: Edit Profile, Resume Sheet, and Portfolio */}
        <Card className="lg:col-span-2">
          <Tabs tabs={[
            { key: "details", label: "Edit Profile", icon: FileText },
            { key: "resume", label: "Resume Builder", icon: Sparkles },
            { key: "portfolio", label: "Developer Portfolio", icon: Layers }
          ]}>
            {(active) => {
              if (active === "details") {
                return (
                  <div className="flex flex-col gap-6">
                    <div className="flex justify-between items-center pb-2 border-b border-border">
                      <span className="text-xs text-ink-500 font-medium">
                        Manage your biography, technical focus, and credentials
                      </span>
                      <Button type="button" size="sm" variant="secondary" onClick={() => setIsEditing(true)}>
                        Edit Profile
                      </Button>
                    </div>

                    <div className="space-y-6">
                      <div>
                        <span className="text-xs font-semibold text-ink-400 uppercase tracking-wider block mb-1">Biography</span>
                        <p className="text-sm text-ink-700 leading-relaxed whitespace-pre-line bg-cream-50/50 p-4 rounded-xl border border-border/60">
                          {values.bio || "No biography added yet."}
                        </p>
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                        <div className="bg-cream-50/50 p-4 rounded-xl border border-border/60">
                          <span className="text-xs font-semibold text-ink-400 uppercase tracking-wider block mb-1">Current Title</span>
                          <span className="text-sm font-medium text-ink-800">{values.currentTitle || "Not specified"}</span>
                        </div>
                        <div className="bg-cream-50/50 p-4 rounded-xl border border-border/60">
                          <span className="text-xs font-semibold text-ink-400 uppercase tracking-wider block mb-1">Location</span>
                          <span className="text-sm font-medium text-ink-800">{values.location || "Not specified"}</span>
                        </div>
                        <div className="bg-cream-50/50 p-4 rounded-xl border border-border/60">
                          <span className="text-xs font-semibold text-ink-400 uppercase tracking-wider block mb-1">Experience</span>
                          <span className="text-sm font-medium text-ink-800">
                            {values.experienceLevel || "—"}{values.yearsExperience !== "" && values.yearsExperience != null ? ` · ${values.yearsExperience} yr` : ""}
                          </span>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="bg-cream-50/50 p-4 rounded-xl border border-border/60 flex flex-col justify-center min-h-[76px]">
                          <span className="text-xs font-semibold text-ink-400 uppercase tracking-wider block mb-1">Education</span>
                          <span className="text-sm font-medium text-ink-800">{values.education || "Not specified"}</span>
                        </div>

                        <div className="bg-cream-50/50 p-4 rounded-xl border border-border/60 flex flex-col justify-center min-h-[76px]">
                          <span className="text-xs font-semibold text-ink-400 uppercase tracking-wider block mb-1.5">Technical Skills</span>
                          <div className="flex flex-wrap gap-1.5">
                            {values.skills ? (
                              values.skills.split(",").map((s) => (
                                <Badge key={s} tone="primary" className="px-2 py-0.5 text-xs font-medium">
                                  {s.trim()}
                                </Badge>
                              ))
                            ) : (
                              <span className="text-sm text-ink-500">Not specified</span>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Documents block inside details page */}
                      <div className="border-t border-border pt-4">
                        <div className="flex items-center justify-between mb-3">
                          <h4 className="text-sm font-semibold text-ink-900">Supporting Credentials</h4>
                          <Badge tone={getResumeStatus(values.resume) === "Not Uploaded" ? "warning" : getResumeStatus(values.resume) === "Updated" ? "primary" : "success"}>
                            Resume: {getResumeStatus(values.resume)}
                          </Badge>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                          <FileUpload
                            label="Upload Resume"
                            hint="Upload PDF (max 10MB)"
                            accept=".pdf"
                            initialFiles={values.resumeFile ? [values.resumeFile] : []}
                            onChange={handleResumeChange}
                          />
                          <FileUpload
                            label="Certifications Documents"
                            hint="Upload cert files (max 10MB)"
                            accept=".pdf,.png,.jpg"
                            multiple
                            initialFiles={values.certFiles || []}
                            onChange={handleCertsChange}
                          />
                        </div>
                      </div>
                    </div>

                    <Modal
                      open={isEditing}
                      onClose={() => setIsEditing(false)}
                      title="Edit Profile Details"
                      description="Update your bio, education, skills, and social links."
                      size="lg"
                      footer={
                        <div className="flex gap-2">
                          <Button type="button" variant="secondary" onClick={() => setIsEditing(false)}>
                            Cancel
                          </Button>
                          <Button type="submit" form="profile-edit-form" variant="primary" icon={Save} loading={saving}>
                            Save Changes
                          </Button>
                        </div>
                      }
                    >
                      <form id="profile-edit-form" onSubmit={save} className="flex flex-col gap-4 text-left">
                        <Textarea name="bio" label="Bio" value={values.bio} onChange={set("bio")} rows={3} hint="Shown on your public portfolio page." />
                        <div className="grid sm:grid-cols-2 gap-4">
                          <Input name="currentTitle" label="Current Title" value={values.currentTitle} onChange={set("currentTitle")} placeholder="e.g. Trainee Software Engineer" />
                          <Input name="location" label="Location" value={values.location} onChange={set("location")} placeholder="e.g. Hyderabad, India" />
                        </div>
                        <div className="grid sm:grid-cols-2 gap-4">
                          <Select name="experienceLevel" label="Experience Level" value={values.experienceLevel} onChange={set("experienceLevel")}
                            options={EXPERIENCE_LEVELS.map((l) => ({ value: l, label: l || "—" }))} />
                          <Input name="yearsExperience" type="number" min="0" max="60" label="Years of Experience" value={values.yearsExperience} onChange={set("yearsExperience")} />
                        </div>
                        <Input name="skills" label="Technical Skills" value={values.skills} onChange={set("skills")} hint="Comma-separated." />
                        <div className="rounded-lg border border-border/70 p-3 flex flex-col gap-3">
                          <span className="text-xs font-semibold text-ink-500 uppercase tracking-wide">Education</span>
                          <div className="grid sm:grid-cols-3 gap-3">
                            <Input name="eduInstitution" label="Institution" value={values.eduInstitution} onChange={set("eduInstitution")} />
                            <Input name="eduDegree" label="Degree" value={values.eduDegree} onChange={set("eduDegree")} />
                            <Input name="eduYear" type="number" min="1900" max="2100" label="Year" value={values.eduYear} onChange={set("eduYear")} />
                          </div>
                          <p className="text-[11px] text-ink-400">Institution and Degree are both required to save an education entry.</p>
                        </div>
                        <Input name="github" label="GitHub URL" value={values.github} onChange={set("github")} hint="Used to verify your pull request submissions." />
                      </form>
                    </Modal>
                  </div>
                );
              }

              if (active === "resume") {
                return (
                  <div className="bg-white border border-border rounded-xl p-8 max-w-xl mx-auto shadow-sm">
                    <div className="text-center border-b-2 border-border pb-4 mb-5">
                      <h4 className="font-display font-extrabold text-2xl text-ink-950 uppercase tracking-wider">{user?.name}</h4>
                      <p className="text-xs font-semibold text-primary-600 tracking-wider uppercase mt-1">{user?.track || "Software Engineer"}</p>
                      <p className="text-[10px] text-ink-500 mt-2">
                        Email: {user?.email} | Phone: {user?.phone}
                      </p>
                    </div>

                    <div className="space-y-4 text-xs">
                      <div>
                        <h5 className="font-bold text-ink-900 border-b border-border pb-1 uppercase tracking-wide">Professional Profile</h5>
                        <p className="text-ink-600 mt-1.5 leading-relaxed">{values.bio || "No summary provided."}</p>
                      </div>

                      <div>
                        <h5 className="font-bold text-ink-900 border-b border-border pb-1 uppercase tracking-wide">Education</h5>
                        <p className="text-ink-700 mt-1.5 font-medium">{values.education || "Not specified."}</p>
                      </div>

                      <div>
                        <h5 className="font-bold text-ink-900 border-b border-border pb-1 uppercase tracking-wide">Technical Competencies</h5>
                        <div className="flex flex-wrap gap-1.5 mt-2">
                          {values.skills ? values.skills.split(",").map(s => (
                            <span key={s} className="bg-cream-100 border border-border text-ink-700 px-2 py-0.5 rounded font-mono font-medium">
                              {s.trim()}
                            </span>
                          )) : "Not specified."}
                        </div>
                      </div>

                      <div>
                        <h5 className="font-bold text-ink-900 border-b border-border pb-1 uppercase tracking-wide">Verified Credentials</h5>
                        <div className="mt-2 space-y-2">
                          {certs.length > 0 ? certs.map(c => (
                            <div key={c.id} className="flex justify-between items-start gap-4">
                              <div>
                                <p className="font-semibold text-ink-850">{c.title}</p>
                                <p className="text-[10px] text-ink-400 font-mono mt-0.5">Hash: {c.hash?.slice(0, 32)}...</p>
                              </div>
                              <Badge tone="success" className="font-mono text-[9px]">{c.verifyCode}</Badge>
                            </div>
                          )) : (
                            <p className="text-[10px] text-ink-400 italic">No verified credentials issued yet. Graduation credentials appear once approved by mentor.</p>
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                );
              }

              if (active === "portfolio") {
                return (
                  <div className="space-y-6">
                    {/* Glowing Header */}
                    <div className="bg-gradient-to-r from-primary-900 to-indigo-900 rounded-xl p-6 text-white text-left relative overflow-hidden shadow-inner">
                      <div className="absolute right-0 bottom-0 translate-x-4 translate-y-4 opacity-10">
                        <GraduationCap size={150} />
                      </div>
                      <span className="text-[10px] font-bold uppercase tracking-wider text-indigo-200 bg-indigo-500/20 px-2.5 py-1 rounded-full border border-indigo-400/20">
                        Verified Developer Portfolio
                      </span>
                      <h3 className="font-display font-extrabold text-2xl mt-3">{user?.name}</h3>
                      <p className="text-sm text-indigo-100 mt-1">{user?.track || "Trainee"}</p>
                    </div>

                    {/* Stats Summary cards */}
                    <div className="grid grid-cols-3 gap-3">
                      <div className="border border-border rounded-xl p-3 bg-cream-50/30 text-center">
                        <span className="text-[10px] text-ink-450 block font-bold uppercase">Attendance</span>
                        <span className="text-xl font-extrabold text-primary-900 mt-1 block">{stats.attendance}%</span>
                      </div>
                      <div className="border border-border rounded-xl p-3 bg-cream-50/30 text-center">
                        <span className="text-[10px] text-ink-450 block font-bold uppercase">Tasks Finished</span>
                        <span className="text-xl font-extrabold text-primary-900 mt-1 block">{stats.taskCompletion}%</span>
                      </div>
                      <div className="border border-border rounded-xl p-3 bg-cream-50/30 text-center">
                        <span className="text-[10px] text-ink-450 block font-bold uppercase">Quiz Average</span>
                        <span className="text-xl font-extrabold text-primary-900 mt-1 block">{stats.quizAverage}%</span>
                      </div>
                    </div>

                    {/* Bio & Skills */}
                    <div className="text-left space-y-4">
                      <div>
                        <h4 className="text-xs font-bold text-ink-400 uppercase tracking-wider mb-1">About Me</h4>
                        <p className="text-sm text-ink-700 leading-relaxed">{values.bio || "No summary provided."}</p>
                      </div>

                      <div>
                        <h4 className="text-xs font-bold text-ink-400 uppercase tracking-wider mb-2">Technical Toolbox</h4>
                        <div className="flex flex-wrap gap-1.5">
                          {values.skills ? values.skills.split(",").map((s, idx) => (
                            <Badge key={idx} tone={idx % 2 === 0 ? "primary" : "neutral"} className="px-2.5 py-1 text-xs">
                              {s.trim()}
                            </Badge>
                          )) : <span className="text-sm text-ink-450">No competencies specified.</span>}
                        </div>
                      </div>

                      {/* Verified Badge */}
                      {certs.length > 0 && (
                        <div className="border border-success-200 bg-success-50/50 rounded-xl p-4 flex items-center gap-3">
                          <Award className="text-success-700 shrink-0" size={28} />
                          <div>
                            <p className="text-sm font-semibold text-success-850">Moriah Skill Hub Certified App Graduate</p>
                            <p className="text-xs text-success-700 mt-0.5">Credential code verified on ledger: <strong className="font-mono">{certs[0].verifyCode}</strong></p>
                          </div>
                        </div>
                      )}
                    </div>
                  </div>
                );
              }
            }}
          </Tabs>
        </Card>

        <SignatureCard className="lg:col-span-3" />
      </div>
    </div>
  );
}
