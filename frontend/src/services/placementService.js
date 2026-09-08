// Client placement pipeline — WIRED to the backend module (/api/v1/placements).
// A placement is created server-side when a recruitment request is APPROVED
// (see clientService.requestRecruitment -> HR/ADMIN decide). This service is
// the single read/write path for client/TalentPool.jsx, student/Interviews.jsx
// and hr/Documents.jsx; utils/placementPipeline.js now only holds the display
// helpers (stage labels, tone, progress, checklist shapes).
import { apiClient } from "./apiClient";
import { BACKEND_TO_FE_STAGE, FE_TO_BACKEND_STAGE } from "../utils/placementPipeline";

const asRows = (res) => (Array.isArray(res) ? res : res?.content ?? []);

// A backend Placement -> the flat "recruitment" record every page already
// expects: identity fields + the current stage as an FE label + every key of
// `details` spread to the top level (date, time, roundType, meetingLink,
// notes, technicalRating, hrRoundDate, documents, offerText, offerCtc,
// clientSignedAt, studentSignedAt, rejectedAt, …).
export function toFeRecruitment(p) {
  return {
    id: p.id,
    recruitmentRequestId: p.recruitmentRequestId ?? null,
    candidateId: p.candidateUuid,
    candidateUuid: p.candidateUuid,
    candidateName: p.candidateName || "",
    clientUuid: p.clientUuid || null,
    clientName: p.clientName || "",
    stage: BACKEND_TO_FE_STAGE[p.stage] || p.stage,
    backendStage: p.stage,
    createdAt: p.createdAt || null,
    updatedAt: p.updatedAt || null,
    ...(p.details && typeof p.details === "object" ? p.details : {}),
  };
}

export async function getRecruitments({ stage } = {}) {
  const params = {};
  if (stage) params.stage = FE_TO_BACKEND_STAGE[stage] || stage;
  const res = await apiClient.get("/placements", Object.keys(params).length ? params : undefined);
  return asRows(res).map(toFeRecruitment);
}

export async function getRecruitment(id) {
  return toFeRecruitment(await apiClient.get(`/placements/${id}`));
}

// GET /api/v1/placements/candidates (HR/ADMIN) — every client-shortlisted
// candidate as a picker option for HR letter generation. `details` carries the
// placement's track / designation / ctc / offer* keys for prefilling the form.
export async function getPlacementCandidates() {
  const res = await apiClient.get("/placements/candidates");
  return asRows(res).map((c) => {
    const d = c.details && typeof c.details === "object" ? c.details : {};
    return {
      placementId: c.placementId,
      candidateUuid: c.candidateUuid,
      candidateName: c.candidateName || "",
      clientUuid: c.clientUuid || null,
      clientContactName: c.clientContactName || "",
      stage: BACKEND_TO_FE_STAGE[c.stage] || c.stage,
      backendStage: c.stage,
      graduated: !!c.graduated,
      track: d.offerTrack || d.track || "",
      designation: d.offerDesignation || d.designation || "",
      department: d.offerDepartment || d.department || "",
      ctc: d.offerCtc || d.ctc || "",
      clientName: d.offerClientName || "",
    };
  });
}

// Advance (or update details on) one placement. `feStage` is an FE label or a
// backend enum; `detailsPatch` is merged server-side (a null value drops a key).
export async function advancePlacement(id, feStage, detailsPatch = {}) {
  const res = await apiClient.put(`/placements/${id}`, {
    stage: FE_TO_BACKEND_STAGE[feStage] || feStage,
    details: detailsPatch || {},
  });
  return toFeRecruitment(res);
}

// Details-only update: keep the current stage, merge fields.
export async function updatePlacementDetails(id, currentFeStage, detailsPatch) {
  return advancePlacement(id, currentFeStage, detailsPatch);
}
