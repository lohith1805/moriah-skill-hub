# Moriah Skill Hub — API Documentation

> Generated from `docs/openapi.json` (OpenAPI 3.1.0). 113 endpoints across 24 groups. Auth/role column is read from each controller's `@PreAuthorize`.

## Conventions

**Base URL** — `/api/v1` (prefix shown in full on every route below). Dev: `http://localhost:8080`.

**Auth** — Bearer JWT access token in the `Authorization` header on every non-public route:

```
Authorization: Bearer <accessToken>
```

Obtain a token from `POST /api/v1/auth/login` (or the OAuth2 flow). Access tokens last 60 min; refresh with `POST /api/v1/auth/refresh`. ADMIN / HR_MANAGER accounts must complete a 2FA challenge on login before a token pair is issued.

**Response envelope** — every endpoint returns this wrapper (never a bare object or list):

```json
{
  "success": true,
  "data": {
    "...": "the payload described per-endpoint below"
  },
  "error": null,
  "timestamp": "2026-01-15T10:30:00Z"
}
```

On error, `success` is `false`, `data` is `null`, and `error` is populated (`fieldErrors` is present only for validation failures):

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Human-readable reason",
    "fieldErrors": [
      {
        "field": "email",
        "message": "must not be blank"
      }
    ]
  },
  "timestamp": "2026-01-15T10:30:00Z"
}
```

Common error codes: `VALIDATION_FAILED` (400), `INVALID_CREDENTIALS` (401), `UNAUTHENTICATED` (401), `INSUFFICIENT_ROLE` / `NOT_RESOURCE_OWNER` (403), `RESOURCE_NOT_FOUND` (404), `RATE_LIMIT_EXCEEDED` (429), `INTERNAL_ERROR` (500).

**Pagination** — list endpoints accept `?page=0&size=20&sort=field,asc` (max `size` = 100) and return:

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "...": "row"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 137,
    "totalPages": 7,
    "last": false
  },
  "error": null,
  "timestamp": "2026-01-15T10:30:00Z"
}
```

**Rate limits** — `POST /api/v1/auth/**`: 10/min per client IP. All other routes: 300/min per bearer token (per client IP when unauthenticated).

**OAuth2 login (not in this list / not in `openapi.json`)** — the Google & GitHub login endpoints are handled by Spring Security's filter chain, not a controller, so springdoc cannot document them. Browser flow:

| Step | Endpoint |
|---|---|
| Start | `GET /api/v1/auth/oauth2/authorize/{google\|github}` → 302 to the provider |
| Provider redirects back | `GET /api/v1/auth/oauth2/callback/{google\|github}?code=…&state=…` |

On success the callback returns the **same `LoginResponse` envelope as `POST /api/v1/auth/login`** (token pair, or a 2FA challenge). Register the redirect URI `{baseUrl}/api/v1/auth/oauth2/callback/{google|github}` with the provider and set the client id/secret in `.env` (see `docs/required-integrations.md`).

---

## Contents

