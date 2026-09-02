# Payment Webhooks — full API reference

Both endpoints are **public at the auth layer** (gateways cannot send a JWT) but **secured by
HMAC signature verification** inside the controller, and both sit behind the global rate limiter.

Source of truth: `payment/PaymentWebhookController.java`, `payment/gateway/RazorpayService.java`,
`payment/gateway/StripeService.java`, `payment/PaymentWebhookService.java`,
`payment/WebhookIdempotencyService.java`. See also `docs/PROJECT-REFERENCE.md` §1.5 (enrolment
flow) and §2.2 / the *Webhooks* module.

---

## Common contract (both gateways)

| | |
|---|---|
| **Base** | `POST /api/v1/webhooks/{razorpay\|stripe}` |
| **Content-Type** | `application/json` |
| **Body** | raw JSON, read as a `String` **before any parsing** — the signature is verified against these exact bytes, so no proxy may re-serialise the body |
| **Success** | `200 OK`, **empty body** (`ResponseEntity<Void>`) — returned for a freshly-processed event **and** for a duplicate |
| **Bad signature** | `400` with the standard `ApiResponse` error envelope, `error.code = INVALID_WEBHOOK_SIGNATURE` |
| **Valid signature but malformed JSON** | `400`, `error.code = VALIDATION_FAILED` |
| **Handler failed mid-processing** | `5xx` (`error.code = INTERNAL_ERROR`) — the idempotency claim is released first, so the gateway's redelivery is treated as fresh work. This is deliberate: a non-2xx is how you make the gateway retry. |
| **Rate limited** | `429`, `error.code = RATE_LIMIT_EXCEEDED` |
| **Idempotency** | first thing after the signature check: `INSERT` a key into `webhook_events(event_id)` (unique). A duplicate key → `200` immediately, **no state change**. |
| **Ordering** | signature → idempotency claim → **one `@Transactional`**: `payments → CAPTURED`, `user_subscriptions → ACTIVE`, `invoices` row (`PENDING`), batch seat reserved → commit → `afterCommit`: enqueue invoice-PDF job + welcome notification. PDF rendering is **never** inside the gateway's ~5 s window. |
| **Env vars** | Razorpay: `RAZORPAY_WEBHOOK_SECRET` · Stripe: `STRIPE_WEBHOOK_SECRET` |

### Response envelope on error

```json
{
  "success": false,
  "data": null,
  "error": { "code": "INVALID_WEBHOOK_SIGNATURE", "message": "Webhook signature verification failed." },
  "timestamp": "2026-01-15T10:30:00Z"
}
```

---

## Razorpay

### Endpoint

```
POST /api/v1/webhooks/razorpay
Header: X-Razorpay-Signature: <hex HMAC-SHA256 of the raw body, keyed by RAZORPAY_WEBHOOK_SECRET>
```

Verified by `com.razorpay.Utils.verifyWebhookSignature(rawBody, signatureHeader, webhookSecret)` —
constant-time, HMAC-SHA256, hex-encoded.

### Dashboard setup

Razorpay Dashboard → **Settings → Webhooks → Add New Webhook**

- **Webhook URL:** `https://<your-host>/api/v1/webhooks/razorpay`
- **Secret:** any string — put the *same* value in `.env` as `RAZORPAY_WEBHOOK_SECRET`. HMAC is
  symmetric, so for local testing it just has to match; it does not have to be "issued" by
  Razorpay.
- **Active events:** tick **`payment.captured`** and **`refund.processed`**. All other events are
  received, logged, and ignored with `200`.

### Events handled

