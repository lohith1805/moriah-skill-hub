import { mockRequest } from "./apiClient";

export async function getNotifications() {
  const raw = localStorage.getItem("msh_notifications");
  const list = raw ? JSON.parse(raw) : [];
  return mockRequest(list);
}

export async function markAsRead(id) {
  const raw = localStorage.getItem("msh_notifications");
  const list = raw ? JSON.parse(raw) : [];
  const idx = list.findIndex((n) => n.id === id);
  let updated = null;
  if (idx > -1) {
    list[idx] = { ...list[idx], read: true };
    localStorage.setItem("msh_notifications", JSON.stringify(list));
    updated = list[idx];
  }
  return mockRequest(updated);
}
