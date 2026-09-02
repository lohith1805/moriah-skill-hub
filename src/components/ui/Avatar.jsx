import { initials } from "../../utils/formatters";

export default function Avatar({ name, color = "#0D2845", size = 36, src }) {
  if (src) {
    return <img src={src} alt={name} style={{ width: size, height: size }} className="rounded-full object-cover" />;
  }
  return (
    <div
      style={{ width: size, height: size, backgroundColor: color, fontSize: size * 0.38 }}
      className="flex items-center justify-center rounded-full font-semibold text-white shrink-0"
      title={name}
    >
      {initials(name)}
    </div>
  );
}
