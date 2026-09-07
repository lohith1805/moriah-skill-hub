export const formatDate = (input) => {
  if (!input) return "—";
  const d = typeof input === "string" ? new Date(input) : input;
  return d.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });
};

export const formatDateTime = (input) => {
  if (!input) return "—";
  const d = typeof input === "string" ? new Date(input) : input;
  return `${formatDate(d)}, ${d.toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })}`;
};

export const timeAgo = (input) => {
  const d = typeof input === "string" ? new Date(input) : input;
  const seconds = Math.floor((Date.now() - d.getTime()) / 1000);
  const steps = [
    ["year", 31536000],
    ["month", 2592000],
    ["day", 86400],
    ["hour", 3600],
    ["minute", 60],
  ];
  for (const [label, secs] of steps) {
    const v = Math.floor(seconds / secs);
    if (v >= 1) return `${v} ${label}${v > 1 ? "s" : ""} ago`;
  }
  return "just now";
};

export const initials = (name = "") =>
  name
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase())
    .join("");

export const truncate = (str = "", len = 60) => (str.length > len ? str.slice(0, len) + "…" : str);