- [Admin](#admin) — 13 endpoints
- [Assessments](#assessments) — 5 endpoints
- [Attendance](#attendance) — 4 endpoints
- [Auth](#auth) — 13 endpoints
- [BA](#ba) — 3 endpoints
- [Batches](#batches) — 8 endpoints
- [Bug Challenges](#bug-challenges) — 1 endpoints
- [CRM](#crm) — 5 endpoints
- [Certificates](#certificates) — 4 endpoints
- [Checkout](#checkout) — 1 endpoints
- [Clients](#clients) — 3 endpoints
- [HR](#hr) — 10 endpoints
- [PIP](#pip) — 6 endpoints
- [Placements](#placements) — 3 endpoints
- [Plans](#plans) — 1 endpoints
- [Projects](#projects) — 5 endpoints
- [Reviews](#reviews) — 3 endpoints
- [Sprints](#sprints) — 6 endpoints
- [Standups](#standups) — 3 endpoints
- [Submissions](#submissions) — 2 endpoints
- [Subscriptions](#subscriptions) — 1 endpoints
- [Tasks](#tasks) — 5 endpoints
- [Users](#users) — 5 endpoints
- [Webhooks](#webhooks) — 3 endpoints

---

## Admin

### `GET` `/api/v1/admin/audit`

Read-only audit log, optionally filtered by entity type, user, and a start date

**Auth:** Role: ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `entityType` | string | no |  |
| `userUuid` | string | no |  |
| `from` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "userUuid": "string",
        "action": "string",
        "entityType": "string",
        "entityId": 0,
        "entityUuid": "string",
        "oldValue": "string",
        "newValue": "string",
        "ipAddress": "string",
        "createdAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`, `403`

---

### `GET` `/api/v1/admin/client-requests`

List client self-registrations awaiting review (default) or already rejected

**Auth:** Role: ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "uuid": "string",
        "fullName": "string",
        "email": "string",
        "phone": "string",
        "companyName": "string",
        "industry": "string",
        "status": "ACTIVE",
        "submittedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/admin/client-requests/{userUuid}/approve`

Approve a client registration — account becomes ACTIVE and the applicant is emailed

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `userUuid` | string |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "phone": "string",
    "companyName": "string",
    "industry": "string",
    "status": "ACTIVE",
    "submittedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`, `409`

---

### `POST` `/api/v1/admin/client-requests/{userUuid}/reject`

Decline a client registration — account becomes REJECTED and the applicant is emailed the reason

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `userUuid` | string |  |

**Request body:**

```json
{
  "reason": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "phone": "string",
    "companyName": "string",
    "industry": "string",
    "status": "ACTIVE",
    "submittedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`, `409`

---

### `POST` `/api/v1/admin/exports/{report}`

Generate an XLSX export (users/revenue/audit) and return a presigned download URL

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `report` | string |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "report": "string",
    "rowCount": 0,
    "deliveredInline": true,
    "downloadUrl": "string",
    "urlExpiresAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `400`, `403`

---

### `GET` `/api/v1/admin/metrics/overview`

Admin KPI overview — attendance/task/quiz averages, recent revenue, lead funnel, batch velocity

**Auth:** Role: ADMIN

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "activeStudentCount": 0,
    "avgAttendancePercent": 0.0,
    "avgTaskCompletionPercent": 0.0,
    "avgQuizAveragePercent": 0.0,
    "recentRevenue": [
      {
        "revenueMonth": "string",
        "currency": "string",
        "totalCaptured": 0.0
      }
    ],
    "leadFunnel": [
      {
        "status": "string",
        "leadCount": 0
      }
    ],
    "totalPlannedPoints": 0,
    "totalCompletedPoints": 0,
    "overallVelocityRatio": 0.0
  },
  "error": null
}
```

**Status codes:** `200`, `403`

---

### `GET` `/api/v1/admin/metrics/revenue`

Monthly captured revenue for an optional date range, defaults to the trailing 12 months

**Auth:** Role: ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `from` | string | no |  |
| `to` | string | no |  |

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "revenueMonth": "string",
      "currency": "string",
      "totalCaptured": 0.0
    }
  ],
  "error": null
}
```

**Status codes:** `200`, `403`

---

### `PUT` `/api/v1/admin/plans/{id}`

Update a subscription plan's pricing/feature flags at runtime — cache evicted immediately

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "name": "string",
  "priceInr": 0.0,
  "durationDays": 0,
  "maxProjects": 0,
  "mentorSupport": true,
  "allowsBatch": true,
  "allowsSprints": true,
  "allowsPip": true,
  "allowsInternshipLetter": true,
  "allowsClientProject": true,
  "active": true
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "code": "string",
    "name": "string",
    "priceInr": 0.0,
    "tierRank": 0,
    "durationDays": 0,
    "mentorSupport": true,
    "allowsBatch": true,
    "allowsSprints": true,
    "allowsPip": true,
    "allowsInternshipLetter": true,
    "allowsClientProject": true
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `GET` `/api/v1/admin/users`

List users, optionally filtered by role and/or status

**Auth:** Role: ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `role` | string | no |  |
| `status` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "uuid": "string",
        "fullName": "string",
        "email": "string",
        "status": "ACTIVE",
        "roles": [
          "STUDENT"
        ],
        "createdAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`, `403`

---

### `POST` `/api/v1/admin/users`

Invite a staff member — creates an INVITED account and emails an accept-invite link. roles must be staff roles (not STUDENT/CLIENT).

**Auth:** Role: ADMIN

**Request body:**

```json
{
  "fullName": "string",
  "email": "user@example.com",
  "phone": "string",
  "roles": [
    "STUDENT"
  ]
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "status": "ACTIVE",
    "roles": [
      "STUDENT"
    ],
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `400`, `403`, `409`

---

### `POST` `/api/v1/admin/users/{userUuid}/resend-invite`

Re-send the accept-invite link for an account still in INVITED state — burns the previous link

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `userUuid` | string |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "status": "ACTIVE",
    "roles": [
      "STUDENT"
    ],
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`, `409`

---

### `PUT` `/api/v1/admin/users/{userUuid}/roles`

Replace a user's role assignments — increments token_version, invalidating every outstanding access token immediately

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `userUuid` | string |  |

**Request body:**

```json
{
  "roles": [
    "STUDENT"
  ]
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "status": "ACTIVE",
    "roles": [
      "STUDENT"
    ],
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `PUT` `/api/v1/admin/users/{userUuid}/status`

Change a user's status — increments token_version, invalidating every outstanding access token immediately

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `userUuid` | string |  |

**Request body:**

```json
{
  "status": "ACTIVE"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "status": "ACTIVE",
    "roles": [
      "STUDENT"
    ],
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

## Assessments

### `GET` `/api/v1/assessments`

List assessments for a batch

**Auth:** Role: TRAINER_PM or ADMIN or STUDENT

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `batchId` | integer | yes |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "batchId": 0,
        "projectId": 0,
        "title": "string",
        "durationMinutes": 0,
        "passPercentage": 0,
        "maxAttempts": 0,
        "active": true
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/assessments`

Create an assessment (quiz) with its questions

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "batchId": 0,
  "projectId": 0,
  "title": "string",
  "durationMinutes": 0,
  "passPercentage": 0,
  "maxAttempts": 0,
  "questions": [
    {
      "questionText": "string",
      "questionType": "MCQ",
      "options": [
        "string"
      ],
      "correctAnswerIndices": [
        0
      ],
      "marks": 0,
      "explanation": "string"
    }
  ]
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "projectId": 0,
    "title": "string",
    "durationMinutes": 0,
    "passPercentage": 0,
    "maxAttempts": 0,
    "active": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/assessments/attempts/{id}`

Get an attempt — its questions while in progress, its graded result once terminal

**Auth:** Role: TRAINER_PM or ADMIN or STUDENT

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "quizId": 0,
    "quizTitle": "string",
    "attemptNumber": 0,
    "status": "IN_PROGRESS",
    "startedAt": "2026-01-15T10:30:00Z",
    "submittedAt": "2026-01-15T10:30:00Z",
    "durationMinutes": 0,
    "questions": [
      {
        "questionId": 0,
        "questionText": "string",
        "questionType": "MCQ",
        "options": [
          "string"
        ],
        "marks": 0,
        "givenAnswerIndices": [
          0
        ],
        "givenCodeAnswer": "string",
        "isCorrect": true,
        "marksAwarded": 0.0,
        "explanation": "string"
      }
    ],
    "autoGradedMarks": 0.0,
    "autoGradableMarks": 0.0,
    "percentage": 0.0,
    "passed": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/assessments/attempts/{id}/submit`

Submit an attempt for grading

**Auth:** Role: STUDENT

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "answers": [
    {
      "questionId": 0,
      "selectedOptionIndices": [
        0
      ],
      "codeAnswer": "string"
    }
  ]
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "quizId": 0,
    "quizTitle": "string",
    "attemptNumber": 0,
    "status": "IN_PROGRESS",
    "startedAt": "2026-01-15T10:30:00Z",
    "submittedAt": "2026-01-15T10:30:00Z",
    "durationMinutes": 0,
    "questions": [
      {
        "questionId": 0,
        "questionText": "string",
        "questionType": "MCQ",
        "options": [
          "string"
        ],
        "marks": 0,
        "givenAnswerIndices": [
          0
        ],
        "givenCodeAnswer": "string",
        "isCorrect": true,
        "marksAwarded": 0.0,
        "explanation": "string"
      }
    ],
    "autoGradedMarks": 0.0,
    "autoGradableMarks": 0.0,
    "percentage": 0.0,
    "passed": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/assessments/{id}/attempts`

Start (or resume) an attempt at an assessment

**Auth:** Role: STUDENT

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "quizId": 0,
    "quizTitle": "string",
    "attemptNumber": 0,
    "status": "IN_PROGRESS",
    "startedAt": "2026-01-15T10:30:00Z",
    "submittedAt": "2026-01-15T10:30:00Z",
    "durationMinutes": 0,
    "questions": [
      {
        "questionId": 0,
        "questionText": "string",
        "questionType": "MCQ",
        "options": [
          "string"
        ],
        "marks": 0,
        "givenAnswerIndices": [
          0
        ],
        "givenCodeAnswer": "string",
        "isCorrect": true,
        "marksAwarded": 0.0,
        "explanation": "string"
      }
    ],
    "autoGradedMarks": 0.0,
    "autoGradableMarks": 0.0,
    "percentage": 0.0,
    "passed": true
  },
  "error": null
}
```

**Status codes:** `200`

---

## Attendance

### `GET` `/api/v1/attendance/batch/{batchId}`

Attendance roster for a batch

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `batchId` | integer |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "standupId": 0,
        "batchId": 0,
        "standupScheduledAt": "2026-01-15T10:30:00Z",
        "userUuid": "string",
        "userFullName": "string",
        "status": "PRESENT",
        "checkedInAt": "2026-01-15T10:30:00Z",
        "blockerNotes": "string",
        "markedByUuid": "string",
        "autoMarked": true
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/attendance/me`

My own attendance history

**Auth:** Role: STUDENT

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "standupId": 0,
        "batchId": 0,
        "standupScheduledAt": "2026-01-15T10:30:00Z",
        "userUuid": "string",
        "userFullName": "string",
        "status": "PRESENT",
        "checkedInAt": "2026-01-15T10:30:00Z",
        "blockerNotes": "string",
        "markedByUuid": "string",
        "autoMarked": true
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/standups/{id}/attendance`

PM override of a student's attendance status

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "userUuid": "string",
  "status": "PRESENT",
  "blockerNotes": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "standupId": 0,
    "batchId": 0,
    "standupScheduledAt": "2026-01-15T10:30:00Z",
    "userUuid": "string",
    "userFullName": "string",
    "status": "PRESENT",
    "checkedInAt": "2026-01-15T10:30:00Z",
    "blockerNotes": "string",
    "markedByUuid": "string",
    "autoMarked": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/standups/{id}/checkin`

Self check in to a standup

**Auth:** Role: STUDENT

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "blockerNotes": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "standupId": 0,
    "batchId": 0,
    "standupScheduledAt": "2026-01-15T10:30:00Z",
    "userUuid": "string",
    "userFullName": "string",
    "status": "PRESENT",
    "checkedInAt": "2026-01-15T10:30:00Z",
    "blockerNotes": "string",
    "markedByUuid": "string",
    "autoMarked": true
  },
  "error": null
}
```

**Status codes:** `200`

---

## Auth

### `POST` `/api/v1/auth/2fa/disable`

Disable 2FA — requires a currently-valid code, not just an authenticated call

**Auth:** Public — no token required

**Request body:**

```json
{
  "totpCode": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/2fa/enable`

Start 2FA setup — generates a secret; confirm it via POST /2fa/verify before it takes effect. Send {} for an already-logged-in caller voluntarily enabling 2FA; send {"challengeToken": "..."} for the mandatory-2FA setup path (LoginResponse.twoFactorSetupRequired = true), where there is no access token yet to authenticate this call with

**Auth:** Public — no token required

**Request body:**

```json
{
  "challengeToken": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "secret": "string",
    "provisioningUri": "string"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/2fa/verify`

Confirms 2FA setup (no challengeToken, uses the caller's access token) or completes a 2FA-gated login (challengeToken from LoginResponse)

**Auth:** Public — no token required

**Request body:**

```json
{
  "challengeToken": "string",
  "totpCode": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "tokens": {
      "accessToken": "string",
      "refreshToken": "string",
      "expiresInSeconds": 0
    }
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/accept-invite`

Redeem a staff accept-invite link — sets the password, activates the account (INVITED -> ACTIVE) and logs in. Returns a 2FA challenge instead of tokens for an invited ADMIN / HR_MANAGER, exactly like a normal first login.

**Auth:** Public — no token required

**Request body:**

```json
{
  "token": "string",
  "password": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "twoFactorRequired": true,
    "twoFactorSetupRequired": true,
    "challengeToken": "string",
    "tokens": {
      "accessToken": "string",
      "refreshToken": "string",
      "expiresInSeconds": 0
    }
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/login`

Log in with email and password. If 2FA is enabled, returns a challenge token instead of a token pair — exchange it at POST /2fa/verify.

**Auth:** Public — no token required

**Request body:**

```json
{
  "email": "user@example.com",
  "password": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "twoFactorRequired": true,
    "twoFactorSetupRequired": true,
    "challengeToken": "string",
    "tokens": {
      "accessToken": "string",
      "refreshToken": "string",
      "expiresInSeconds": 0
    }
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/logout`

Revoke one refresh token and its paired access token

**Auth:** Public — no token required

**Request body:**

```json
{
  "refreshToken": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/logout-all`

Revoke every session for the user identified by this refresh token

**Auth:** Public — no token required

**Request body:**

```json
{
  "refreshToken": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/password/forgot`

Request a password reset link — always responds the same way regardless of whether the email exists

**Auth:** Public — no token required

**Request body:**

```json
{
  "email": "user@example.com"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/password/reset`

Reset a password with the token from the reset link

**Auth:** Public — no token required

**Request body:**

```json
{
  "token": "string",
  "newPassword": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/refresh`

Exchange a refresh token for a new token pair; rotates the refresh token

**Auth:** Public — no token required

**Request body:**

```json
{
  "refreshToken": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "accessToken": "string",
    "refreshToken": "string",
    "expiresInSeconds": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/register`

Register a new student account

**Auth:** Public — no token required

**Request body:**

```json
{
  "fullName": "string",
  "email": "user@example.com",
  "phone": "string",
  "password": "string",
  "githubUsername": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/register/client`

Corporate-client self-registration — creates a PENDING_APPROVAL account that cannot log in until an ADMIN approves it at POST /api/v1/admin/client-requests/{uuid}/approve

**Auth:** Public — no token required

**Request body:**

```json
{
  "fullName": "string",
  "email": "user@example.com",
  "phone": "string",
  "password": "string",
  "companyName": "string",
  "industry": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/auth/verify-email`

Verify an email address with the token from the verification link

**Auth:** Public — no token required

**Request body:**

```json
{
  "token": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`

---

## BA

### `POST` `/api/v1/ba/allocations`

Allocate a student/staff user and batch to a client project

**Auth:** Role: BUSINESS_ANALYST or ADMIN

**Request body:**

```json
{
  "clientProjectId": 0,
  "batchId": 0,
  "userUuid": "string",
  "roleInProject": "string",
  "allocatedDays": 0,
  "storyPointsEstimate": 0,
  "fromDate": "2026-01-15",
  "toDate": "2026-01-15"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "clientProjectId": 0,
    "batchId": 0,
    "userUuid": "string",
    "userFullName": "string",
    "roleInProject": "string",
    "allocatedDays": 0,
    "storyPointsEstimate": 0,
    "fromDate": "2026-01-15",
    "toDate": "2026-01-15"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/ba/documents`

Create a requirement document (BRD/SRS/FRS/USER_STORY) — lands IN_REVIEW directly

**Auth:** Role: BUSINESS_ANALYST or ADMIN

**Request body:**

```json
{
  "clientProjectId": 0,
  "docType": "BRD",
  "title": "string",
  "content": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "clientProjectId": 0,
    "docType": "BRD",
    "title": "string",
    "version": 0,
    "status": "DRAFT",
    "authoredByUuid": "string",
    "authoredByFullName": "string",
    "approvedByUuid": "string",
    "approvedByFullName": "string"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/ba/documents/{id}/approve`

Approve a requirement document — IN_REVIEW to APPROVED

**Auth:** Role: BUSINESS_ANALYST or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "clientProjectId": 0,
    "docType": "BRD",
    "title": "string",
    "version": 0,
    "status": "DRAFT",
    "authoredByUuid": "string",
    "authoredByFullName": "string",
    "approvedByUuid": "string",
    "approvedByFullName": "string"
  },
  "error": null
}
```

**Status codes:** `200`

---

## Batches

### `GET` `/api/v1/batches`

List batches

**Auth:** Role: TRAINER_PM or ADMIN or STUDENT

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "name": "string",
        "trackCode": "string",
        "pmUuid": "string",
        "pmFullName": "string",
        "planTierMinCode": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "capacity": 0,
        "enrolledCount": 0,
        "status": "PLANNED"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/batches`

Create a batch — the caller becomes its PM

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "name": "string",
  "trackCode": "string",
  "planTierMinCode": "string",
  "startDate": "2026-01-15",
  "endDate": "2026-01-15",
  "capacity": 0
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "trackCode": "string",
    "pmUuid": "string",
    "pmFullName": "string",
    "planTierMinCode": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "capacity": 0,
    "enrolledCount": 0,
    "status": "PLANNED"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/batches/{id}`

Get one batch

**Auth:** Role: TRAINER_PM or ADMIN or STUDENT

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "trackCode": "string",
    "pmUuid": "string",
    "pmFullName": "string",
    "planTierMinCode": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "capacity": 0,
    "enrolledCount": 0,
    "status": "PLANNED"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/batches/{id}`

Update a batch

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "name": "string",
  "planTierMinCode": "string",
  "startDate": "2026-01-15",
  "endDate": "2026-01-15",
  "capacity": 0,
  "status": "PLANNED"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "trackCode": "string",
    "pmUuid": "string",
    "pmFullName": "string",
    "planTierMinCode": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "capacity": 0,
    "enrolledCount": 0,
    "status": "PLANNED"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/batches/{id}/students`

The batch roster - enrolled students with their uuid + status (a TRAINER_PM must own the batch). Feeds task assignment, graduation and letters.

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "userUuid": "string",
      "fullName": "string",
      "email": "string",
      "status": "ACTIVE",
      "joinedAt": "2026-01-15T10:30:00Z",
      "graduatedAt": "2026-01-15T10:30:00Z",
      "finalScore": 0.0
    }
  ],
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/batches/{id}/students`

Manually add a student to a batch

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "userUuid": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "trackCode": "string",
    "pmUuid": "string",
    "pmFullName": "string",
    "planTierMinCode": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "capacity": 0,
    "enrolledCount": 0,
    "status": "PLANNED"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `DELETE` `/api/v1/batches/{id}/students/{userUuid}`

Remove a student from a batch (sets status to REASSIGNED, never deletes)

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |
| `userUuid` | string |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/batches/{id}/students/{userUuid}/graduate`

Graduate an ACTIVE student — required before a certificate can be issued

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |
| `userUuid` | string |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "batchId": 0,
    "batchName": "string",
    "userUuid": "string",
    "userFullName": "string",
    "graduatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

## Bug Challenges

### `POST` `/api/v1/projects/{id}/challenges`

Attach a bug-fix challenge — brokenCode required, testScript optional

**Auth:** Role: DEVELOPER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `title` | string | yes |  |
| `expectedBehaviour` | string | yes |  |
| `difficulty` | string | no |  |

**Request body:**

```json
{
  "brokenCode": "<binary>",
  "testScript": "<binary>"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "expectedBehaviour": "string",
    "brokenCodeUrl": "string",
    "testScriptUrl": "string",
    "difficulty": "BEGINNER"
  },
  "error": null
}
```

**Status codes:** `200`

---

## CRM

### `GET` `/api/v1/leads`

**Auth:** Role: LEAD_GEN or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |
| `agentUuid` | string | no |  |
| `source` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "name": "string",
        "email": "string",
        "phone": "string",
        "source": "LANDING_PAGE",
        "leadType": "string",
        "institution": "string",
        "interestedPlanId": 0,
        "status": "NEW",
        "assignedAgentUuid": "string",
        "assignedAgentName": "string",
        "lostReason": "string",
        "convertedUserUuid": "string",
        "createdAt": "2026-01-15T10:30:00Z",
        "updatedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/leads`

**Auth:** Role: LEAD_GEN or ADMIN

**Request body:**

```json
{
  "name": "string",
  "email": "user@example.com",
  "phone": "string",
  "source": "LANDING_PAGE",
  "leadType": "string",
  "institution": "string",
  "interestedPlanId": 0
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "email": "string",
    "phone": "string",
    "source": "LANDING_PAGE",
    "leadType": "string",
    "institution": "string",
    "interestedPlanId": 0,
    "status": "NEW",
    "assignedAgentUuid": "string",
    "assignedAgentName": "string",
    "lostReason": "string",
    "convertedUserUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/leads/targets/me`

**Auth:** Role: LEAD_GEN or ADMIN

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "agentUuid": "string",
    "periodMonth": "2026-01-15",
    "callsTarget": 0,
    "callsMade": 0,
    "conversionsTarget": 0,
    "conversionsMade": 0,
    "revenueTarget": 0.0,
    "revenueAchieved": 0.0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/leads/{id}/activities`

**Auth:** Role: LEAD_GEN or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "activityType": "CALL",
  "outcome": "string",
  "notes": "string",
  "nextFollowUpAt": "2026-01-15T10:30:00Z",
  "occurredAt": "2026-01-15T10:30:00Z",
  "templateCode": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "leadId": 0,
    "agentUuid": "string",
    "agentName": "string",
    "activityType": "CALL",
    "outcome": "string",
    "notes": "string",
    "nextFollowUpAt": "2026-01-15T10:30:00Z",
    "occurredAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/leads/{id}/status`

**Auth:** Role: LEAD_GEN or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "newStatus": "NEW",
  "reason": "string",
  "lostReason": "string",
  "convertedUserUuid": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "email": "string",
    "phone": "string",
    "source": "LANDING_PAGE",
    "leadType": "string",
    "institution": "string",
    "interestedPlanId": 0,
    "status": "NEW",
    "assignedAgentUuid": "string",
    "assignedAgentName": "string",
    "lostReason": "string",
    "convertedUserUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

## Certificates

### `POST` `/api/v1/certificates/issue`

Issue a certificate — requires GRADUATED, no open PIP, and every sprint COMPLETED

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "batchId": 0,
  "userUuid": "string",
  "certificateType": "COMPLETION"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "certificateNumber": "string",
    "userUuid": "string",
    "userFullName": "string",
    "batchId": 0,
    "batchName": "string",
    "certificateType": "COMPLETION",
    "verificationCode": "string",
    "downloadUrl": "string",
    "issuedAt": "2026-01-15T10:30:00Z",
    "revokedAt": "2026-01-15T10:30:00Z",
    "revokeReason": "string"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/certificates/me`

The caller's own issued certificates

**Auth:** Role: STUDENT

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "certificateNumber": "string",
        "userUuid": "string",
        "userFullName": "string",
        "batchId": 0,
        "batchName": "string",
        "certificateType": "COMPLETION",
        "verificationCode": "string",
        "downloadUrl": "string",
        "issuedAt": "2026-01-15T10:30:00Z",
        "revokedAt": "2026-01-15T10:30:00Z",
        "revokeReason": "string"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/certificates/verify/{code}`

Public certificate verification by code — never accepts a certificate id

**Auth:** Public — no token required

**Path parameters:**

| name | type | description |
|---|---|---|
| `code` | string |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "holderFullName": "string",
    "batchName": "string",
    "trackCode": "string",
    "certificateType": "COMPLETION",
    "issuedAt": "2026-01-15T10:30:00Z",
    "valid": true,
    "revokedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/certificates/{id}/revoke`

Revoke a certificate — never deletes it, still resolves publicly as invalid

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "reason": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "certificateNumber": "string",
    "userUuid": "string",
    "userFullName": "string",
    "batchId": 0,
    "batchName": "string",
    "certificateType": "COMPLETION",
    "verificationCode": "string",
    "downloadUrl": "string",
    "issuedAt": "2026-01-15T10:30:00Z",
    "revokedAt": "2026-01-15T10:30:00Z",
    "revokeReason": "string"
  },
  "error": null
}
```

**Status codes:** `200`

---

## Checkout

### `POST` `/api/v1/subscriptions/checkout`

Create a payment order/session for a plan; never activates anything directly

**Auth:** Authenticated (any logged-in user)

**Request body:**

```json
{
  "planCode": "string",
  "gateway": "RAZORPAY",
  "couponCode": "string",
  "trackCode": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "gateway": "RAZORPAY",
    "paymentId": 0,
    "amount": 0.0,
    "currency": "string",
    "razorpayOrderId": "string",
    "razorpayKeyId": "string",
    "stripeCheckoutUrl": "string"
  },
  "error": null
}
```

**Status codes:** `200`

---

## Clients

### `POST` `/api/v1/clients`

Provision a client company, optionally with a portal login

**Auth:** Role: ADMIN

**Request body:**

```json
{
  "companyName": "string",
  "contactPerson": "string",
  "email": "user@example.com",
  "phone": "string",
  "industry": "string",
  "provisionPortalLogin": true
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "companyName": "string",
    "contactPerson": "string",
    "email": "string",
    "phone": "string",
    "industry": "string",
    "userUuid": "string",
    "status": "ACTIVE"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/clients/projects`

Submit a new project scope as the caller's own client company

**Auth:** Role: CLIENT

**Request body:**

```json
{
  "title": "string",
  "scopeDescription": "string",
  "budgetRange": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "clientId": 0,
    "title": "string",
    "scopeDescription": "string",
    "budgetRange": "string",
    "targetBatchId": 0,
    "status": "SUBMITTED",
    "submittedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/clients/projects/{id}/progress`

Burndown and milestone completion for one client project — never another client's data

**Auth:** Role: CLIENT or BUSINESS_ANALYST or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "clientProjectId": 0,
    "title": "string",
    "targetBatchId": 0,
    "milestoneCompletionFraction": 0.0,
    "burndown": [
      {
        "sprintId": 0,
        "sprintNumber": 0,
        "sprintStatus": "string",
        "plannedPoints": 0,
        "completedPoints": 0
      }
    ]
  },
  "error": null
}
```

**Status codes:** `200`

---

## HR

### `GET` `/api/v1/hr/documents`

List HR documents - an employee sees only their own; HR_MANAGER / ADMIN see all, optionally filtered by status / userUuid / documentType. Each row carries a short-lived presigned downloadUrl.

**Auth:** Authenticated (any logged-in user)

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |
| `userUuid` | string | no |  |
| `documentType` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "userUuid": "string",
        "documentType": "string",
        "verificationStatus": "PENDING",
        "verifiedByUuid": "string",
        "verifiedAt": "2026-01-15T10:30:00Z",
        "rejectionReason": "string",
        "userFullName": "string",
        "downloadUrl": "string",
        "createdAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/hr/documents`

**Auth:** Authenticated (any logged-in user)

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `documentType` | string | yes |  |

**Request body:**

```json
{
  "file": "<binary>"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "userUuid": "string",
    "documentType": "string",
    "verificationStatus": "PENDING",
    "verifiedByUuid": "string",
    "verifiedAt": "2026-01-15T10:30:00Z",
    "rejectionReason": "string",
    "userFullName": "string",
    "downloadUrl": "string",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/hr/documents/{id}/verify`

**Auth:** Role: HR_MANAGER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "decision": "PENDING",
  "rejectionReason": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "userUuid": "string",
    "documentType": "string",
    "verificationStatus": "PENDING",
    "verifiedByUuid": "string",
    "verifiedAt": "2026-01-15T10:30:00Z",
    "rejectionReason": "string",
    "userFullName": "string",
    "downloadUrl": "string",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/hr/employees`

**Auth:** Role: HR_MANAGER or ADMIN

**Request body:**

```json
{
  "userUuid": "string",
  "employeeCode": "string",
  "department": "string",
  "designation": "string",
  "employmentType": "FULL_TIME",
  "dateOfJoining": "2026-01-15",
  "baseSalary": 0.0,
  "hourlyRate": 0.0,
  "reportingManagerId": 0
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "userUuid": "string",
    "fullName": "string",
    "employeeCode": "string",
    "department": "string",
    "designation": "string",
    "employmentType": "FULL_TIME",
    "dateOfJoining": "2026-01-15",
    "dateOfExit": "2026-01-15",
    "baseSalary": 0.0,
    "hourlyRate": 0.0,
    "reportingManagerId": 0,
    "status": "ACTIVE"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/hr/leaves`

List leave requests - an employee sees only their own; HR_MANAGER / ADMIN see all, optionally filtered by status and userUuid

**Auth:** Authenticated (any logged-in user)

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |
| `userUuid` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "userUuid": "string",
        "leaveType": "SICK",
        "fromDate": "2026-01-15",
        "toDate": "2026-01-15",
        "days": 0.0,
        "reason": "string",
        "status": "PENDING",
        "approvedByUuid": "string",
        "decidedAt": "2026-01-15T10:30:00Z",
        "userFullName": "string",
        "createdAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/hr/leaves`

**Auth:** Authenticated (any logged-in user)

**Request body:**

```json
{
  "leaveType": "SICK",
  "fromDate": "2026-01-15",
  "toDate": "2026-01-15",
  "reason": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "userUuid": "string",
    "leaveType": "SICK",
    "fromDate": "2026-01-15",
    "toDate": "2026-01-15",
    "days": 0.0,
    "reason": "string",
    "status": "PENDING",
    "approvedByUuid": "string",
    "decidedAt": "2026-01-15T10:30:00Z",
    "userFullName": "string",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/hr/leaves/{id}/decision`

**Auth:** Authenticated (any logged-in user)

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "decision": "PENDING"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "userUuid": "string",
    "leaveType": "SICK",
    "fromDate": "2026-01-15",
    "toDate": "2026-01-15",
    "days": 0.0,
    "reason": "string",
    "status": "PENDING",
    "approvedByUuid": "string",
    "decidedAt": "2026-01-15T10:30:00Z",
    "userFullName": "string",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/hr/letters/{type}`

**Auth:** Role: HR_MANAGER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `type` | string |  |

**Request body:**

```json
{
  "userUuid": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "downloadUrl": "string",
    "expiresAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/hr/payroll`

**Auth:** Role: HR_MANAGER or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `month` | string | yes |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "employeeId": 0,
        "employeeCode": "string",
        "employeeFullName": "string",
        "periodMonth": "2026-01-15",
        "workingDays": 0,
        "presentDays": 0,
        "sessionHours": 0.0,
        "grossAmount": 0.0,
        "deductions": 0.0,
        "netAmount": 0.0,
        "payslipDownloadUrl": "string",
        "status": "DRAFT"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/hr/payroll/generate`

**Auth:** Role: HR_MANAGER or ADMIN

**Request body:**

```json
{
  "periodMonth": "2026-01-15",
  "workingDays": 0,
  "lines": [
    {
      "employeeId": 0,
      "presentDays": 0,
      "sessionHours": 0.0,
      "deductions": 0.0
    }
  ]
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "id": 0,
      "employeeId": 0,
      "employeeCode": "string",
      "employeeFullName": "string",
      "periodMonth": "2026-01-15",
      "workingDays": 0,
      "presentDays": 0,
      "sessionHours": 0.0,
      "grossAmount": 0.0,
      "deductions": 0.0,
      "netAmount": 0.0,
      "payslipDownloadUrl": "string",
      "status": "DRAFT"
    }
  ],
  "error": null
}
```

**Status codes:** `200`

---

## PIP

### `GET` `/api/v1/pip`

Browse PIP records

**Auth:** Role: TRAINER_PM or HR_MANAGER or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `batchId` | integer | no |  |
| `status` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "studentUuid": "string",
        "studentFullName": "string",
        "batchId": 0,
        "batchName": "string",
        "ruleCode": "ATTENDANCE_LOW",
        "triggerReason": "string",
        "severity": "LOW",
        "triggeredAt": "2026-01-15T10:30:00Z",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "status": "TRIGGERED",
        "blocksTaskPull": true,
        "reviewedByUuid": "string",
        "reviewNotes": "string",
        "outcomeAt": "2026-01-15T10:30:00Z",
        "milestones": [
          {
            "id": 0,
            "title": "string",
            "description": "string",
            "dueDate": "2026-01-15",
            "status": "PENDING",
            "completedAt": "2026-01-15T10:30:00Z",
            "verifiedByUuid": "string"
          }
        ]
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/pip/me`

The caller's own currently-open PIP record

**Auth:** Role: STUDENT

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "studentUuid": "string",
    "studentFullName": "string",
    "batchId": 0,
    "batchName": "string",
    "ruleCode": "ATTENDANCE_LOW",
    "triggerReason": "string",
    "severity": "LOW",
    "triggeredAt": "2026-01-15T10:30:00Z",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "TRIGGERED",
    "blocksTaskPull": true,
    "reviewedByUuid": "string",
    "reviewNotes": "string",
    "outcomeAt": "2026-01-15T10:30:00Z",
    "milestones": [
      {
        "id": 0,
        "title": "string",
        "description": "string",
        "dueDate": "2026-01-15",
        "status": "PENDING",
        "completedAt": "2026-01-15T10:30:00Z",
        "verifiedByUuid": "string"
      }
    ]
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/pip/rules`

The six PIP rules and their current thresholds

**Auth:** Role: TRAINER_PM or HR_MANAGER or ADMIN

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "ruleCode": "ATTENDANCE_LOW",
      "description": "string",
      "thresholdValue": 0.0,
      "windowDays": 0,
      "severity": "LOW",
      "active": true
    }
  ],
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/pip/rules/{code}`

Update a PIP rule's threshold/window/severity/active flag

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `code` | string |  |

**Request body:**

```json
{
  "thresholdValue": 0.0,
  "severity": "LOW",
  "active": true
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "ruleCode": "ATTENDANCE_LOW",
    "description": "string",
    "thresholdValue": 0.0,
    "windowDays": 0,
    "severity": "LOW",
    "active": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/pip/{id}/milestones/{milestoneId}/complete`

Mark a PIP milestone complete

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |
| `milestoneId` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "description": "string",
    "dueDate": "2026-01-15",
    "status": "PENDING",
    "completedAt": "2026-01-15T10:30:00Z",
    "verifiedByUuid": "string"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/pip/{id}/review`

Day-15 review — CLEARED requires task completion >= 85% and no unsatisfactory reviews

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "outcome": "TRIGGERED",
  "reviewNotes": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "studentUuid": "string",
    "studentFullName": "string",
    "batchId": 0,
    "batchName": "string",
    "ruleCode": "ATTENDANCE_LOW",
    "triggerReason": "string",
    "severity": "LOW",
    "triggeredAt": "2026-01-15T10:30:00Z",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "TRIGGERED",
    "blocksTaskPull": true,
    "reviewedByUuid": "string",
    "reviewNotes": "string",
    "outcomeAt": "2026-01-15T10:30:00Z",
    "milestones": [
      {
        "id": 0,
        "title": "string",
        "description": "string",
        "dueDate": "2026-01-15",
        "status": "PENDING",
        "completedAt": "2026-01-15T10:30:00Z",
        "verifiedByUuid": "string"
      }
    ]
  },
  "error": null
}
```

**Status codes:** `200`

---

## Placements

### `GET` `/api/v1/placements`

List placements visible to the caller - a CLIENT sees the ones they requested, a STUDENT the ones where they are the candidate, HR_MANAGER / ADMIN see all. Optional stage filter.

**Auth:** Role: CLIENT or STUDENT or HR_MANAGER or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `stage` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "recruitmentRequestId": 0,
        "candidateUuid": "string",
        "candidateName": "string",
        "clientUuid": "string",
        "clientName": "string",
        "stage": "SHORTLISTED",
        "details": {},
        "createdAt": "2026-01-15T10:30:00Z",
        "updatedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/placements/{id}`

One placement

**Auth:** Role: CLIENT or STUDENT or HR_MANAGER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "recruitmentRequestId": 0,
    "candidateUuid": "string",
    "candidateName": "string",
    "clientUuid": "string",
    "clientName": "string",
    "stage": "SHORTLISTED",
    "details": {},
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/placements/{id}`

Advance a placement's stage (forward-only; REJECTED from any non-terminal) and merge stage fields into details. Per-stage ownership is enforced.

**Auth:** Role: CLIENT or STUDENT or HR_MANAGER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "stage": "SHORTLISTED",
  "details": {}
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "recruitmentRequestId": 0,
    "candidateUuid": "string",
    "candidateName": "string",
    "clientUuid": "string",
    "clientName": "string",
    "stage": "SHORTLISTED",
    "details": {},
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

## Plans

### `GET` `/api/v1/plans`

List active subscription plans

**Auth:** Public — no token required

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "code": "string",
      "name": "string",
      "priceInr": 0.0,
      "tierRank": 0,
      "durationDays": 0,
      "mentorSupport": true,
      "allowsBatch": true,
      "allowsSprints": true,
      "allowsPip": true,
      "allowsInternshipLetter": true,
      "allowsClientProject": true
    }
  ],
  "error": null
}
```

**Status codes:** `200`

---

## Projects

### `GET` `/api/v1/projects`

Browse projects — non-admin callers always see PUBLISHED only

**Auth:** Role: DEVELOPER or ADMIN or TRAINER_PM or STUDENT

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `difficulty` | string | no |  |
| `domain` | string | no |  |
| `status` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "title": "string",
        "slug": "string",
        "description": "string",
        "techStack": [
          "string"
        ],
        "difficulty": "BEGINNER",
        "domain": "string",
        "starterRepoUrl": "string",
        "version": "string",
        "status": "DRAFT",
        "createdByUuid": "string",
        "createdByFullName": "string",
        "assets": [
          {
            "id": 0,
            "assetType": "IMAGE",
            "title": "string",
            "url": "string",
            "sortOrder": 0
          }
        ],
        "challenges": [
          {
            "id": 0,
            "title": "string",
            "expectedBehaviour": "string",
            "brokenCodeUrl": "string",
            "testScriptUrl": "string",
            "difficulty": "BEGINNER"
          }
        ]
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/projects`

Author a new project (always created DRAFT)

**Auth:** Role: DEVELOPER or ADMIN

**Request body:**

```json
{
  "title": "string",
  "description": "string",
  "techStack": [
    "string"
  ],
  "difficulty": "BEGINNER",
  "domain": "string",
  "starterRepoUrl": "string",
  "version": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "slug": "string",
    "description": "string",
    "techStack": [
      "string"
    ],
    "difficulty": "BEGINNER",
    "domain": "string",
    "starterRepoUrl": "string",
    "version": "string",
    "status": "DRAFT",
    "createdByUuid": "string",
    "createdByFullName": "string",
    "assets": [
      {
        "id": 0,
        "assetType": "IMAGE",
        "title": "string",
        "url": "string",
        "sortOrder": 0
      }
    ],
    "challenges": [
      {
        "id": 0,
        "title": "string",
        "expectedBehaviour": "string",
        "brokenCodeUrl": "string",
        "testScriptUrl": "string",
        "difficulty": "BEGINNER"
      }
    ]
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/projects/{id}`

Edit a DRAFT project in place, or bump a PUBLISHED/ARCHIVED one into a new DRAFT version

**Auth:** Role: DEVELOPER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "title": "string",
  "description": "string",
  "techStack": [
    "string"
  ],
  "difficulty": "BEGINNER",
  "domain": "string",
  "starterRepoUrl": "string",
  "version": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "slug": "string",
    "description": "string",
    "techStack": [
      "string"
    ],
    "difficulty": "BEGINNER",
    "domain": "string",
    "starterRepoUrl": "string",
    "version": "string",
    "status": "DRAFT",
    "createdByUuid": "string",
    "createdByFullName": "string",
    "assets": [
      {
        "id": 0,
        "assetType": "IMAGE",
        "title": "string",
        "url": "string",
        "sortOrder": 0
      }
    ],
    "challenges": [
      {
        "id": 0,
        "title": "string",
        "expectedBehaviour": "string",
        "brokenCodeUrl": "string",
        "testScriptUrl": "string",
        "difficulty": "BEGINNER"
      }
    ]
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/projects/{id}/assets`

Attach an asset — exactly one of file or externalUrl

**Auth:** Role: DEVELOPER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `assetType` | string | yes |  |
| `title` | string | no |  |
| `externalUrl` | string | no |  |
| `sortOrder` | integer | no |  |

**Request body:**

```json
{
  "file": "<binary>"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "assetType": "IMAGE",
    "title": "string",
    "url": "string",
    "sortOrder": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/projects/{id}/publish`

DRAFT -> PUBLISHED — only a PUBLISHED project can attach to a task

**Auth:** Role: DEVELOPER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "slug": "string",
    "description": "string",
    "techStack": [
      "string"
    ],
    "difficulty": "BEGINNER",
    "domain": "string",
    "starterRepoUrl": "string",
    "version": "string",
    "status": "DRAFT",
    "createdByUuid": "string",
    "createdByFullName": "string",
    "assets": [
      {
        "id": 0,
        "assetType": "IMAGE",
        "title": "string",
        "url": "string",
        "sortOrder": 0
      }
    ],
    "challenges": [
      {
        "id": 0,
        "title": "string",
        "expectedBehaviour": "string",
        "brokenCodeUrl": "string",
        "testScriptUrl": "string",
        "difficulty": "BEGINNER"
      }
    ]
  },
  "error": null
}
```

**Status codes:** `200`

---

## Reviews

### `POST` `/api/v1/reviews`

Review a submission — score, verdict, comments

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "submissionId": 0,
  "score": 0,
  "verdict": "APPROVED",
  "comments": "string",
  "inlineComments": [
    {
      "filePath": "string",
      "line": 0,
      "comment": "string"
    }
  ]
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "submissionId": 0,
    "reviewerUuid": "string",
    "reviewerName": "string",
    "score": 0,
    "verdict": "APPROVED",
    "comments": "string",
    "inlineComments": [
      {
        "filePath": "string",
        "line": 0,
        "comment": "string"
      }
    ],
    "reviewedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/reviews/queue`

IN_REVIEW tasks awaiting the caller's review

**Auth:** Role: TRAINER_PM or ADMIN

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "sprintId": 0,
        "projectId": 0,
        "title": "string",
        "description": "string",
        "taskType": "DAILY",
        "assignedToUuid": "string",
        "assignedToName": "string",
        "storyPoints": 0,
        "dueAt": "2026-01-15T10:30:00Z",
        "status": "BACKLOG",
        "completedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/reviews/weekly`

Record a student's weekly qualitative rating

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "userUuid": "string",
  "batchId": 0,
  "sprintId": 0,
  "weekStart": "2026-01-15",
  "rating": "SATISFACTORY",
  "notes": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "studentUuid": "string",
    "studentName": "string",
    "batchId": 0,
    "sprintId": 0,
    "weekStart": "2026-01-15",
    "rating": "SATISFACTORY",
    "notes": "string",
    "reviewedByUuid": "string",
    "reviewedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

## Sprints

### `GET` `/api/v1/assignment-windows`

List assignment windows for a batch

**Auth:** Role: TRAINER_PM or ADMIN or STUDENT

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `batchId` | integer | yes |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "batchId": 0,
        "weekStart": "2026-01-15",
        "weekEnd": "2026-01-15",
        "dueAt": "2026-01-15T10:30:00Z",
        "taskId": 0
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/assignment-windows`

Create a weekly assignment window for a batch

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "batchId": 0,
  "weekStart": "2026-01-15",
  "weekEnd": "2026-01-15",
  "dueAt": "2026-01-15T10:30:00Z",
  "taskId": 0
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "weekStart": "2026-01-15",
    "weekEnd": "2026-01-15",
    "dueAt": "2026-01-15T10:30:00Z",
    "taskId": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/sprints`

List sprints for a batch

**Auth:** Role: TRAINER_PM or ADMIN or STUDENT

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `batchId` | integer | yes |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "batchId": 0,
        "sprintNumber": 0,
        "goal": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "status": "PLANNED",
        "plannedPoints": 0,
        "completedPoints": 0
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/sprints`

Create a sprint

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "batchId": 0,
  "sprintNumber": 0,
  "goal": "string",
  "startDate": "2026-01-15",
  "endDate": "2026-01-15",
  "plannedPoints": 0
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "sprintNumber": 0,
    "goal": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "PLANNED",
    "plannedPoints": 0,
    "completedPoints": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/sprints/{id}`

Update a sprint

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "goal": "string",
  "startDate": "2026-01-15",
  "endDate": "2026-01-15",
  "plannedPoints": 0,
  "status": "PLANNED"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "sprintNumber": 0,
    "goal": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "PLANNED",
    "plannedPoints": 0,
    "completedPoints": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/sprints/{id}/activate`

Activate a sprint (requires the previous sprint COMPLETED)

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "sprintNumber": 0,
    "goal": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "PLANNED",
    "plannedPoints": 0,
    "completedPoints": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

## Standups

### `GET` `/api/v1/standups`

List standups for a batch, optionally scoped to one day

**Auth:** Role: TRAINER_PM or ADMIN or STUDENT

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `batchId` | integer | yes |  |
| `date` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "batchId": 0,
        "sprintId": 0,
        "scheduledAt": "2026-01-15T10:30:00Z",
        "lateCutoffMinutes": 0,
        "notes": "string",
        "status": "SCHEDULED",
        "finalisedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/standups`

Schedule a standup

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "batchId": 0,
  "sprintId": 0,
  "scheduledAt": "2026-01-15T10:30:00Z",
  "lateCutoffMinutes": 0,
  "notes": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "sprintId": 0,
    "scheduledAt": "2026-01-15T10:30:00Z",
    "lateCutoffMinutes": 0,
    "notes": "string",
    "status": "SCHEDULED",
    "finalisedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/standups/{id}`

Edit or cancel a SCHEDULED standup

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "notes": "string",
  "lateCutoffMinutes": 0,
  "status": "SCHEDULED"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "batchId": 0,
    "sprintId": 0,
    "scheduledAt": "2026-01-15T10:30:00Z",
    "lateCutoffMinutes": 0,
    "notes": "string",
    "status": "SCHEDULED",
    "finalisedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

## Submissions

### `GET` `/api/v1/submissions`

List submissions for a task

**Auth:** Role: TRAINER_PM or ADMIN or STUDENT

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `taskId` | integer | yes |  |
| `status` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "taskId": 0,
        "studentUuid": "string",
        "studentName": "string",
        "attemptNumber": 0,
        "prUrl": "string",
        "repoOwner": "string",
        "repoName": "string",
        "prNumber": 0,
        "prState": "OPEN",
        "commitCount": 0,
        "latestCommitSha": "string",
        "videoUrl": "string",
        "notes": "string",
        "status": "SUBMITTED",
        "submittedAt": "2026-01-15T10:30:00Z",
        "verifiedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/submissions`

Submit a PR for a task

**Auth:** Role: STUDENT

**Request body:**

```json
{
  "taskId": 0,
  "prUrl": "string",
  "videoUrl": "string",
  "notes": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "taskId": 0,
    "studentUuid": "string",
    "studentName": "string",
    "attemptNumber": 0,
    "prUrl": "string",
    "repoOwner": "string",
    "repoName": "string",
    "prNumber": 0,
    "prState": "OPEN",
    "commitCount": 0,
    "latestCommitSha": "string",
    "videoUrl": "string",
    "notes": "string",
    "status": "SUBMITTED",
    "submittedAt": "2026-01-15T10:30:00Z",
    "verifiedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

## Subscriptions

### `GET` `/api/v1/subscriptions/me`

The caller's current active subscription

**Auth:** Authenticated (any logged-in user)

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "planCode": "string",
    "planName": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "status": "PENDING",
    "autoRenew": true
  },
  "error": null
}
```

**Status codes:** `200`

---

## Tasks

### `GET` `/api/v1/tasks`

List tasks for a sprint

**Auth:** Role: TRAINER_PM or ADMIN or STUDENT

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `sprintId` | integer | yes |  |
| `status` | string | no |  |
| `assignedTo` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "sprintId": 0,
        "projectId": 0,
        "title": "string",
        "description": "string",
        "taskType": "DAILY",
        "assignedToUuid": "string",
        "assignedToName": "string",
        "storyPoints": 0,
        "dueAt": "2026-01-15T10:30:00Z",
        "status": "BACKLOG",
        "completedAt": "2026-01-15T10:30:00Z"
      }
    ],
    "page": 0,
    "size": 0,
    "totalElements": 0,
    "totalPages": 0,
    "last": true
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/tasks`

Create a task

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "sprintId": 0,
  "projectId": 0,
  "title": "string",
  "description": "string",
  "taskType": "DAILY",
  "storyPoints": 0,
  "dueAt": "2026-01-15T10:30:00Z"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "sprintId": 0,
    "projectId": 0,
    "title": "string",
    "description": "string",
    "taskType": "DAILY",
    "assignedToUuid": "string",
    "assignedToName": "string",
    "storyPoints": 0,
    "dueAt": "2026-01-15T10:30:00Z",
    "status": "BACKLOG",
    "completedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/tasks/{id}`

Update a task, including driving its status forward

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "title": "string",
  "description": "string",
  "taskType": "DAILY",
  "storyPoints": 0,
  "dueAt": "2026-01-15T10:30:00Z",
  "status": "BACKLOG"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "sprintId": 0,
    "projectId": 0,
    "title": "string",
    "description": "string",
    "taskType": "DAILY",
    "assignedToUuid": "string",
    "assignedToName": "string",
    "storyPoints": 0,
    "dueAt": "2026-01-15T10:30:00Z",
    "status": "BACKLOG",
    "completedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/tasks/{id}/assign`

PM assigns a BACKLOG task to a specific student

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "userUuid": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "sprintId": 0,
    "projectId": 0,
    "title": "string",
    "description": "string",
    "taskType": "DAILY",
    "assignedToUuid": "string",
    "assignedToName": "string",
    "storyPoints": 0,
    "dueAt": "2026-01-15T10:30:00Z",
    "status": "BACKLOG",
    "completedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/tasks/{id}/pull`

Student self-assigns a BACKLOG task

**Auth:** Role: STUDENT

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "sprintId": 0,
    "projectId": 0,
    "title": "string",
    "description": "string",
    "taskType": "DAILY",
    "assignedToUuid": "string",
    "assignedToName": "string",
    "storyPoints": 0,
    "dueAt": "2026-01-15T10:30:00Z",
    "status": "BACKLOG",
    "completedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

## Users

### `GET` `/api/v1/portfolio/{slug}`

A student's public portfolio

**Auth:** Public — no token required

**Path parameters:**

| name | type | description |
|---|---|---|
| `slug` | string |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "fullName": "string",
    "currentTitle": "string",
    "bio": "string",
    "location": "string",
    "skills": [
      "string"
    ],
    "completedProjects": [
      {
        "title": "string",
        "slug": "string"
      }
    ],
    "issuedCertificates": [
      {
        "certificateType": "COMPLETION",
        "issuedAt": "2026-01-15T10:30:00Z",
        "verificationCode": "string"
      }
    ]
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/users/me`

The caller's own profile

**Auth:** Authenticated (any logged-in user)

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "phone": "string",
    "githubUsername": "string",
    "linkedinUrl": "string",
    "bio": "string",
    "location": "string",
    "currentTitle": "string",
    "experienceLevel": "string",
    "yearsExperience": 0,
    "skills": [
      "string"
    ],
    "education": [
      {
        "institution": "string",
        "degree": "string",
        "fieldOfStudy": "string",
        "startYear": 0,
        "endYear": 0
      }
    ],
    "workExperience": [
      {
        "company": "string",
        "title": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "description": "string"
      }
    ],
    "hasResume": true,
    "portfolioSlug": "string",
    "isComplete": true,
    "completionPercent": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/users/me/profile`

Update the caller's profile fields

**Auth:** Authenticated (any logged-in user)

**Request body:**

```json
{
  "githubUsername": "string",
  "bio": "string",
  "location": "string",
  "currentTitle": "string",
  "experienceLevel": "string",
  "yearsExperience": 0,
  "skills": [
    "string"
  ],
  "education": [
    {
      "institution": "string",
      "degree": "string",
      "fieldOfStudy": "string",
      "startYear": 0,
      "endYear": 0
    }
  ],
  "workExperience": [
    {
      "company": "string",
      "title": "string",
      "startDate": "2026-01-15",
      "endDate": "2026-01-15",
      "description": "string"
    }
  ]
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "phone": "string",
    "githubUsername": "string",
    "linkedinUrl": "string",
    "bio": "string",
    "location": "string",
    "currentTitle": "string",
    "experienceLevel": "string",
    "yearsExperience": 0,
    "skills": [
      "string"
    ],
    "education": [
      {
        "institution": "string",
        "degree": "string",
        "fieldOfStudy": "string",
        "startYear": 0,
        "endYear": 0
      }
    ],
    "workExperience": [
      {
        "company": "string",
        "title": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "description": "string"
      }
    ],
    "hasResume": true,
    "portfolioSlug": "string",
    "isComplete": true,
    "completionPercent": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/users/me/resume`

A presigned URL for downloading the caller's resume

**Auth:** Authenticated (any logged-in user)

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "downloadUrl": "string",
    "expiresAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `POST` `/api/v1/users/me/resume`

Upload (or replace) the caller's resume PDF

**Auth:** Authenticated (any logged-in user)

**Request body:**

```json
{
  "file": "<binary>"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "uuid": "string",
    "fullName": "string",
    "email": "string",
    "phone": "string",
    "githubUsername": "string",
    "linkedinUrl": "string",
    "bio": "string",
    "location": "string",
    "currentTitle": "string",
    "experienceLevel": "string",
    "yearsExperience": 0,
    "skills": [
      "string"
    ],
    "education": [
      {
        "institution": "string",
        "degree": "string",
        "fieldOfStudy": "string",
        "startYear": 0,
        "endYear": 0
      }
    ],
    "workExperience": [
      {
        "company": "string",
        "title": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "description": "string"
      }
    ],
    "hasResume": true,
    "portfolioSlug": "string",
    "isComplete": true,
    "completionPercent": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

## Webhooks

### `POST` `/api/v1/webhooks/razorpay`

Razorpay payment/refund webhook — signature-verified, not token-verified

**Auth:** Public — no token required

**Request body:**

```json
"string"
```

**Status codes:** `200`

---

### `POST` `/api/v1/webhooks/stripe`

Stripe checkout/refund webhook — signature-verified, not token-verified

**Auth:** Public — no token required

**Request body:**

```json
"string"
```

**Status codes:** `200`

---

### `POST` `/api/v1/webhooks/whatsapp`

WhatsApp Cloud API inbound message webhook — signature-verified, not token-verified

**Auth:** Public — no token required

**Request body:**

```json
"string"
```

**Status codes:** `200`

---
