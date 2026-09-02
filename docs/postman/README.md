# Moriah Skill Hub — Postman (role-aware)

Import `Moriah-Skill-Hub.postman_collection.json` (the environment file is optional — every variable also lives on the collection).

## Logging in per role
`Auth /` has one **Login** request per seeded account. Running it stores a role-specific token:

| Run this | Sets |
|---|---|
| Login — student1 | `{{studentAccessToken}}` |
| Login — student3 (STARTER) | `{{student3AccessToken}}` — for `403 ENTITLEMENT_REQUIRED` tests |
| Login — pm | `{{pmAccessToken}}` |
| Login — dev | `{{devAccessToken}}` |
| Login — sales | `{{salesAccessToken}}` |
| Login — ba | `{{baAccessToken}}` |
| Login — client | `{{clientAccessToken}}` |
| Login — admin (2FA) → steps 1,2,3 | `{{adminAccessToken}}` |
| Login — hr (2FA) → steps 1,2,3 | `{{hrAccessToken}}` |

Every endpoint in the module folders already uses the right one (its description lists which roles are allowed). Password for all: **Password123!**.

## Admin / HR 2FA
1. **1 · Login** → stores `{{adminChallengeToken}}` (valid 5 min).
2. **2 · Enable 2FA** → stores `{{adminTotpSecret}}` (first login only).
3. **3 · Verify 2FA** → a pre-request script computes the current 6-digit code from `{{adminTotpSecret}}` and submits it → `{{adminAccessToken}}`.
   If the code is blank, paste one from `python -c "import pyotp; print(pyotp.TOTP('SECRET').now())"` into the `adminTotpCode` variable and resend.

## Seeded UUIDs (pre-filled variables)
`{{student1Uuid}}`=…0008, `{{student2Uuid}}`=…0009, `{{student3Uuid}}`=…0010, `{{pmUuid}}`=…0002, `{{devUuid}}`=…0003, `{{hrUuid}}`=…0005, `{{adminUuid}}`=…0001, etc.

## Carry-over IDs
`{{batchId}}`, `{{sprintId}}`, `{{submissionId}}`, `{{paymentId}}`, `{{verificationCode}}`, … are blank — set them from earlier responses. Full sequence: `docs/testing-flow.md`.

## Webhooks
`/webhooks/razorpay` and `/webhooks/stripe` have a pre-request script that signs the body with `{{webhookSecret}}` — set it to your `.env` `RAZORPAY_WEBHOOK_SECRET` / `STRIPE_WEBHOOK_SECRET`.

Regenerate: `python scripts/gen-postman.py` (after re-exporting `docs/openapi.json`).
