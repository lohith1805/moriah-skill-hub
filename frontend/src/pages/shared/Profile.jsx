import { useState } from "react";
import { Save, User, Mail, Shield, Phone, Edit3, Camera } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Avatar from "../../components/ui/Avatar";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import SignatureCard from "../../components/widgets/SignatureCard";
import { Input, Textarea } from "../../components/ui/FormField";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { ROLE_LABELS } from "../../utils/constants";
import { updateProfile } from "../../services/authService";

export default function SharedProfile() {
  const { user, refreshUser } = useAuth();
  const { notify } = useToast();
  const [saving, setSaving] = useState(false);
  const [photoError, setPhotoError] = useState("");
  const [values, setValues] = useState(() => ({
    bio: user?.bio || "",
    location: user?.location || "",
    currentTitle: user?.currentTitle || "",
    githubUsername: user?.githubUsername || "",
  }));

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
      // The backend profile has no avatar field yet, so the photo is kept
      // locally in this browser only.
      const reader = new FileReader();
      reader.onloadend = () => {
        try {
          localStorage.setItem("msh_profile_photo", String(reader.result));
        } catch {
          /* quota / private mode — non-fatal */
        }
        notify("Profile photo saved to this browser.", { type: "success", title: "Photo Updated" });
        setTimeout(() => window.location.reload(), 600);
      };
      reader.readAsDataURL(file);
    }
  };

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    try {
      await updateProfile(values);
      await refreshUser();
      notify("Profile updated.", { type: "success", title: "Saved" });
    } catch (err) {
      notify(err.message || "Could not save your profile.", { type: "error" });
    } finally {
      setSaving(false);
    }
  };

  const localPhoto = (() => {
    try {
      return localStorage.getItem("msh_profile_photo") || undefined;
    } catch {
      return undefined;
    }
  })();

  return (
    <div>
      <PageHeader
        title="My Profile"
        subtitle="Manage your personal profile information and bio details"
        breadcrumbs={[{ label: "Dashboard" }, { label: "Profile" }]}
      />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Avatar & Role Summary Card */}
        <Card className="flex flex-col items-center text-center">
          <div className="relative group cursor-pointer" onClick={() => document.getElementById("avatar-upload-input")?.click()}>
            <Avatar name={user?.name} color={user?.avatarColor} size={80} src={localPhoto} />
            <div className="absolute inset-0 rounded-full bg-black/25 opacity-0 group-hover:opacity-100 transition-opacity duration-200 flex items-center justify-center">
              <Camera className="text-white" size={22} />
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
            <span>{localPhoto ? "Replace Photo" : "Upload Photo"}</span>
          </button>
          {photoError && (
            <p className="mt-2 text-xs text-error-500 font-semibold max-w-[180px] text-center leading-normal">
              {photoError}
            </p>
          )}
          <h3 className="font-display font-bold text-ink-900 text-lg mt-4">{user?.name}</h3>
          <p className="text-xs text-ink-500 font-medium mt-0.5">{user?.email}</p>
          <div className="mt-3">
            <Badge tone="primary">{ROLE_LABELS[user?.role] || "Staff Member"}</Badge>
          </div>

          <div className="w-full border-t border-border pt-5 mt-6 flex flex-col gap-3 text-left">
            <div className="flex items-center gap-2.5 text-xs text-ink-600">
              <Mail size={14} className="text-ink-400" />
              <span>{user?.email}</span>
            </div>
            <div className="flex items-center gap-2.5 text-xs text-ink-600">
              <Shield size={14} className="text-ink-400" />
              <span className="capitalize">{user?.role} Access Mode</span>
            </div>
          </div>
        </Card>

        {/* Right Column: Edit Profile Form */}
        <Card className="lg:col-span-2">
          <h3 className="font-display font-bold text-ink-900 text-base mb-5 flex items-center gap-2">
            <Edit3 size={17} className="text-primary-600" /> Edit Profile Details
          </h3>

          <form onSubmit={save} className="flex flex-col gap-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                name="name"
                label="Full Name"
                value={user?.name || ""}
                disabled
                hint="Name and phone are managed by an administrator."
              />
              <Input
                name="email"
                autoComplete="email"
                label="Email address"
                value={user?.email || ""}
                disabled
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                name="phone"
                autoComplete="tel"
                label="Phone Number"
                value={user?.phone || "—"}
                disabled
                icon={Phone}
              />
              <Input
                name="currentTitle"
                label="Current Title / Role"
                value={values.currentTitle}
                onChange={(e) => setValues((v) => ({ ...v, currentTitle: e.target.value }))}
                placeholder="e.g. Senior Trainer"
              />
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                name="location"
                label="Location"
                value={values.location}
                onChange={(e) => setValues((v) => ({ ...v, location: e.target.value }))}
                placeholder="e.g. Bengaluru"
              />
              <Input
                name="githubUsername"
                label="GitHub Username"
                value={values.githubUsername}
                onChange={(e) => setValues((v) => ({ ...v, githubUsername: e.target.value }))}
                placeholder="octocat"
              />
            </div>

            <Textarea
              name="bio"
              label="Personal Bio / About"
              value={values.bio}
              onChange={(e) => setValues((v) => ({ ...v, bio: e.target.value }))}
              rows={4}
              placeholder="Tell us about your background or operational area..."
            />

            <div className="pt-2">
              <Button type="submit" icon={Save} loading={saving}>
                Save Profile Changes
              </Button>
            </div>
          </form>
        </Card>

        <SignatureCard className="lg:col-span-3" />
      </div>
    </div>
  );
}
