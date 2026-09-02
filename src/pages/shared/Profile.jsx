import { useState } from "react";
import { Save, User, Mail, Shield, Phone, Edit3, Camera } from "lucide-react";
import PageHeader from "../../components/layout/PageHeader";
import Card from "../../components/ui/Card";
import Avatar from "../../components/ui/Avatar";
import Badge from "../../components/ui/Badge";
import Button from "../../components/ui/Button";
import { Input, Textarea } from "../../components/ui/FormField";
import { useAuth } from "../../context/AuthContext";
import { useToast } from "../../context/ToastContext";
import { ROLE_LABELS } from "../../utils/constants";

export default function SharedProfile() {
  const { user } = useAuth();
  const { notify } = useToast();
  const [saving, setSaving] = useState(false);
  const [photoError, setPhotoError] = useState("");
  const [values, setValues] = useState(() => {
    return {
      name: user?.name || "",
      phone: user?.phone || "+91 98765 43210",
      bio: user?.bio || "Technical professional contributing to Moriah Skill Hub operations.",
    };
  });

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

  const save = async (e) => {
    e.preventDefault();
    setSaving(true);
    await new Promise((r) => setTimeout(r, 600));
    setSaving(false);

    if (user) {
      const updatedUser = { 
        ...user, 
        name: values.name, 
        phone: values.phone, 
        bio: values.bio 
      };

      try {
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
        notify("Profile details updated successfully.", { type: "success", title: "Saved" });
        setTimeout(() => {
          window.location.reload();
        }, 800);
      } catch (err) {
        console.warn("Failed to persist shared profile update:", err);
      }
    }
  };

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
            <Avatar name={user?.name} color={user?.avatarColor} size={80} src={user?.photo} />
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
            <span>{user?.photo ? "Replace Photo" : "Upload Photo"}</span>
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
            <Input
              name="name"
              autoComplete="name"
              label="Full Name"
              value={values.name}
              onChange={(e) => setValues((v) => ({ ...v, name: e.target.value }))}
              required
            />
            
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Input
                name="email"
                autoComplete="email"
                label="Email address"
                value={user?.email || ""}
                disabled
                hint="Your email address is managed by your administrator."
              />
              <Input
                name="phone"
                autoComplete="tel"
                label="Phone Number"
                value={values.phone}
                onChange={(e) => setValues((v) => ({ ...v, phone: e.target.value }))}
                icon={Phone}
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
      </div>
    </div>
  );
}
