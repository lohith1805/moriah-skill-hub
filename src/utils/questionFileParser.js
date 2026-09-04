// Parses a bulk-upload file of MCQ questions for the Assessment Bank (and
// Video Lesson quizzes, which use the identical question shape). Two input
// formats are supported so authors can use whichever tool they already have
// open — a spreadsheet, or Word/plain text:
//
// 1. CSV / spreadsheet export, one question per row, columns in this order
//    (a header row is optional and, if present, is skipped automatically):
//      question, optionA, optionB, optionC, optionD, correct
//    "correct" is the letter A/B/C/D of the right option.
//
// 2. Word (.docx) or plain text (.txt), one question per block, blocks
//    separated by a blank line, in this exact line shape:
//      Q: <question text>
//      A) <option>
//      B) <option>
//      C) <option>
//      D) <option>
//      Correct: <A|B|C|D>
//
// Either format returns the same normalized shape so the caller doesn't
// need to know which one was used:
//   { question, options: [a,b,c,d], correctAnswer, __row }  (__row = 1-based
//   position in the source file, used only for error messages)

import Papa from "papaparse";
import mammoth from "mammoth";
import * as XLSX from "xlsx";

const LETTER_INDEX = { A: 0, B: 1, C: 2, D: 3 };

function normalizeFromLetter(text, opts, letter) {
  const idx = LETTER_INDEX[(letter || "").trim().toUpperCase()];
  if (idx === undefined) return null;
  return { question: text.trim(), options: opts.map((o) => o.trim()), correctAnswer: opts[idx]?.trim() };
}

// --- CSV / spreadsheet rows ------------------------------------------
// Shared by CSV (via PapaParse) and Excel (via SheetJS) — both hand us an
// array of arrays, columns: question, optionA, optionB, optionC, optionD, correct.

function parseRows(rows) {
  const questions = [];
  const problems = [];

  rows.forEach((row, i) => {
    if (!row || !row.length || row[0] == null || !String(row[0]).trim()) return;
    // Skip an optional header row, e.g. "question,optionA,optionB,..."
    if (i === 0 && /question/i.test(String(row[0]))) return;

    const cells = row.map((v) => (v == null ? "" : String(v)));
    const [q, a, b, c, d, correct] = cells;
    if (![q, a, b, c, d, correct].every((v) => v && v.trim())) {
      problems.push(`Row ${i + 1}: missing a column (need question, 4 options, and the correct letter).`);
      return;
    }
    const parsed = normalizeFromLetter(q, [a, b, c, d], correct);
    if (!parsed) {
      problems.push(`Row ${i + 1}: "${correct}" isn't A, B, C, or D.`);
      return;
    }
    questions.push({ ...parsed, __row: i + 1 });
  });

  return { questions, problems };
}

function parseCsvText(text) {
  const result = Papa.parse(text.trim(), { skipEmptyLines: true });
  return parseRows(result.data);
}

// --- Word/plain text (Q:/A)/B)/C)/D)/Correct: blocks) ------------------

function parseBlockText(text) {
  const blocks = text
    .replace(/\r\n/g, "\n")
    .split(/\n\s*\n/)
    .map((b) => b.trim())
    .filter(Boolean);

  const questions = [];
  const problems = [];

  blocks.forEach((block, i) => {
    const lines = block.split("\n").map((l) => l.trim()).filter(Boolean);
    const qLine = lines.find((l) => /^q[:.)]/i.test(l));
    const optA = lines.find((l) => /^a[).]/i.test(l));
    const optB = lines.find((l) => /^b[).]/i.test(l));
    const optC = lines.find((l) => /^c[).]/i.test(l));
    const optD = lines.find((l) => /^d[).]/i.test(l));
    const correctLine = lines.find((l) => /^correct/i.test(l));

    if (!qLine || !optA || !optB || !optC || !optD || !correctLine) {
      problems.push(`Question block ${i + 1}: expected "Q:", "A)"–"D)", and "Correct:" lines — one or more are missing.`);
      return;
    }

    const strip = (l, re) => l.replace(re, "").trim();
    const q = strip(qLine, /^q[:.)]\s*/i);
    const opts = [strip(optA, /^a[).]\s*/i), strip(optB, /^b[).]\s*/i), strip(optC, /^c[).]\s*/i), strip(optD, /^d[).]\s*/i)];
    const correctLetter = strip(correctLine, /^correct[:.]?\s*/i);

    const parsed = normalizeFromLetter(q, opts, correctLetter);
    if (!parsed) {
      problems.push(`Question block ${i + 1}: "${correctLetter}" isn't A, B, C, or D.`);
      return;
    }
    questions.push({ ...parsed, __row: i + 1 });
  });

  return { questions, problems };
}

// --- Entry point --------------------------------------------------------

// Returns { questions, problems }. `problems` is a list of human-readable
// strings for rows/blocks that couldn't be parsed — the caller decides
// whether to still import the rest or block entirely.
export async function parseQuestionFile(file) {
  const name = file.name.toLowerCase();

  if (name.endsWith(".csv")) {
    const text = await file.text();
    return parseCsvText(text);
  }

  if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
    const buf = await file.arrayBuffer();
    const wb = XLSX.read(buf, { type: "array" });
    const sheet = wb.Sheets[wb.SheetNames[0]];
    if (!sheet) return { questions: [], problems: ["The spreadsheet has no sheets."] };
    const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, blankrows: false, defval: "" });
    return parseRows(rows);
  }

  if (name.endsWith(".docx")) {
    const arrayBuffer = await file.arrayBuffer();
    const { value: rawText } = await mammoth.extractRawText({ arrayBuffer });
    // mammoth.extractRawText joins every paragraph with "\n\n", not just
    // blank ones — so a genuine blank paragraph (our block separator) comes
    // out as 4+ newlines, while an ordinary paragraph break is exactly 2.
    // Normalize both back to the same convention parseBlockText expects
    // (single \n within a block, one blank line between blocks) before
    // reusing the same block parser as .txt files.
    const normalized = rawText
      .replace(/\r\n/g, "\n")
      .replace(/\n{3,}/g, "\uE000")
      .replace(/\n{2}/g, "\n")
      .replace(/\uE000/g, "\n\n");
    return parseBlockText(normalized);
  }

  if (name.endsWith(".txt")) {
    const text = await file.text();
    return parseBlockText(text);
  }

  return { questions: [], problems: [`Unsupported file type "${file.name.split(".").pop()}". Use .csv, .xlsx, .xls, .txt, or .docx.`] };
}