// Saves plain text as a downloadable .txt file — a real browser download (this is a normal web
// app, not a sandboxed preview), used by every "view a requirement document's full content"
// modal (BA Documents, Developer Client Requirements, the shared Client Project Documents inbox)
// so a reader isn't limited to the in-modal scroll box.
export function downloadTextFile(filename, content) {
  const blob = new Blob([content || ""], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename.endsWith(".txt") ? filename : `${filename}.txt`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
