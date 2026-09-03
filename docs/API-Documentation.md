# Moriah Skill Hub — API Documentation

> Generated from `docs/openapi.json` (OpenAPI 3.1.0). 202 endpoints across 32 groups. Auth/role column is read from each controller's `@PreAuthorize`.

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

- [Admin](#admin) — 25 endpoints
- [Assessments](#assessments) — 12 endpoints
- [Attendance](#attendance) — 4 endpoints
- [Auth](#auth) — 14 endpoints
- [BA](#ba) — 8 endpoints
- [Batches](#batches) — 8 endpoints
- [Bug Challenges](#bug-challenges) — 5 endpoints
- [CRM](#crm) — 15 endpoints
- [Certificates](#certificates) — 4 endpoints
- [Checkout](#checkout) — 1 endpoints
- [Clients](#clients) — 3 endpoints
- [Dev](#dev) — 3 endpoints
- [HR](#hr) — 24 endpoints
- [Interviews](#interviews) — 5 endpoints
- [Lessons](#lessons) — 12 endpoints
- [Notifications](#notifications) — 4 endpoints
- [PIP](#pip) — 6 endpoints
- [Placements](#placements) — 3 endpoints
- [Plans](#plans) — 1 endpoints
- [Projects](#projects) — 5 endpoints
- [Public](#public) — 1 endpoints
- [Resources](#resources) — 5 endpoints
- [Reviews](#reviews) — 3 endpoints
- [Sprints](#sprints) — 6 endpoints
- [Standups](#standups) — 3 endpoints
- [Submissions](#submissions) — 2 endpoints
- [Subscriptions](#subscriptions) — 2 endpoints
- [Talent](#talent) — 4 endpoints
- [Tasks](#tasks) — 5 endpoints
- [Users](#users) — 5 endpoints
- [Webhooks](#webhooks) — 3 endpoints
- [o-auth-2-default-callback-fallback-controller](#o-auth-2-default-callback-fallback-controller) — 1 endpoints

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

### `GET` `/api/v1/admin/coupons`

List all coupons, newest first

**Auth:** Role: ADMIN

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "code": "string",
        "discountType": "PERCENTAGE",
        "discountValue": 0.0,
        "validFrom": "2026-01-15",
        "validUntil": "2026-01-15",
        "maxRedemptions": 0,
        "timesRedeemed": 0,
        "active": true,
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

### `POST` `/api/v1/admin/coupons`

Create a coupon

**Auth:** Role: ADMIN

**Request body:**

```json
{
  "code": "string",
  "discountType": "PERCENTAGE",
  "discountValue": 0.0,
  "validFrom": "2026-01-15",
  "validUntil": "2026-01-15",
  "maxRedemptions": 0,
  "active": true
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "code": "string",
    "discountType": "PERCENTAGE",
    "discountValue": 0.0,
    "validFrom": "2026-01-15",
    "validUntil": "2026-01-15",
    "maxRedemptions": 0,
    "timesRedeemed": 0,
    "active": true,
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `403`, `409`

---

### `PUT` `/api/v1/admin/coupons/{code}`

Replace a coupon's discount, validity window, cap and active flag

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `code` | string |  |

**Request body:**

```json
{
  "discountType": "PERCENTAGE",
  "discountValue": 0.0,
  "validFrom": "2026-01-15",
  "validUntil": "2026-01-15",
  "maxRedemptions": 0,
  "active": true
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "code": "string",
    "discountType": "PERCENTAGE",
    "discountValue": 0.0,
    "validFrom": "2026-01-15",
    "validUntil": "2026-01-15",
    "maxRedemptions": 0,
    "timesRedeemed": 0,
    "active": true,
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `DELETE` `/api/v1/admin/coupons/{code}`

Deactivate a coupon (active = false) — never row-deletes, so redemption history is kept

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `code` | string |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

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

### `GET` `/api/v1/admin/payments`

List payments, newest first — optional status / gateway / userUuid filters

**Auth:** Role: ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |
| `gateway` | string | no |  |
| `userUuid` | string | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "gatewayOrderId": "string",
        "gatewayPaymentId": "string",
        "userUuid": "string",
        "userFullName": "string",
        "planId": 0,
        "trackCode": "string",
        "gateway": "RAZORPAY",
        "amount": 0.0,
        "currency": "string",
        "status": "CREATED",
        "failureReason": "string",
        "capturedAt": "2026-01-15T10:30:00Z",
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

### `GET` `/api/v1/admin/payments/summary`

Payment counts and amount totals per status, plus total captured / refunded

**Auth:** Role: ADMIN

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "byStatus": [
      {
        "status": "CREATED",
        "count": 0,
        "totalAmount": 0.0
      }
    ],
    "totalCount": 0,
    "totalCaptured": 0.0,
    "totalRefunded": 0.0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/admin/payments/{gatewayOrderId}`

One payment's detail

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `gatewayOrderId` | string |  |

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "gatewayOrderId": "string",
    "gatewayPaymentId": "string",
    "userUuid": "string",
    "userFullName": "string",
    "planId": 0,
    "trackCode": "string",
    "gateway": "RAZORPAY",
    "amount": 0.0,
    "currency": "string",
    "status": "CREATED",
    "failureReason": "string",
    "capturedAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `POST` `/api/v1/admin/payments/{gatewayOrderId}/refund`

Issue a full refund through the original gateway — only for a CAPTURED payment, once. The gateway's refund webhook reconciles the final state.

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `gatewayOrderId` | string |  |

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
    "gatewayOrderId": "string",
    "gatewayPaymentId": "string",
    "userUuid": "string",
    "userFullName": "string",
    "planId": 0,
    "trackCode": "string",
    "gateway": "RAZORPAY",
    "amount": 0.0,
    "currency": "string",
    "status": "CREATED",
    "failureReason": "string",
    "capturedAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`, `409`, `502`

---

### `POST` `/api/v1/admin/plans`

Create a subscription plan — cache evicted immediately

**Auth:** Role: ADMIN

**Request body:**

```json
{
  "code": "string",
  "name": "string",
  "priceInr": 0.0,
  "tierRank": 0,
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

**Response `201`:**

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

**Status codes:** `201`, `403`, `409`

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

### `DELETE` `/api/v1/admin/plans/{id}`

Deactivate a subscription plan (is_active = false) — never row-deletes, so subscription history is kept

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
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

### `GET` `/api/v1/admin/users/{userUuid}`

Single-user detail — profile fields, roles, 2FA and login timestamps

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
    "githubUsername": "string",
    "linkedinUrl": "string",
    "status": "ACTIVE",
    "roles": [
      "STUDENT"
    ],
    "twoFactorEnabled": true,
    "emailVerifiedAt": "2026-01-15T10:30:00Z",
    "lastLoginAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `PUT` `/api/v1/admin/users/{userUuid}`

Edit a user's profile fields (name, phone, GitHub, LinkedIn). Status and roles have their own endpoints.

**Auth:** Role: ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `userUuid` | string |  |

**Request body:**

```json
{
  "fullName": "string",
  "phone": "string",
  "githubUsername": "string",
  "linkedinUrl": "string"
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
    "status": "ACTIVE",
    "roles": [
      "STUDENT"
    ],
    "twoFactorEnabled": true,
    "emailVerifiedAt": "2026-01-15T10:30:00Z",
    "lastLoginAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`, `409`

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

### `GET` `/api/v1/assessments/banks`

List question banks — optional topic / active filters

**Auth:** Role: TRAINER_PM or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `topic` | string | no |  |
| `active` | boolean | no |  |

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
        "topic": "string",
        "description": "string",
        "active": true,
        "questionCount": 0,
        "createdByUuid": "string",
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

### `POST` `/api/v1/assessments/banks`

Create a question bank

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "name": "string",
  "topic": "string",
  "description": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "topic": "string",
    "description": "string",
    "active": true,
    "questionCount": 0,
    "createdByUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/assessments/banks/{id}`

Rename / re-topic a bank or toggle its active flag

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "name": "string",
  "topic": "string",
  "description": "string",
  "active": true
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "topic": "string",
    "description": "string",
    "active": true,
    "questionCount": 0,
    "createdByUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `DELETE` `/api/v1/assessments/banks/{id}`

Deactivate a bank (is_active = false) — never row-deletes

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `GET` `/api/v1/assessments/banks/{id}/questions`

List the questions in a bank (correct-answer keys are never returned)

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "bankId": 0,
        "questionText": "string",
        "questionType": "MCQ",
        "options": [
          "string"
        ],
        "marks": 0,
        "explanation": "string",
        "difficulty": "EASY",
        "createdByUuid": "string",
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

**Status codes:** `200`, `404`

---

### `POST` `/api/v1/assessments/banks/{id}/questions`

Add a question to a bank

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
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
  "explanation": "string",
  "difficulty": "EASY"
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "bankId": 0,
    "questionText": "string",
    "questionType": "MCQ",
    "options": [
      "string"
    ],
    "marks": 0,
    "explanation": "string",
    "difficulty": "EASY",
    "createdByUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `400`, `404`

---

### `DELETE` `/api/v1/assessments/banks/{id}/questions/{questionId}`

Remove a question from a bank

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |
| `questionId` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`, `404`

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

### `POST` `/api/v1/auth/resend-verification`

Re-send the email-verification link — always responds the same way regardless of whether the address maps to an account still awaiting verification

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

### `GET` `/api/v1/ba/documents`

List requirement documents — optional clientProjectId / status filters

**Auth:** Role: BUSINESS_ANALYST or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `clientProjectId` | integer | no |  |
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
        "clientProjectId": 0,
        "docType": "BRD",
        "title": "string",
        "version": 0,
        "status": "DRAFT",
        "authoredByUuid": "string",
        "authoredByFullName": "string",
        "approvedByUuid": "string",
        "approvedByFullName": "string",
        "devReviewedByUuid": "string",
        "devReviewedByFullName": "string",
        "devReviewedAt": "2026-01-15T10:30:00Z"
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
    "approvedByFullName": "string",
    "devReviewedByUuid": "string",
    "devReviewedByFullName": "string",
    "devReviewedAt": "2026-01-15T10:30:00Z"
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
    "approvedByFullName": "string",
    "devReviewedByUuid": "string",
    "devReviewedByFullName": "string",
    "devReviewedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/ba/meetings`

List meetings — optional status / clientProjectId filters

**Auth:** Role: BUSINESS_ANALYST or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |
| `clientProjectId` | integer | no |  |

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
        "agenda": "string",
        "clientProjectId": 0,
        "scheduledAt": "2026-01-15T10:30:00Z",
        "durationMinutes": 0,
        "location": "string",
        "status": "SCHEDULED",
        "minutes": "string",
        "createdByUuid": "string",
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

### `POST` `/api/v1/ba/meetings`

Schedule a meeting (starts SCHEDULED)

**Auth:** Role: BUSINESS_ANALYST or ADMIN

**Request body:**

```json
{
  "title": "string",
  "agenda": "string",
  "clientProjectId": 0,
  "scheduledAt": "2026-01-15T10:30:00Z",
  "durationMinutes": 0,
  "location": "string"
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "agenda": "string",
    "clientProjectId": 0,
    "scheduledAt": "2026-01-15T10:30:00Z",
    "durationMinutes": 0,
    "location": "string",
    "status": "SCHEDULED",
    "minutes": "string",
    "createdByUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `404`

---

### `PUT` `/api/v1/ba/meetings/{id}`

Replace a meeting's fields — including status and minutes

**Auth:** Role: BUSINESS_ANALYST or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "title": "string",
  "agenda": "string",
  "clientProjectId": 0,
  "scheduledAt": "2026-01-15T10:30:00Z",
  "durationMinutes": 0,
  "location": "string",
  "status": "SCHEDULED",
  "minutes": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "agenda": "string",
    "clientProjectId": 0,
    "scheduledAt": "2026-01-15T10:30:00Z",
    "durationMinutes": 0,
    "location": "string",
    "status": "SCHEDULED",
    "minutes": "string",
    "createdByUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `DELETE` `/api/v1/ba/meetings/{id}`

Cancel a meeting (status = CANCELLED) — never row-deletes

**Auth:** Role: BUSINESS_ANALYST or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`, `404`

---

## Batches

### `GET` `/api/v1/batches`

List batches — a STUDENT sees only the batches they are enrolled in

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

Get one batch — a STUDENT may only read a batch they are enrolled in

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

The batch roster — enrolled students with their uuid + status (a TRAINER_PM must own the batch). Feeds task assignment, graduation and letters.

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

### `GET` `/api/v1/challenges/{challengeId}`

One bug-fix challenge by id

**Auth:** Role: DEVELOPER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `challengeId` | integer |  |

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

**Status codes:** `200`, `404`

---

### `PUT` `/api/v1/challenges/{challengeId}`

Edit a challenge's title / expected behaviour / difficulty — creator or ADMIN only

**Auth:** Role: DEVELOPER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `challengeId` | integer |  |

**Request body:**

```json
{
  "title": "string",
  "expectedBehaviour": "string",
  "difficulty": "BEGINNER"
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

**Status codes:** `200`, `403`, `404`, `409`

---

### `DELETE` `/api/v1/challenges/{challengeId}`

Delete a bug-fix challenge — creator or ADMIN only

**Auth:** Role: DEVELOPER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `challengeId` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `GET` `/api/v1/projects/{id}/challenges`

List every bug-fix challenge on a project

**Auth:** Role: DEVELOPER or ADMIN

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
      "id": 0,
      "title": "string",
      "expectedBehaviour": "string",
      "brokenCodeUrl": "string",
      "testScriptUrl": "string",
      "difficulty": "BEGINNER"
    }
  ],
  "error": null
}
```

**Status codes:** `200`, `404`

---

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
        "dealValue": 0.0,
        "status": "NEW",
        "assignedAgentUuid": "string",
        "assignedAgentName": "string",
        "lostReason": "string",
        "convertedUserUuid": "string",
        "nextFollowUpAt": "2026-01-15T10:30:00Z",
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
  "interestedPlanId": 0,
  "dealValue": 0.0
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
    "dealValue": 0.0,
    "status": "NEW",
    "assignedAgentUuid": "string",
    "assignedAgentName": "string",
    "lostReason": "string",
    "convertedUserUuid": "string",
    "nextFollowUpAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/leads/campaigns`

List campaigns — optional status filter

**Auth:** Role: LEAD_GEN or ADMIN

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
        "id": 0,
        "name": "string",
        "channel": "EMAIL",
        "description": "string",
        "startDate": "2026-01-15",
        "endDate": "2026-01-15",
        "budget": 0.0,
        "targetLeads": 0,
        "status": "PLANNED",
        "createdByUuid": "string",
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

### `POST` `/api/v1/leads/campaigns`

Create a campaign (starts PLANNED)

**Auth:** Role: LEAD_GEN or ADMIN

**Request body:**

```json
{
  "name": "string",
  "channel": "EMAIL",
  "description": "string",
  "startDate": "2026-01-15",
  "endDate": "2026-01-15",
  "budget": 0.0,
  "targetLeads": 0
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "name": "string",
    "channel": "EMAIL",
    "description": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "budget": 0.0,
    "targetLeads": 0,
    "status": "PLANNED",
    "createdByUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/leads/campaigns/{id}`

Replace a campaign's fields, including its status

**Auth:** Role: LEAD_GEN or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "name": "string",
  "channel": "EMAIL",
  "description": "string",
  "startDate": "2026-01-15",
  "endDate": "2026-01-15",
  "budget": 0.0,
  "targetLeads": 0,
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
    "channel": "EMAIL",
    "description": "string",
    "startDate": "2026-01-15",
    "endDate": "2026-01-15",
    "budget": 0.0,
    "targetLeads": 0,
    "status": "PLANNED",
    "createdByUuid": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `DELETE` `/api/v1/leads/campaigns/{id}`

Cancel a campaign (status = CANCELLED) — never row-deletes

**Auth:** Role: LEAD_GEN or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `POST` `/api/v1/leads/inbound`

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Request body:**

```json
{
  "name": "string",
  "email": "user@example.com",
  "phone": "string",
  "message": "string",
  "leadType": "string",
  "source": "LANDING_PAGE"
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
    "dealValue": 0.0,
    "status": "NEW",
    "assignedAgentUuid": "string",
    "assignedAgentName": "string",
    "lostReason": "string",
    "convertedUserUuid": "string",
    "nextFollowUpAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/leads/targets/leaderboard`

**Auth:** Role: LEAD_GEN or ADMIN

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "agentUuid": "string",
      "agentName": "string",
      "totalLeads": 0,
      "converted": 0,
      "pipelineValue": 0.0,
      "callsTarget": 0,
      "callsMade": 0,
      "conversionsTarget": 0,
      "conversionsMade": 0,
      "revenueTarget": 0.0,
      "revenueAchieved": 0.0
    }
  ],
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

### `GET` `/api/v1/leads/{id}`

**Auth:** Role: LEAD_GEN or ADMIN

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
    "email": "string",
    "phone": "string",
    "source": "LANDING_PAGE",
    "leadType": "string",
    "institution": "string",
    "interestedPlanId": 0,
    "dealValue": 0.0,
    "status": "NEW",
    "assignedAgentUuid": "string",
    "assignedAgentName": "string",
    "lostReason": "string",
    "convertedUserUuid": "string",
    "nextFollowUpAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/leads/{id}`

**Auth:** Role: LEAD_GEN or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "name": "string",
  "leadType": "string",
  "institution": "string",
  "interestedPlanId": 0,
  "dealValue": 0.0
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
    "dealValue": 0.0,
    "status": "NEW",
    "assignedAgentUuid": "string",
    "assignedAgentName": "string",
    "lostReason": "string",
    "convertedUserUuid": "string",
    "nextFollowUpAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `DELETE` `/api/v1/leads/{id}`

**Auth:** Role: LEAD_GEN or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

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

### `GET` `/api/v1/leads/{id}/activities`

**Auth:** Role: LEAD_GEN or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "leadId": 0,
        "agentUuid": "string",
        "agentName": "string",
        "activityType": "CALL",
        "outcome": "string",
        "notes": "string",
        "nextFollowUpAt": "2026-01-15T10:30:00Z",
        "occurredAt": "2026-01-15T10:30:00Z"
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
  "convertedUserUuid": "string",
  "convertedUserEmail": "string"
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
    "dealValue": 0.0,
    "status": "NEW",
    "assignedAgentUuid": "string",
    "assignedAgentName": "string",
    "lostReason": "string",
    "convertedUserUuid": "string",
    "nextFollowUpAt": "2026-01-15T10:30:00Z",
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

## Dev

### `GET` `/api/v1/dev/requirement-documents`

List requirement documents to review — optional clientProjectId / status filters

**Auth:** Role: DEVELOPER or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `clientProjectId` | integer | no |  |
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
        "clientProjectId": 0,
        "docType": "BRD",
        "title": "string",
        "version": 0,
        "status": "DRAFT",
        "authoredByUuid": "string",
        "authoredByFullName": "string",
        "approvedByUuid": "string",
        "approvedByFullName": "string",
        "devReviewedByUuid": "string",
        "devReviewedByFullName": "string",
        "devReviewedAt": "2026-01-15T10:30:00Z"
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

### `GET` `/api/v1/dev/requirement-documents/{id}`

One requirement document with its full content

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
    "clientProjectId": 0,
    "docType": "BRD",
    "title": "string",
    "version": 0,
    "status": "DRAFT",
    "content": "string",
    "authoredByUuid": "string",
    "authoredByFullName": "string",
    "approvedByUuid": "string",
    "approvedByFullName": "string",
    "devReviewedByUuid": "string",
    "devReviewedByFullName": "string",
    "devReviewedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `POST` `/api/v1/dev/requirement-documents/{id}/acknowledge`

Mark a requirement document as reviewed by the caller — idempotent; does not change status

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
    "clientProjectId": 0,
    "docType": "BRD",
    "title": "string",
    "version": 0,
    "status": "DRAFT",
    "content": "string",
    "authoredByUuid": "string",
    "authoredByFullName": "string",
    "approvedByUuid": "string",
    "approvedByFullName": "string",
    "devReviewedByUuid": "string",
    "devReviewedByFullName": "string",
    "devReviewedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

---

## HR

### `GET` `/api/v1/hr/disciplinary`

List disciplinary actions — optional status / severity / employeeId filters

**Auth:** Role: HR_MANAGER or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |
| `severity` | string | no |  |
| `employeeId` | integer | no |  |

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
        "employeeName": "string",
        "actionType": "VERBAL_WARNING",
        "severity": "LOW",
        "incidentDate": "2026-01-15",
        "description": "string",
        "actionTaken": "string",
        "status": "OPEN",
        "acknowledgedAt": "2026-01-15T10:30:00Z",
        "resolvedAt": "2026-01-15T10:30:00Z",
        "resolutionNotes": "string",
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

### `POST` `/api/v1/hr/disciplinary`

Raise a disciplinary action against an employee (starts OPEN)

**Auth:** Role: HR_MANAGER or ADMIN

**Request body:**

```json
{
  "employeeId": 0,
  "actionType": "VERBAL_WARNING",
  "severity": "LOW",
  "incidentDate": "2026-01-15",
  "description": "string"
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "employeeId": 0,
    "employeeCode": "string",
    "employeeName": "string",
    "actionType": "VERBAL_WARNING",
    "severity": "LOW",
    "incidentDate": "2026-01-15",
    "description": "string",
    "actionTaken": "string",
    "status": "OPEN",
    "acknowledgedAt": "2026-01-15T10:30:00Z",
    "resolvedAt": "2026-01-15T10:30:00Z",
    "resolutionNotes": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `404`

---

### `GET` `/api/v1/hr/disciplinary/{id}`

One disciplinary action by id

**Auth:** Role: HR_MANAGER or ADMIN

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
    "employeeId": 0,
    "employeeCode": "string",
    "employeeName": "string",
    "actionType": "VERBAL_WARNING",
    "severity": "LOW",
    "incidentDate": "2026-01-15",
    "description": "string",
    "actionTaken": "string",
    "status": "OPEN",
    "acknowledgedAt": "2026-01-15T10:30:00Z",
    "resolvedAt": "2026-01-15T10:30:00Z",
    "resolutionNotes": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/hr/disciplinary/{id}`

Update an action — status (ACKNOWLEDGED/RESOLVED stamp their timestamps), action taken, resolution notes

**Auth:** Role: HR_MANAGER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "actionType": "VERBAL_WARNING",
  "severity": "LOW",
  "incidentDate": "2026-01-15",
  "description": "string",
  "actionTaken": "string",
  "status": "OPEN",
  "resolutionNotes": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "employeeId": 0,
    "employeeCode": "string",
    "employeeName": "string",
    "actionType": "VERBAL_WARNING",
    "severity": "LOW",
    "incidentDate": "2026-01-15",
    "description": "string",
    "actionTaken": "string",
    "status": "OPEN",
    "acknowledgedAt": "2026-01-15T10:30:00Z",
    "resolvedAt": "2026-01-15T10:30:00Z",
    "resolutionNotes": "string",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `GET` `/api/v1/hr/documents`

List HR documents — an employee sees only their own; HR_MANAGER / ADMIN see all, optionally filtered by status / userUuid / documentType. Each row carries a short-lived presigned downloadUrl.

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
        "userFullName": "string",
        "documentType": "string",
        "verificationStatus": "PENDING",
        "verifiedByUuid": "string",
        "verifiedAt": "2026-01-15T10:30:00Z",
        "rejectionReason": "string",
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
    "userFullName": "string",
    "documentType": "string",
    "verificationStatus": "PENDING",
    "verifiedByUuid": "string",
    "verifiedAt": "2026-01-15T10:30:00Z",
    "rejectionReason": "string",
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
    "userFullName": "string",
    "documentType": "string",
    "verificationStatus": "PENDING",
    "verifiedByUuid": "string",
    "verifiedAt": "2026-01-15T10:30:00Z",
    "rejectionReason": "string",
    "downloadUrl": "string",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/hr/employees`

List employees — optional status / department / search (name or code) filters

**Auth:** Role: HR_MANAGER or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |
| `department` | string | no |  |
| `search` | string | no |  |

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

### `POST` `/api/v1/hr/employees`

Create an employee record

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

### `GET` `/api/v1/hr/employees/{id}`

One employee record by id

**Auth:** Role: HR_MANAGER or ADMIN

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

### `GET` `/api/v1/hr/exits`

List exit records — optional status / employeeId filters

**Auth:** Role: HR_MANAGER or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |
| `employeeId` | integer | no |  |

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
        "employeeName": "string",
        "exitType": "RESIGNATION",
        "lastWorkingDay": "2026-01-15",
        "reason": "string",
        "noticePeriodDays": 0,
        "status": "INITIATED",
        "clearanceChecklist": [
          {
            "label": "string",
            "done": true
          }
        ],
        "exitInterviewNotes": "string",
        "completedAt": "2026-01-15T10:30:00Z",
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

### `POST` `/api/v1/hr/exits`

Initiate an employee's offboarding (starts INITIATED)

**Auth:** Role: HR_MANAGER or ADMIN

**Request body:**

```json
{
  "employeeId": 0,
  "exitType": "RESIGNATION",
  "lastWorkingDay": "2026-01-15",
  "reason": "string",
  "noticePeriodDays": 0
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "employeeId": 0,
    "employeeCode": "string",
    "employeeName": "string",
    "exitType": "RESIGNATION",
    "lastWorkingDay": "2026-01-15",
    "reason": "string",
    "noticePeriodDays": 0,
    "status": "INITIATED",
    "clearanceChecklist": [
      {
        "label": "string",
        "done": true
      }
    ],
    "exitInterviewNotes": "string",
    "completedAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `404`, `409`

---

### `PUT` `/api/v1/hr/exits/{id}`

Update an in-progress exit — checklist, notes, working status. Not for COMPLETED.

**Auth:** Role: HR_MANAGER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "exitType": "RESIGNATION",
  "lastWorkingDay": "2026-01-15",
  "reason": "string",
  "noticePeriodDays": 0,
  "status": "INITIATED",
  "clearanceChecklist": [
    {
      "label": "string",
      "done": true
    }
  ],
  "exitInterviewNotes": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "employeeId": 0,
    "employeeCode": "string",
    "employeeName": "string",
    "exitType": "RESIGNATION",
    "lastWorkingDay": "2026-01-15",
    "reason": "string",
    "noticePeriodDays": 0,
    "status": "INITIATED",
    "clearanceChecklist": [
      {
        "label": "string",
        "done": true
      }
    ],
    "exitInterviewNotes": "string",
    "completedAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `400`, `404`, `409`

---

### `POST` `/api/v1/hr/exits/{id}/complete`

Finalise an exit — sets the employee to EXITED (or TERMINATED) and stamps date_of_exit

**Auth:** Role: HR_MANAGER or ADMIN

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
    "employeeId": 0,
    "employeeCode": "string",
    "employeeName": "string",
    "exitType": "RESIGNATION",
    "lastWorkingDay": "2026-01-15",
    "reason": "string",
    "noticePeriodDays": 0,
    "status": "INITIATED",
    "clearanceChecklist": [
      {
        "label": "string",
        "done": true
      }
    ],
    "exitInterviewNotes": "string",
    "completedAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`, `409`

---

### `GET` `/api/v1/hr/leaves`

List leave requests — an employee sees only their own; HR_MANAGER / ADMIN see all, optionally filtered by status and userUuid

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
        "userFullName": "string",
        "leaveType": "SICK",
        "fromDate": "2026-01-15",
        "toDate": "2026-01-15",
        "days": 0.0,
        "reason": "string",
        "status": "PENDING",
        "approvedByUuid": "string",
        "decidedAt": "2026-01-15T10:30:00Z",
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
    "userFullName": "string",
    "leaveType": "SICK",
    "fromDate": "2026-01-15",
    "toDate": "2026-01-15",
    "days": 0.0,
    "reason": "string",
    "status": "PENDING",
    "approvedByUuid": "string",
    "decidedAt": "2026-01-15T10:30:00Z",
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
    "userFullName": "string",
    "leaveType": "SICK",
    "fromDate": "2026-01-15",
    "toDate": "2026-01-15",
    "days": 0.0,
    "reason": "string",
    "status": "PENDING",
    "approvedByUuid": "string",
    "decidedAt": "2026-01-15T10:30:00Z",
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

### `GET` `/api/v1/hr/onboardings`

List onboarding records — optional status / employeeId filters

**Auth:** Role: HR_MANAGER or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |
| `employeeId` | integer | no |  |

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
        "employeeName": "string",
        "buddyId": 0,
        "startDate": "2026-01-15",
        "status": "NOT_STARTED",
        "checklist": [
          {
            "label": "string",
            "done": true
          }
        ],
        "notes": "string",
        "completedAt": "2026-01-15T10:30:00Z",
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

### `POST` `/api/v1/hr/onboardings`

Start an employee's onboarding (starts NOT_STARTED)

**Auth:** Role: HR_MANAGER or ADMIN

**Request body:**

```json
{
  "employeeId": 0,
  "startDate": "2026-01-15",
  "buddyUuid": "string"
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "employeeId": 0,
    "employeeCode": "string",
    "employeeName": "string",
    "buddyId": 0,
    "startDate": "2026-01-15",
    "status": "NOT_STARTED",
    "checklist": [
      {
        "label": "string",
        "done": true
      }
    ],
    "notes": "string",
    "completedAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `404`, `409`

---

### `GET` `/api/v1/hr/onboardings/{id}`

One onboarding record by id

**Auth:** Role: HR_MANAGER or ADMIN

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
    "employeeId": 0,
    "employeeCode": "string",
    "employeeName": "string",
    "buddyId": 0,
    "startDate": "2026-01-15",
    "status": "NOT_STARTED",
    "checklist": [
      {
        "label": "string",
        "done": true
      }
    ],
    "notes": "string",
    "completedAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/hr/onboardings/{id}`

Update an onboarding — checklist, buddy, notes, status (COMPLETED stamps completedAt)

**Auth:** Role: HR_MANAGER or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "startDate": "2026-01-15",
  "buddyUuid": "string",
  "status": "NOT_STARTED",
  "checklist": [
    {
      "label": "string",
      "done": true
    }
  ],
  "notes": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "employeeId": 0,
    "employeeCode": "string",
    "employeeName": "string",
    "buddyId": 0,
    "startDate": "2026-01-15",
    "status": "NOT_STARTED",
    "checklist": [
      {
        "label": "string",
        "done": true
      }
    ],
    "notes": "string",
    "completedAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

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

## Interviews

### `GET` `/api/v1/interviews`

List interviews — optional status / type / studentUuid filters

**Auth:** Role: TRAINER_PM or ADMIN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `status` | string | no |  |
| `type` | string | no |  |
| `studentUuid` | string | no |  |

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
        "studentName": "string",
        "scheduledByUuid": "string",
        "interviewType": "MOCK",
        "scheduledAt": "2026-01-15T10:30:00Z",
        "durationMinutes": 0,
        "mode": "ONLINE",
        "location": "string",
        "interviewerName": "string",
        "meetingLink": "string",
        "status": "SCHEDULED",
        "feedback": "string",
        "rating": 0,
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

### `POST` `/api/v1/interviews`

Schedule an interview for a student (starts SCHEDULED)

**Auth:** Role: TRAINER_PM or ADMIN

**Request body:**

```json
{
  "studentUuid": "string",
  "interviewType": "MOCK",
  "scheduledAt": "2026-01-15T10:30:00Z",
  "durationMinutes": 0,
  "mode": "ONLINE",
  "location": "string",
  "interviewerName": "string",
  "meetingLink": "string"
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "studentUuid": "string",
    "studentName": "string",
    "scheduledByUuid": "string",
    "interviewType": "MOCK",
    "scheduledAt": "2026-01-15T10:30:00Z",
    "durationMinutes": 0,
    "mode": "ONLINE",
    "location": "string",
    "interviewerName": "string",
    "meetingLink": "string",
    "status": "SCHEDULED",
    "feedback": "string",
    "rating": 0,
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `403`, `404`

---

### `GET` `/api/v1/interviews/me`

The caller student's own interviews

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
        "studentUuid": "string",
        "studentName": "string",
        "scheduledByUuid": "string",
        "interviewType": "MOCK",
        "scheduledAt": "2026-01-15T10:30:00Z",
        "durationMinutes": 0,
        "mode": "ONLINE",
        "location": "string",
        "interviewerName": "string",
        "meetingLink": "string",
        "status": "SCHEDULED",
        "feedback": "string",
        "rating": 0,
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

### `PUT` `/api/v1/interviews/{id}`

Reschedule / update an interview, including status, feedback and rating

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "interviewType": "MOCK",
  "scheduledAt": "2026-01-15T10:30:00Z",
  "durationMinutes": 0,
  "mode": "ONLINE",
  "location": "string",
  "interviewerName": "string",
  "meetingLink": "string",
  "status": "SCHEDULED",
  "feedback": "string",
  "rating": 0
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
    "scheduledByUuid": "string",
    "interviewType": "MOCK",
    "scheduledAt": "2026-01-15T10:30:00Z",
    "durationMinutes": 0,
    "mode": "ONLINE",
    "location": "string",
    "interviewerName": "string",
    "meetingLink": "string",
    "status": "SCHEDULED",
    "feedback": "string",
    "rating": 0,
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `DELETE` `/api/v1/interviews/{id}`

Cancel an interview (status = CANCELLED) — never row-deletes

**Auth:** Role: TRAINER_PM or ADMIN

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

## Lessons

### `GET` `/api/v1/lessons`

Browse lessons — optional module filter. Each row carries the caller's progress. includeUnpublished is honoured only for curator roles.

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `module` | string | no |  |
| `includeUnpublished` | boolean | no |  |

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
        "description": "string",
        "moduleName": "string",
        "videoUrl": "string",
        "durationSeconds": 0,
        "sortOrder": 0,
        "published": true,
        "createdByUuid": "string",
        "progress": {
          "status": "string",
          "watchedSeconds": 0,
          "completedAt": "2026-01-15T10:30:00Z"
        },
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

### `POST` `/api/v1/lessons`

Create a lesson (published defaults to false)

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Request body:**

```json
{
  "title": "string",
  "description": "string",
  "moduleName": "string",
  "videoUrl": "string",
  "durationSeconds": 0,
  "sortOrder": 0,
  "published": true
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "description": "string",
    "moduleName": "string",
    "videoUrl": "string",
    "durationSeconds": 0,
    "sortOrder": 0,
    "published": true,
    "createdByUuid": "string",
    "progress": {
      "status": "string",
      "watchedSeconds": 0,
      "completedAt": "2026-01-15T10:30:00Z"
    },
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `403`

---

### `GET` `/api/v1/lessons/me/progress`

The caller's progress across every lesson they have started

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "lessonId": 0,
        "title": "string",
        "moduleName": "string",
        "status": "string",
        "watchedSeconds": 0,
        "durationSeconds": 0,
        "completedAt": "2026-01-15T10:30:00Z",
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

### `GET` `/api/v1/lessons/modules`

Distinct published module names with their lesson counts

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "moduleName": "string",
      "lessonCount": 0
    }
  ],
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/lessons/{id}`

One lesson with the caller's progress

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

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
    "description": "string",
    "moduleName": "string",
    "videoUrl": "string",
    "durationSeconds": 0,
    "sortOrder": 0,
    "published": true,
    "createdByUuid": "string",
    "progress": {
      "status": "string",
      "watchedSeconds": 0,
      "completedAt": "2026-01-15T10:30:00Z"
    },
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `PUT` `/api/v1/lessons/{id}`

Edit a lesson — creator or ADMIN only

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "title": "string",
  "description": "string",
  "moduleName": "string",
  "videoUrl": "string",
  "durationSeconds": 0,
  "sortOrder": 0,
  "published": true
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "description": "string",
    "moduleName": "string",
    "videoUrl": "string",
    "durationSeconds": 0,
    "sortOrder": 0,
    "published": true,
    "createdByUuid": "string",
    "progress": {
      "status": "string",
      "watchedSeconds": 0,
      "completedAt": "2026-01-15T10:30:00Z"
    },
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `DELETE` `/api/v1/lessons/{id}`

Unpublish a lesson (is_published = false) — creator or ADMIN only; never row-deletes

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `POST` `/api/v1/lessons/{id}/progress`

Report watch progress for the caller — upsert; watchedSeconds never moves backwards

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "watchedSeconds": 0,
  "completed": true
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "description": "string",
    "moduleName": "string",
    "videoUrl": "string",
    "durationSeconds": 0,
    "sortOrder": 0,
    "published": true,
    "createdByUuid": "string",
    "progress": {
      "status": "string",
      "watchedSeconds": 0,
      "completedAt": "2026-01-15T10:30:00Z"
    },
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `GET` `/api/v1/lessons/{id}/quiz`

The lesson's quiz questions (correct-answer keys are never returned)

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

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
      "id": 0,
      "questionText": "string",
      "options": [
        "string"
      ],
      "sortOrder": 0
    }
  ],
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `POST` `/api/v1/lessons/{id}/quiz/questions`

Add an MCQ to the lesson quiz — creator or ADMIN only

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "questionText": "string",
  "options": [
    "string"
  ],
  "correctIndex": 0,
  "explanation": "string"
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "questionText": "string",
    "options": [
      "string"
    ],
    "sortOrder": 0
  },
  "error": null
}
```

**Status codes:** `201`, `400`, `403`, `404`

---

### `DELETE` `/api/v1/lessons/{id}/quiz/questions/{questionId}`

Remove a quiz question — creator or ADMIN only

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |
| `questionId` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `POST` `/api/v1/lessons/{id}/quiz/submit`

Submit quiz answers — grades against the key; >= 60% marks the lesson COMPLETED

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "answers": [
    0
  ]
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "score": 0,
    "total": 0,
    "passed": true,
    "passMarkPercent": 0,
    "submittedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

---

## Notifications

### `GET` `/api/v1/notifications`

The caller's in-app notifications, newest first. ?unreadOnly=true limits to unread rows.

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `unreadOnly` | boolean | no |  |

**Paginated** — also accepts `page` (0-based), `size` (max 100), `sort=field,asc|desc`.

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 0,
        "templateCode": "string",
        "payload": {},
        "read": true,
        "readAt": "2026-01-15T10:30:00Z",
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

### `PUT` `/api/v1/notifications/read-all`

Mark every unread in-app notification read; returns how many were flipped

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "markedRead": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `GET` `/api/v1/notifications/unread-count`

How many unread in-app notifications the caller has — for a bell badge

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "unreadCount": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

### `PUT` `/api/v1/notifications/{id}/read`

Mark one notification read — idempotent; 404 if the id is not the caller's own

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

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
    "templateCode": "string",
    "payload": {},
    "read": true,
    "readAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

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

List placements visible to the caller — optional stage filter

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

## Public

### `GET` `/api/v1/public/stats`

Aggregate counts for the landing page (graduates, placements, …)

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "graduates": 0,
    "activeLearners": 0,
    "activeBatches": 0,
    "placements": 0,
    "certificatesIssued": 0,
    "hiringPartners": 0
  },
  "error": null
}
```

**Status codes:** `200`

---

## Resources

### `GET` `/api/v1/resources`

Browse the resource library — optional category / search filters. includeInactive is honoured only for curator roles.

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `category` | string | no |  |
| `search` | string | no |  |
| `includeInactive` | boolean | no |  |

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
        "description": "string",
        "category": "ARTICLE",
        "url": "string",
        "tags": [
          "string"
        ],
        "createdByUuid": "string",
        "active": true,
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

### `POST` `/api/v1/resources`

Add a resource to the library

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Request body:**

```json
{
  "title": "string",
  "description": "string",
  "category": "ARTICLE",
  "url": "string",
  "tags": [
    "string"
  ]
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "description": "string",
    "category": "ARTICLE",
    "url": "string",
    "tags": [
      "string"
    ],
    "createdByUuid": "string",
    "active": true,
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `403`

---

### `GET` `/api/v1/resources/{id}`

One resource by id

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

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
    "description": "string",
    "category": "ARTICLE",
    "url": "string",
    "tags": [
      "string"
    ],
    "createdByUuid": "string",
    "active": true,
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `404`

---

### `PUT` `/api/v1/resources/{id}`

Edit a resource — creator or ADMIN only

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "title": "string",
  "description": "string",
  "category": "ARTICLE",
  "url": "string",
  "tags": [
    "string"
  ],
  "active": true
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "title": "string",
    "description": "string",
    "category": "ARTICLE",
    "url": "string",
    "tags": [
      "string"
    ],
    "createdByUuid": "string",
    "active": true,
    "createdAt": "2026-01-15T10:30:00Z",
    "updatedAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

---

### `DELETE` `/api/v1/resources/{id}`

Deactivate a resource (is_active = false) — creator or ADMIN only; never row-deletes

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Response `200`:**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**Status codes:** `200`, `403`, `404`

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

### `GET` `/api/v1/subscriptions/me/invoices`

The caller's billing history — captured/refunded payments with invoice status and a short-lived PDF link (null while the invoice is still being generated)

**Auth:** Authenticated (any logged-in user)

**Response `200`:**

```json
{
  "success": true,
  "data": [
    {
      "invoiceNumber": "string",
      "planCode": "string",
      "planName": "string",
      "amount": 0.0,
      "currency": "string",
      "paymentStatus": "CREATED",
      "invoiceStatus": "string",
      "paidAt": "2026-01-15T10:30:00Z",
      "pdfUrl": "string",
      "reference": "string"
    }
  ],
  "error": null
}
```

**Status codes:** `200`

---

## Talent

### `GET` `/api/v1/recruitment-requests`

List recruitment requests — a CLIENT sees their own, ADMIN/HR_MANAGER see all

**Auth:** Role: CLIENT or ADMIN or HR_MANAGER

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
        "id": 0,
        "candidateUuid": "string",
        "candidateName": "string",
        "requestedByUuid": "string",
        "roleTitle": "string",
        "engagementType": "FULL_TIME",
        "message": "string",
        "status": "PENDING",
        "decisionNote": "string",
        "decidedByUuid": "string",
        "decidedAt": "2026-01-15T10:30:00Z",
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

### `POST` `/api/v1/recruitment-requests`

Request to recruit a candidate — lands PENDING for ADMIN/HR review

**Auth:** Role: CLIENT

**Request body:**

```json
{
  "candidateUuid": "string",
  "roleTitle": "string",
  "engagementType": "FULL_TIME",
  "message": "string"
}
```

**Response `201`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "candidateUuid": "string",
    "candidateName": "string",
    "requestedByUuid": "string",
    "roleTitle": "string",
    "engagementType": "FULL_TIME",
    "message": "string",
    "status": "PENDING",
    "decisionNote": "string",
    "decidedByUuid": "string",
    "decidedAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `201`, `403`, `404`

---

### `PUT` `/api/v1/recruitment-requests/{id}/status`

Approve or reject a PENDING recruitment request

**Auth:** Role: ADMIN or HR_MANAGER

**Path parameters:**

| name | type | description |
|---|---|---|
| `id` | integer |  |

**Request body:**

```json
{
  "status": "PENDING",
  "decisionNote": "string"
}
```

**Response `200`:**

```json
{
  "success": true,
  "data": {
    "id": 0,
    "candidateUuid": "string",
    "candidateName": "string",
    "requestedByUuid": "string",
    "roleTitle": "string",
    "engagementType": "FULL_TIME",
    "message": "string",
    "status": "PENDING",
    "decisionNote": "string",
    "decidedByUuid": "string",
    "decidedAt": "2026-01-15T10:30:00Z",
    "createdAt": "2026-01-15T10:30:00Z"
  },
  "error": null
}
```

**Status codes:** `200`, `400`, `403`, `404`, `409`

---

### `GET` `/api/v1/talent-pool`

Browse candidate profiles — optional search (name/title) and skill filters

**Auth:** Role: CLIENT or ADMIN or HR_MANAGER or LEAD_GEN

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `search` | string | no |  |
| `skill` | string | no |  |

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
        "currentTitle": "string",
        "location": "string",
        "experienceLevel": "string",
        "yearsExperience": 0,
        "skills": [
          "string"
        ],
        "portfolioSlug": "string",
        "bio": "string"
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
    "completionPercent": 0,
    "twoFactorEnabled": true
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
    "completionPercent": 0,
    "twoFactorEnabled": true
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
    "completionPercent": 0,
    "twoFactorEnabled": true
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

## o-auth-2-default-callback-fallback-controller

### `GET` `/login/oauth2/code/**`

**Auth:** Authenticated (any logged-in user) — no explicit role check on the route

**Query parameters:**

| name | type | required | description |
|---|---|---|---|
| `error` | string | no |  |

**Status codes:** `200`

---
