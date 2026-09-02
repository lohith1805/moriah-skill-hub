// In-app notifications feed (backend gap B1.5).
//   GET  /api/v1/notifications            (paginated, ?unreadOnly)
//   GET  /api/v1/notifications/unread-count
//   PUT  /api/v1/notifications/{id}/read
//   PUT  /api/v1/notifications/read-all
//
// Backend rows are { id, templateCode, payload:{...}, read, readAt, createdAt }.
// The UI wants { id, title, body, time, read } — mapped here.

import { apiClient } from "./apiClient";

function humanizeTemplate(code) {
  if (!code) return "Notification";
  return code
    .toLowerCase()
    .split(/[_\s]+/)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

function toFeNotification(n) {
  const p = n.payload || {};
  return {
    id: n.id,
    title: p.title || p.subject || humanizeTemplate(n.templateCode),
    body: p.body || p.message || p.text || "",
    time: n.createdAt,
    read: !!n.read,
    templateCode: n.templateCode,
    payload: p,
  };
}

export async function getNotifications({ page = 0, size = 30, unreadOnly = false } = {}) {
  const res = await apiClient.get("/notifications", { page, size, unreadOnly });
  const rows = (res && res.content ? res.content : []).map(toFeNotification);
  // Header/NotificationBell just want the array.
  return rows;
}

export async function getUnreadCount() {
  const res = await apiClient.get("/notifications/unread-count");
  return res ? res.unreadCount ?? 0 : 0;
}

export async function markAsRead(id) {
  const n = await apiClient.put(`/notifications/${id}/read`);
  return toFeNotification(n);
}

export async function markAllAsRead() {
  const res = await apiClient.put("/notifications/read-all");
  return res ? res.markedRead ?? 0 : 0;
}