| `event` | Payload paths the backend reads | Effect |
|---|---|---|
| `payment.captured` | `payload.payment.entity.order_id`, `.id`, `.amount` (paise → ÷100) | look up `payments` by `gateway_order_id`; **re-check** `entity.amount/100 == payments.amount` (server-computed at checkout). Match → `payments.status=CAPTURED`, `gateway_payment_id` set, `captured_at` set; then activate subscription + invoice + batch allocation + `PaymentCapturedEvent`. Mismatch → `payments.status=FAILED`, `failure_reason` set, `PAYMENT_CAPTURE_FAILED` audit row, **still `200`**. Caller already has an ACTIVE subscription → payment stays `CAPTURED`, `PAYMENT_CAPTURED_NO_SUBSCRIPTION` audit + `ERROR` log for a manual refund, invoice/allocation/event skipped, **still `200`**. |
| `refund.processed` | `payload.payment.entity.id` | look up `payments` by `gateway_payment_id`; `status=REFUNDED`, `PAYMENT_REFUNDED` audit; the funding subscription → `CANCELLED` and the student is de-allocated (`batch_students.status=REASSIGNED`). Already `REFUNDED` → no-op. |
| anything else | — | logged, `200`, nothing changes |

### Idempotency key

`event_id = "{event}:{payload.payment.entity.id or payload.refund.entity.id}"` — e.g.
`payment.captured:pay_PkX9…`. Stable across Razorpay's own retry redeliveries of the same event.

### Minimal `payment.captured` payload (only the fields consumed)

```json
{
  "event": "payment.captured",
  "payload": {
    "payment": {
      "entity": {
        "id": "pay_PkX9abcdEF1234",
        "order_id": "order_PkX8zzzzEF0000",
        "amount": 1499900,
        "currency": "INR",
        "status": "captured"
      }
    }
  }
}
```

`amount` is **paise** — 1499900 = ₹14,999.00, which must equal the `payments.amount` set at
checkout.

### `refund.processed` payload

```json
{
  "event": "refund.processed",
  "payload": {
    "payment": { "entity": { "id": "pay_PkX9abcdEF1234" } },
    "refund":  { "entity": { "id": "rfnd_PkYb11112222" } }
  }
}
```

### Local test with curl

```bash
BODY='{"event":"payment.captured","payload":{"payment":{"entity":{"id":"pay_TEST1","order_id":"order_TEST1","amount":1499900,"currency":"INR","status":"captured"}}}}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$RAZORPAY_WEBHOOK_SECRET" -hex | sed 's/^.* //')
curl -sS -i -X POST http://localhost:8080/api/v1/webhooks/razorpay \
  -H "Content-Type: application/json" \
  -H "X-Razorpay-Signature: $SIG" \
  --data "$BODY"
```

`order_TEST1` must be a real `payments.gateway_order_id` created by
`POST /api/v1/subscriptions/checkout` for the state change to land; otherwise it is logged as
"unknown gatewayOrderId" and returns `200`.

---

## Stripe

### Endpoint

```
POST /api/v1/webhooks/stripe
Header: Stripe-Signature: t=<ts>,v1=<HMAC-SHA256 of "{t}.{rawBody}" keyed by STRIPE_WEBHOOK_SECRET>
```

Verified **and parsed in one call** by
`com.stripe.net.Webhook.constructEvent(rawBody, sigHeader, webhookSecret)` — the backend never
`readTree`s the body itself. Bad signature → `Optional.empty()` → `400 INVALID_WEBHOOK_SIGNATURE`.

### Dashboard setup

Stripe Dashboard (**Test mode**) → **Developers → Webhooks → Add endpoint**

- **Endpoint URL:** `https://<your-host>/api/v1/webhooks/stripe`
- **Events to send:** **`checkout.session.completed`** and **`charge.refunded`**
- Copy the **Signing secret** (`whsec_…`) → `.env` `STRIPE_WEBHOOK_SECRET`
- Also set `.env` `STRIPE_SECRET_KEY` = your `sk_test_…` (used for creating Checkout Sessions in
  `POST /subscriptions/checkout`, not the webhook)

### Events handled

| `type` | Object fields read | Effect |
|---|---|---|
| `checkout.session.completed` | `data.object.client_reference_id` (= `payments.id`, set at checkout), `.amount_total` (minor unit → ÷100), `.payment_intent` | look up `payments` **by id**; same amount re-check as Razorpay; on match → capture + activate + invoice + allocate + event. Mismatch → `FAILED` + audit + `200`. Pre-existing ACTIVE subscription → `PAYMENT_CAPTURED_NO_SUBSCRIPTION` + `200`, as Razorpay. |
| `charge.refunded` | `data.object.payment_intent` | look up `payments` by `gateway_payment_id` (= the payment-intent id stored at capture); `REFUNDED` + subscription `CANCELLED` + de-allocate. |
| anything else | — | logged, `200` |

