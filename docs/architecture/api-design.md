# API Design Specification

**Status:** Draft
**Scope:** Public and internal service APIs

## 1. Base Convention

Public APIs use:

```text
/api/v1
```

Examples:

```text
POST /api/v1/escrows
GET  /api/v1/escrows/{escrowId}
POST /api/v1/escrows/{escrowId}/accept-terms
POST /api/v1/escrows/{escrowId}/fund
POST /api/v1/escrows/{escrowId}/accept-delivery
```

Use nouns for resources and explicit action endpoints where the operation represents a domain command.

---

## 2. Standard Headers

Every request should support:

```http
Authorization: Bearer <token>
X-Correlation-Id: <uuid>
```

Financial/state-changing requests should support:

```http
Idempotency-Key: <unique-key>
```

The server generates a correlation ID if the client does not provide one.

---

## 3. Idempotency

Required for operations such as:

* Funding
* Release
* Refund
* Payout
* Financial adjustment

Example:

```http
POST /api/v1/escrows/{id}/fund
Idempotency-Key: fund-8fa21
```

Retrying the same request with the same key must not create another financial effect.

Reusing the key with a different payload must return an error.

---

## 4. Response Format

Successful resource response:

```json
{
  "data": {
    "id": "019c...",
    "status": "FUNDED"
  },
  "correlationId": "019c..."
}
```

Do not expose internal entity models directly.

Use explicit API response DTOs.

---

## 5. Error Format

All services use one error structure:

```json
{
  "code": "ESCROW_INVALID_STATE",
  "message": "Escrow cannot be funded in its current state.",
  "correlationId": "019c...",
  "details": []
}
```

Common status codes:

```text
200 OK
201 Created
202 Accepted
204 No Content

400 Bad Request
401 Unauthorized
403 Forbidden
404 Not Found
409 Conflict
422 Unprocessable Entity
429 Too Many Requests

500 Internal Server Error
503 Service Unavailable
```

Business conflicts such as duplicate state transitions normally use `409`.

---

## 6. Asynchronous Operations

Long-running financial operations should not pretend to complete synchronously.

Example:

```http
POST /api/v1/payouts
```

Response:

```http
202 Accepted
```

```json
{
  "data": {
    "payoutId": "019c...",
    "status": "PROCESSING"
  }
}
```

Clients can later query:

```text
GET /api/v1/payouts/{payoutId}
```

---

## 7. Pagination

Do not use deep offset pagination for large datasets.

Avoid:

```text
?page=100000
```

Use cursor pagination:

```http
GET /api/v1/escrows?limit=50&cursor=abc123
```

Response:

```json
{
  "data": [],
  "pagination": {
    "nextCursor": "xyz789",
    "hasMore": true
  }
}
```

Default limit:

```text
20
```

Maximum limit:

```text
100
```

---

## 8. Filtering and Sorting

Example:

```http
GET /api/v1/escrows?status=FUNDED&currency=NGN&sort=-createdAt
```

Supported filters must be explicitly documented.

Do not allow arbitrary database-field sorting.

---

## 9. Optimistic Concurrency

Where clients update mutable resources, the API may expose a version.

Example:

```json
{
  "id": "019c...",
  "version": 7
}
```

Updates may require:

```http
If-Match: 7
```

A stale update returns:

```http
409 Conflict
```

---

## 10. Authentication and Authorization

Authentication answers:

```text
Who are you?
```

Authorization answers:

```text
Can you perform this operation on this resource?
```

Example:

A valid JWT does not automatically allow a user to accept delivery.

The Escrow Service must verify:

```text
authenticated user == escrow buyer
```

---

## 11. Rate Limiting

Rate limits may be applied by:

* IP
* User
* API key
* Marketplace
* Endpoint

Possible response:

```http
429 Too Many Requests
Retry-After: 10
```

Redis may support distributed rate-limit counters.

---

## 12. Internal APIs

Internal services must not assume that private network access equals authorization.

Service-to-service communication requires authenticated identity.

Internal endpoints should not expose implementation-specific database operations.

Example:

Good:

```text
POST /internal/v1/risk/evaluate
```

Bad:

```text
POST /internal/v1/database/update-user-risk-column
```

---

## 13. Validation

Validate requests at the API boundary.

Examples:

* Required fields
* Amount greater than zero
* Valid currency
* Supported enum values
* Maximum text sizes
* File size limits

Domain rules must still be enforced inside the domain layer.

---

## 14. API Versioning

Breaking public changes require a new major API version:

```text
/api/v1
/api/v2
```

Do not create a new API version for every additive field.

Existing consumers must remain compatible during migration periods.

---

## 15. Webhooks

Marketplace integrations may receive signed webhooks.

Example:

```text
EscrowFunded
EscrowReleased
PayoutSucceeded
```

Webhook requirements:

* Unique webhook event ID
* Signature
* Timestamp
* Retry policy
* Delivery logs
* Idempotent consumer expectation

Receivers must expect duplicate delivery.

---

## 16. OpenAPI

Every public service API must expose an OpenAPI specification.

API contracts should be reviewable before frontend or external consumers depend on them.

---

## 17. Core API Rules

```text
Never expose JPA entities directly.

Financial writes require idempotency.

Use cursor pagination for large datasets.

Use explicit error codes.

Propagate correlation IDs.

Validate authorization at resource level.

Use 202 for genuinely asynchronous operations.

Do not hide business commands behind generic PATCH endpoints.

Do not trust client-side financial status.
```

## 18. First Vertical Slice APIs

The initial implementation will require approximately:

```text
POST /api/v1/auth/register

POST /api/v1/escrows

POST /api/v1/escrows/{id}/accept-terms

GET  /api/v1/escrows/{id}

POST /api/v1/escrows/{id}/fund

GET  /api/v1/payments/{id}
```

Additional internal event-driven processing will move the escrow from:

```text
FUNDING_PROCESSING
```

to:

```text
FUNDED
```

after the Ledger Service confirms secured funds.
