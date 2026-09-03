// Client-side PDF generation for the app's "download" buttons, so every
// downloadable document comes out as a real .pdf rather than .txt / .html.
// Server-generated documents (certificates, the real subscription invoice)
// are downloaded from their pre-signed backend URL instead — see openPdfUrl.
import { jsPDF } from "jspdf";

const BRAND = "Moriah Skill Hub";
const MARGIN = 48;

function newDoc() {
  const doc = new jsPDF({ unit: "pt", format: "a4" });
  return { doc, w: doc.internal.pageSize.getWidth(), h: doc.internal.pageSize.getHeight() };
}

/**
 * A simple document PDF: brand line, title, rule, then a list of blocks.
 * blocks: array of either a plain string (a paragraph) or
 *   { heading?: string, lines?: string[], keyValues?: [label, value][] }.
 * Triggers a download of `<filename>.pdf`.
 */
export function downloadPdf(filename, title, blocks = []) {
  const { doc, w, h } = newDoc();
  const contentW = w - MARGIN * 2;
  let y = MARGIN;

  const ensureRoom = (lineHeight) => {
    if (y + lineHeight > h - MARGIN) {
      doc.addPage();
      y = MARGIN;
    }
  };
  const line = (text, { bold = false, size = 11, gap = 16, color = 40 } = {}) => {
    doc.setFont("helvetica", bold ? "bold" : "normal");
    doc.setFontSize(size);
    doc.setTextColor(color);
    doc.splitTextToSize(String(text ?? ""), contentW).forEach((chunk) => {
      ensureRoom(gap);
      doc.text(chunk, MARGIN, y);
      y += gap;
    });
  };

  line(BRAND, { bold: true, size: 11, color: 120, gap: 22 });
  line(title, { bold: true, size: 18, color: 20, gap: 12 });
  doc.setDrawColor(220);
  doc.line(MARGIN, y, MARGIN + contentW, y);
  y += 22;

  blocks.forEach((block) => {
    if (typeof block === "string") {
      line(block);
      y += 6;
      return;
    }
    if (block.heading) {
      y += 6;
      line(block.heading, { bold: true, size: 12, color: 25 });
      y += 2;
    }
    (block.keyValues || []).forEach(([label, value]) => {
      doc.setFont("helvetica", "bold");
      doc.setFontSize(10.5);
      doc.setTextColor(90);
      ensureRoom(16);
      doc.text(`${label}`, MARGIN, y);
      doc.setFont("helvetica", "normal");
      doc.setTextColor(30);
      doc.splitTextToSize(String(value ?? ""), contentW - 140).forEach((chunk, i) => {
        if (i > 0) { ensureRoom(15); y += 15; }
        doc.text(chunk, MARGIN + 140, y);
      });
      y += 17;
    });
    (block.lines || []).forEach((l) => line(l));
    y += 8;
  });

  doc.save(filename.endsWith(".pdf") ? filename : `${filename}.pdf`);
}

/** Open a server-generated PDF (pre-signed URL) in a new tab. */
export function openPdfUrl(url, onBlocked) {
  const win = window.open(url, "_blank", "noopener");
  if (!win && typeof onBlocked === "function") onBlocked();
}

/**
 * A GST-style tax invoice / credit note PDF for one admin transaction.
 * Mirrors utils/invoiceTemplate.js's breakdown (amount is tax-inclusive, 18%
 * GST split CGST/SGST). Downloads `<invoiceNo>.pdf`.
 */
export function downloadInvoicePdf({ invoiceNo, isCreditNote, txn, breakdown }) {
  const { doc, w } = newDoc();
  const contentW = w - MARGIN * 2;
  const inr = (v) =>
    new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(Number(v) || 0);
  let y = MARGIN;

  doc.setFont("helvetica", "bold"); doc.setFontSize(20); doc.setTextColor(76, 29, 149);
  doc.text(BRAND, MARGIN, y);
  doc.setFont("helvetica", "bold"); doc.setFontSize(16); doc.setTextColor(20);
  doc.text(isCreditNote ? "CREDIT NOTE (Refund)" : "TAX INVOICE", w - MARGIN, y, { align: "right" });
  y += 16;
  doc.setFont("helvetica", "normal"); doc.setFontSize(9); doc.setTextColor(110);
  doc.text("Moriah Skill Hub Technologies Pvt. Ltd.  ·  Hyderabad, Telangana, India — 500081", MARGIN, y); y += 12;
  doc.text("GSTIN: 36AAMCM1234A1Z5   |   PAN: AAMCM1234A", MARGIN, y);
  doc.setTextColor(30); doc.setFontSize(10);
  doc.text(`Invoice No: ${invoiceNo}`, w - MARGIN, y - 12, { align: "right" });
  doc.text(`Invoice Date: ${txn.date || new Date().toLocaleDateString("en-IN")}`, w - MARGIN, y, { align: "right" });
  y += 14;
  doc.setDrawColor(124, 58, 237); doc.setLineWidth(2); doc.line(MARGIN, y, MARGIN + contentW, y);
  doc.setLineWidth(1); y += 24;

  doc.setFont("helvetica", "bold"); doc.setFontSize(10); doc.setTextColor(90);
  doc.text("BILLED TO", MARGIN, y);
  doc.text("PAYMENT", MARGIN + contentW / 2, y);
  y += 15;
  doc.setFont("helvetica", "normal"); doc.setFontSize(11); doc.setTextColor(30);
  doc.text(txn.student || "—", MARGIN, y);
  doc.text(`${txn.gateway || "—"} · ${txn.status || ""}`, MARGIN + contentW / 2, y);
  y += 12;
  doc.setFontSize(9); doc.setTextColor(120);
  doc.text("Student, Moriah Skill Hub", MARGIN, y);
  if (txn.id) doc.text(`Ref: ${txn.id}`, MARGIN + contentW / 2, y);
  y += 26;

  const rows = [
    ["Taxable Value", inr(breakdown.taxableValue)],
    ["CGST @ 9%", inr(breakdown.cgst)],
    ["SGST @ 9%", inr(breakdown.sgst)],
  ];
  doc.setFontSize(11); doc.setTextColor(30);
  doc.setFont("helvetica", "bold");
  doc.text(`${txn.plan || "Subscription Plan"} — Skill Development Program Fee`, MARGIN, y);
  y += 18;
  doc.setFont("helvetica", "normal");
  rows.forEach(([label, value]) => {
    doc.text(label, MARGIN + contentW - 220, y);
    doc.text(value, MARGIN + contentW, y, { align: "right" });
    y += 16;
  });
  doc.setDrawColor(124, 58, 237); doc.line(MARGIN + contentW - 220, y, MARGIN + contentW, y); y += 16;
  doc.setFont("helvetica", "bold"); doc.setTextColor(76, 29, 149);
  doc.text(isCreditNote ? "Total Refunded" : "Total Amount Paid", MARGIN + contentW - 220, y);
  doc.text(inr(breakdown.total), MARGIN + contentW, y, { align: "right" });
  y += 32;

  doc.setFont("helvetica", "normal"); doc.setFontSize(9); doc.setTextColor(150);
  doc.splitTextToSize(
    `This is a system-generated ${isCreditNote ? "credit note" : "tax invoice"} and does not require a physical signature. ` +
    `For billing queries, contact billing@moriahskillhub.com.`,
    contentW,
  ).forEach((chunk) => { doc.text(chunk, MARGIN, y); y += 12; });

  doc.save(`${invoiceNo}.pdf`);
}