### Idempotency key

`event_id = event.getId()` — Stripe's own `evt_…` id, unique per event and constant across
Stripe's retries.

### Minimal `checkout.session.completed` payload (Stripe sends the full envelope; these are the consumed fields)

```json
{
  "id": "evt_1PkX9AbCdEf",
  "type": "checkout.session.completed",
  "data": {
    "object": {
      "object": "checkout.session",
      "client_reference_id": "42",
      "amount_total": 1499900,
      "currency": "inr",
      "payment_intent": "pi_3PkX9AbCdEf",
      "payment_status": "paid"
    }
  }
}
```

`client_reference_id` `"42"` must be a real `payments.id` from `POST /subscriptions/checkout`.
`amount_total` is the minor unit (paise for INR) and must equal `payments.amount × 100`.

### `charge.refunded` payload (consumed fields)

```json
{
  "id": "evt_1PkYbRefund",
  "type": "charge.refunded",
  "data": { "object": { "object": "charge", "payment_intent": "pi_3PkX9AbCdEf", "refunded": true } }
}
```

### Local test — the easy way (Stripe CLI generates a valid signature for you)

```bash
stripe login
stripe listen --forward-to localhost:8080/api/v1/webhooks/stripe   # prints a whsec_… → put in .env, restart the app
# in another shell, after creating a checkout session via the app:
stripe trigger checkout.session.completed
stripe trigger charge.refunded
```

### Local test — manual curl

```bash
BODY='{"id":"evt_TEST1","type":"checkout.session.completed","data":{"object":{"object":"checkout.session","client_reference_id":"42","amount_total":1499900,"currency":"inr","payment_intent":"pi_TEST1","payment_status":"paid"}}}'
T=$(date +%s)
SIG=$(printf '%s' "$T.$BODY" | openssl dgst -sha256 -hmac "$STRIPE_WEBHOOK_SECRET" -hex | sed 's/^.* //')
curl -sS -i -X POST http://localhost:8080/api/v1/webhooks/stripe \
  -H "Content-Type: application/json" \
  -H "Stripe-Signature: t=$T,v1=$SIG" \
  --data "$BODY"
```

---

## Retry / idempotency semantics (both)

1. **Duplicate delivery** (same `event_id`) → `claim()` returns `false` → `200`, nothing runs.
   Replaying `payment.captured` twice yields **one** subscription and **one** invoice.
2. **Handler throws** → the `webhook_events` claim is `DELETE`d (released) and the exception
   rethrown → `5xx` → the gateway redelivers → on redelivery it is re-claimed cleanly and
   reprocessed.
3. **Process killed mid-handler** → the claim row is left `RECEIVED`; `claim()` reclaims it after
   15 min on the next delivery, and `WebhookReconciliationJob` (every 10 min) logs an `ERROR` for
   any row stuck > 30 min.
4. **Success** → the `webhook_events` row is flipped to `PROCESSED` (`processed_at` set)
   **in the same transaction** as the business state change.

---

## Notes

- `openssl` is on the PATH (mingw64), so the `openssl dgst -sha256 -hmac` recipes work in the Git
  Bash terminal. `RAZORPAY_WEBHOOK_SECRET` / `STRIPE_WEBHOOK_SECRET` only need to be present in
  `.env` and match whatever you sign the test payload with.
- End-to-end, a webhook only produces a state change when the referenced payment row exists —
  create it first with `POST /api/v1/subscriptions/checkout` (which writes a `payments` row in
  `CREATED` and returns the gateway order / session). See `docs/PROJECT-REFERENCE.md` →
  *Checkout* and *Subscriptions*.
- `ErrorCode` mapping: `INVALID_WEBHOOK_SIGNATURE` → 400, `VALIDATION_FAILED` → 400,
  `PAYMENT_GATEWAY_ERROR` → 502, `RATE_LIMIT_EXCEEDED` → 429, unhandled handler exception →
  `INTERNAL_ERROR` → 500.
