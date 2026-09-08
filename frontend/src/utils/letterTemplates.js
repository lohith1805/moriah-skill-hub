// Shared document templates + renderer. Originally lived only in
// hr/Documents.jsx; pulled out here so client/TalentPool.jsx (Client Review
// & Signature) and student/Interviews.jsx (Student Approval + Signature)
// can render the exact same offer-letter text HR generated, without
// duplicating the template copy.

export const DEFAULT_TEMPLATES = [
  {
    value: "student_offer",
    label: "Student Admission & Offer Letter",
    text: `MORIAH SKILL HUB — STUDENT ADMISSION OFFER

Date: {{date}}
Reference: MSH-ADM-{{id}}

Dear {{name}},

Congratulations! We are pleased to formally offer you admission into the {{track}} Track at Moriah Skill Hub.

Program Details:
• Cohort Track: {{track}}
• Joining Date: {{date}}
• Sponsoring / Recruiting Partner: {{clientName}}
• Pedagogy: Agile Sprint Simulation & Live Industry Projects
• Mentorship: 1-on-1 Code Reviews & Daily Standups

Please review the attached terms and submit your digital e-signature to confirm your enrollment seat.

Authorized Signatory,
Director of Admissions & Training,
Moriah Skill Hub Inc.`
  },
  {
    value: "internship_agreement",
    label: "Internship & IP Agreement",
    text: `MORIAH SKILL HUB — INTERNSHIP & APPRENTICESHIP AGREEMENT

Date: {{date}}
Reference: MSH-INT-{{id}}

This Internship Agreement is entered into between Moriah Skill Hub ("Company") and {{name}} ("Intern").

1. SCOPE OF APPRENTICESHIP: The Intern will participate in active development sprints, submitting pull requests, and joining weekly sprint demo ceremonies.
2. STIPEND & COMPENSATION: Monthly performance stipend as defined during onboarding.
3. INTELLECTUAL PROPERTY: All project source code, architectures, and client deliverables created during the internship remain the exclusive property of Moriah Skill Hub and its enterprise sponsors.
4. CONFIDENTIALITY: The Intern agrees to maintain strict confidentiality of proprietary tools and client data.

Digitally Sealed & Authorized,
HR & Legal Counsel, Moriah Skill Hub`
  },
  {
    value: "employment_contract",
    label: "Employment Contract",
    text: `MORIAH SKILL HUB — FULL-TIME EMPLOYMENT CONTRACT

Date: {{date}}
Reference: MSH-EMP-{{id}}

Dear {{name}},

We are delighted to offer you full-time employment at Moriah Skill Hub as {{designation}}.

Terms of Employment:
• Designation: {{designation}}
• Department: {{department}}
• Annual Compensation (CTC): {{ctc}}
• Reporting Lead: Head of Engineering & Operations
• Probation Period: 3 Months from Joining Date

By affixing your digital signature below, you accept the terms and conditions outlined in our standard employee handbook.

Warm regards,
Head of Human Resources, Moriah Skill Hub`
  },
  {
    value: "placement_confirmation",
    label: "Candidate Placement & Offer Confirmation",
    text: `MORIAH SKILL HUB — PLACEMENT & OFFER CONFIRMATION

Date: {{date}}
Reference: MSH-PLC-{{id}}

Dear {{name}},

Congratulations! Following your interview round(s) and successful document verification, {{clientName}} has selected you through the Moriah Skill Hub Talent Pool for the {{track}} track.

Placement Details:
• Track: {{track}}
• Recruiting Company: {{clientName}}
• Annual CTC: {{ctc}}
• Status: Documents Verified — Offer Confirmed

This letter formally confirms your successful placement facilitated by Moriah Skill Hub. Please review the terms below, and route this letter through Client Review & Signature followed by your own approval and digital signature to finalize your placement.

Digitally issued & authorized,
HR Placement Cell, Moriah Skill Hub`
  },
  {
    value: "experience_certificate",
    label: "Experience & Relieving Certificate",
    text: `MORIAH SKILL HUB — EXPERIENCE & RELIEVING CERTIFICATE

Date: {{date}}
Reference: MSH-EXP-{{id}}

TO WHOMSOEVER IT MAY CONCERN

This is to certify that {{name}} was employed with Moriah Skill Hub from {{joinDate}} to {{date}} as {{designation}}.

During their tenure with us, {{name}} demonstrated exemplary technical expertise, professionalism, and commitment to software engineering excellence.

All dues have been cleared. We wish them the very best in all future endeavors.

Authorized HR Specialist,
Moriah Skill Hub Platform`
  }
];

export function renderTemplateText(doc, templates = DEFAULT_TEMPLATES) {
  if (!doc) return "";
  const tpl = templates.find((t) => t.value === doc.type) || DEFAULT_TEMPLATES[0];
  return tpl.text
    .replace(/{{name}}/g, doc.name)
    .replace(/{{date}}/g, doc.date)
    .replace(/{{id}}/g, doc.id.slice(-6))
    .replace(/{{track}}/g, doc.track || "Full Stack Engineering")
    .replace(/{{designation}}/g, doc.designation || "Software Engineer")
    .replace(/{{department}}/g, doc.department || "Technology")
    .replace(/{{ctc}}/g, doc.ctc || "₹6,00,000 / annum")
    .replace(/{{joinDate}}/g, "2026-01-10")
    .replace(/{{clientName}}/g, doc.clientName || "the recruiting company");
}