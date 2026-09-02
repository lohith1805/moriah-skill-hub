// Tax invoice generator for admin/Transactions.jsx.
// Builds a print-ready GST-style tax invoice for a student payment and opens
// it in a new browser tab, where the admin can print / "Save as PDF".
// Amount stored on the transaction is treated as tax-inclusive (18% GST,
// split evenly across CGST/SGST for an intra-state supply), consistent with
// SUBSCRIPTION_PLANS pricing in utils/constants.js.

const GST_RATE = 0.18;

const escapeHtml = (str = "") =>
  String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");

const formatINR = (value) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 }).format(value);

export function buildInvoiceBreakdown(amount) {
  const total = Number(amount) || 0;
  const taxableValue = total / (1 + GST_RATE);
  const totalGst = total - taxableValue;
  const cgst = totalGst / 2;
  const sgst = totalGst / 2;
  return { taxableValue, cgst, sgst, totalGst, total };
}

export function invoiceNumberFor(txn) {
  return `INV-${(txn.id || "").replace(/[^a-zA-Z0-9]/g, "").slice(-8).toUpperCase() || "000000"}`;
}

export function buildInvoiceHTML(txn) {
  const { taxableValue, cgst, sgst, total } = buildInvoiceBreakdown(txn.amount);
  const isCreditNote = txn.status === "Refunded";
  const docLabel = isCreditNote ? "CREDIT NOTE (Refund)" : "TAX INVOICE";
  const invoiceNo = invoiceNumberFor(txn);
  const issueDate = txn.date ? new Date(txn.date) : new Date();
  const issueDateStr = isNaN(issueDate.getTime())
    ? escapeHtml(txn.date || "")
    : issueDate.toLocaleDateString("en-IN", { day: "2-digit", month: "short", year: "numeric" });

  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<title>${docLabel} ${escapeHtml(invoiceNo)}</title>
<style>
  * { box-sizing: border-box; }
  body { font-family: 'Segoe UI', Arial, sans-serif; color: #1f2933; margin: 0; padding: 40px; background: #fff; }
  .sheet { max-width: 780px; margin: 0 auto; }
  .header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 3px solid #7c3aed; padding-bottom: 20px; margin-bottom: 24px; }
  .brand { font-size: 22px; font-weight: 700; color: #7c3aed; }
  .brand-sub { font-size: 12px; color: #6b7280; margin-top: 4px; line-height: 1.5; }
  .doc-title { text-align: right; }
  .doc-title h1 { font-size: 20px; margin: 0 0 6px; letter-spacing: 0.5px; }
  .doc-title p { margin: 2px 0; font-size: 12px; color: #6b7280; }
  .meta-grid { display: flex; justify-content: space-between; gap: 24px; margin-bottom: 24px; }
  .meta-box { flex: 1; }
  .meta-box h3 { font-size: 11px; text-transform: uppercase; letter-spacing: 0.6px; color: #9ca3af; margin: 0 0 6px; }
  .meta-box p { margin: 2px 0; font-size: 13px; }
  table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }
  th { text-align: left; background: #f5f3ff; color: #4c1d95; font-size: 12px; text-transform: uppercase; letter-spacing: 0.4px; padding: 10px 12px; border-bottom: 2px solid #ddd6fe; }
  td { padding: 10px 12px; font-size: 13px; border-bottom: 1px solid #eee; }
  td.num, th.num { text-align: right; }
  .totals { width: 320px; margin-left: auto; }
  .totals div { display: flex; justify-content: space-between; padding: 6px 12px; font-size: 13px; }
  .totals .grand { font-weight: 700; font-size: 15px; border-top: 2px solid #7c3aed; margin-top: 4px; padding-top: 10px; color: #4c1d95; }
  .status-badge { display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 11px; font-weight: 600; }
  .status-success { background: #dcfce7; color: #166534; }
  .status-refunded { background: #f3f4f6; color: #374151; }
  .footer { margin-top: 32px; font-size: 11px; color: #9ca3af; border-top: 1px solid #eee; padding-top: 16px; line-height: 1.6; }
  .print-bar { text-align: right; margin-bottom: 16px; }
  .print-bar button { background: #7c3aed; color: #fff; border: none; padding: 8px 16px; border-radius: 6px; font-size: 13px; cursor: pointer; }
  @media print { .print-bar { display: none; } body { padding: 0; } }
</style>
</head>
<body>
  <div class="sheet">
    <div class="print-bar"><button onclick="window.print()">Print / Save as PDF</button></div>

    <div class="header">
      <div>
        <div class="brand">Moriah Skill Hub</div>
        <div class="brand-sub">
          Moriah Skill Hub Technologies Pvt. Ltd.<br />
          Hyderabad, Telangana, India — 500081<br />
          GSTIN: 36AAMCM1234A1Z5 &nbsp;|&nbsp; PAN: AAMCM1234A
        </div>
      </div>
      <div class="doc-title">
        <h1>${docLabel}</h1>
        <p><strong>Invoice No:</strong> ${escapeHtml(invoiceNo)}</p>
        <p><strong>Invoice Date:</strong> ${issueDateStr}</p>
        <p><strong>Reference Txn ID:</strong> ${escapeHtml(txn.id || "")}</p>
      </div>
    </div>

    <div class="meta-grid">
      <div class="meta-box">
        <h3>Billed To</h3>
        <p><strong>${escapeHtml(txn.student || "")}</strong></p>
        <p>Student, Moriah Skill Hub</p>
      </div>
      <div class="meta-box">
        <h3>Payment Details</h3>
        <p>Gateway: ${escapeHtml(txn.gateway || "—")}</p>
        <p>Status: <span class="status-badge ${txn.status === "Success" ? "status-success" : "status-refunded"}">${escapeHtml(txn.status || "")}</span></p>
      </div>
    </div>

    <table>
      <thead>
        <tr>
          <th>Description</th>
          <th class="num">Taxable Value</th>
          <th class="num">CGST (9%)</th>
          <th class="num">SGST (9%)</th>
          <th class="num">Total</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td>${escapeHtml(txn.plan || "Subscription Plan")} — Skill Development Program Fee</td>
          <td class="num">${formatINR(taxableValue)}</td>
          <td class="num">${formatINR(cgst)}</td>
          <td class="num">${formatINR(sgst)}</td>
          <td class="num">${formatINR(total)}</td>
        </tr>
      </tbody>
    </table>

    <div class="totals">
      <div><span>Taxable Value</span><span>${formatINR(taxableValue)}</span></div>
      <div><span>CGST @ 9%</span><span>${formatINR(cgst)}</span></div>
      <div><span>SGST @ 9%</span><span>${formatINR(sgst)}</span></div>
      <div class="grand"><span>${isCreditNote ? "Total Refunded" : "Total Amount Paid"}</span><span>${formatINR(total)}</span></div>
    </div>

    <div class="footer">
      This is a system-generated ${isCreditNote ? "credit note" : "tax invoice"} and does not require a physical signature.
      For billing queries, contact billing@moriahskillhub.com.<br />
      ${isCreditNote ? "This credit note confirms that the above amount was refunded to the original payment method." : "Thank you for choosing Moriah Skill Hub."}
    </div>
  </div>
</body>
</html>`;
}

export function openInvoice(txn) {
  const html = buildInvoiceHTML(txn);
  const win = window.open("", "_blank");
  if (!win) return false;
  win.document.open();
  win.document.write(html);
  win.document.close();
  return true;
}

// Downloads the invoice for a single transaction as its own standalone
// .html file (e.g. "INV-ABC12345.html") — not a zip, and not the app
// bundle. Safe to call from within the invoice preview modal.
export function downloadInvoice(txn) {
  const html = buildInvoiceHTML(txn);
  const invoiceNo = invoiceNumberFor(txn);
  const blob = new Blob([html], { type: "text/html" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${invoiceNo}.html`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}