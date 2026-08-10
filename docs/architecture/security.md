# Security Architecture

**Status:** Draft
**Scope:** Authentication, authorization, service security, secrets, financial operations

## 1. Security Principles

The platform follows:

```text
Least privilege

Zero trust between services

Defense in depth

Secure by default

Explicit authorization

Auditable privileged actions
```

Being inside the internal network does not automatically make a request trusted.

---

## 2. Authentication

Users authenticate through the Identity Service.

Initial approach:

```text
Access Token
→ short-lived JWT

Refresh Token
→ longer-lived and revocable
```

JWTs may contain:

```text
userId
sessionId
roles
issuedAt
expiresAt
```

Do not place sensitive user information inside tokens.

---

## 3. Password Security

Passwords must:

* Never be stored as plaintext
* Be hashed using a strong password hashing algorithm
* Use unique salts
* Never appear in logs or events

Password verification belongs only to the Identity Service.

---

## 4. Multi-Factor Authentication

MFA should be required for high-risk actions such as:

```text
Change payout account

Large payout

Administrative login

Financial adjustment

Account recovery
```

---

## 5. Authorization

Authentication answers:

```text
Who are you?
```

Authorization answers:

```text
Can you perform this action?
```

Authorization must consider:

```text
role
+
resource ownership
+
transaction relationship
+
account restrictions
```

Example:

A valid authenticated user cannot call:

```text
AcceptDelivery
```

unless they are the buyer for that escrow.

---

## 6. RBAC and Resource-Level Authorization

RBAC may define broad roles:

```text
USER
SUPPORT
DISPUTE_OFFICER
COMPLIANCE_OFFICER
FINANCE_OPERATOR
ADMIN
```

But business authorization must also inspect the resource.

Example:

```text
role = USER

AND

userId == escrow.buyerId
```

---

## 7. Service-to-Service Authentication

Internal services must authenticate each other.

Possible production approaches include:

```text
mTLS

signed service tokens

workload identity
```

Kubernetes network access alone is not sufficient authentication.

---

## 8. API Gateway

The gateway may:

* Validate access tokens
* Reject expired tokens
* Apply rate limits
* Sanitize headers
* Generate correlation IDs

But downstream services must still enforce domain authorization.

The gateway must not become the only security layer.

---

## 9. Secrets

Secrets include:

```text
database passwords

Kafka credentials

RabbitMQ credentials

provider API keys

JWT signing keys

encryption keys
```

They must not be committed to Git.

Local development may use environment variables.

Production should use a dedicated secrets-management mechanism.

---

## 10. Environment Files

The repository must ignore:

```text
.env
application-local.yml
application-local.properties
```

Provide safe examples such as:

```text
.env.example
```

without real credentials.

---

## 11. Encryption

All external and internal sensitive communication must use encrypted transport.

Production:

```text
TLS
```

Sensitive stored data must use appropriate encryption at rest.

Highly sensitive values may require application-level encryption.

---

## 12. Financial Authorization

Financial operations require stronger checks.

Before release:

```text
authenticated actor

correct escrow participant

valid escrow state

no dispute

no hold

sufficient funds

idempotency protection
```

Security checks do not replace financial invariants.

---

## 13. Administrative Security

Administrators must not directly modify:

```text
balances

ledger entries

financial history
```

Financial corrections require controlled commands.

Example:

```text
CreateFinancialAdjustment
```

which produces audited ledger entries.

---

## 14. Maker-Checker

High-risk operations may require two people.

Example:

```text
Finance Operator A
→ creates ₦50M adjustment

Finance Operator B
→ approves adjustment
```

One person must not be able to both create and approve sensitive adjustments.

---

## 15. Payment Provider Webhooks

Provider webhooks must verify:

```text
signature

timestamp

provider identity

event reference
```

Replay protection must exist.

Duplicate valid webhooks must still be safe because business processing is idempotent.

---

## 16. Marketplace Webhooks

Outgoing webhooks should include:

```text
eventId

timestamp

signature

event type

payload
```

Marketplace consumers must expect duplicate delivery.

Webhook secrets should support rotation.

---

## 17. Rate Limiting

Rate limiting protects:

* Login
* Password reset
* OTP verification
* Escrow creation
* Payment initiation
* Public APIs
* Marketplace APIs

Possible dimensions:

```text
IP

userId

API key

endpoint

marketplace
```

Redis may maintain distributed counters.

---

## 18. Brute-Force Protection

Authentication endpoints should support:

```text
attempt limits

temporary lockouts

IP/device signals

progressive delays

risk detection
```

Error responses should avoid revealing unnecessary account information.

---

## 19. Session Security

The platform should support:

* Session revocation
* Logout
* Device visibility
* Refresh-token rotation
* Suspicious-session termination

A compromised refresh token must not provide indefinite access.

---

## 20. Sensitive Logging

Never log:

```text
passwords

access tokens

refresh tokens

API secrets

private keys

full bank credentials

identity documents
```

Logs may contain stable internal identifiers where appropriate.

---

## 21. Audit Logging

Important security events include:

```text
LoginSucceeded
LoginFailed
PasswordChanged
MfaEnabled
PayoutDestinationChanged
AccountRestricted
AdminActionPerformed
FinancialAdjustmentApproved
```

Audit records must be immutable.

---

## 22. File Upload Security

Evidence uploads must use:

* Size limits
* Allowed content types
* Malware scanning
* Random object names
* Authorization checks
* Signed upload/download URLs

Application services should not blindly trust uploaded filenames or MIME types.

---

## 23. Database Security

Each service should use its own database credentials.

Example:

```text
escrow-service
→ escrow database user

ledger-service
→ ledger database user
```

Escrow credentials should not have access to Ledger tables.

---

## 24. Broker Security

Kafka and RabbitMQ must use:

```text
authentication

authorization

TLS in production

service-specific permissions
```

A Notification Service should not have unrestricted access to every financial topic.

---

## 25. Redis Security

Redis should not be publicly exposed.

Access should be:

* Network restricted
* Authenticated
* Encrypted where required

Sensitive permanent data should not depend solely on Redis.

---

## 26. Elasticsearch Security

Elasticsearch must not be directly exposed to public clients.

Applications should access search through controlled services.

Search indexes must avoid unnecessary sensitive information.

---

## 27. Security Headers

Public HTTP responses should use appropriate security headers where applicable.

Examples include protections against:

* MIME sniffing
* framing
* unsafe browser transport

Frontend-specific controls will be handled at the edge/gateway.

---

## 28. Dependency Security

CI should later include:

```text
dependency vulnerability scanning

container image scanning

secret scanning

static analysis
```

Critical vulnerabilities should block deployment.

---

## 29. Production Access

Production access must follow least privilege.

Engineers should not routinely connect directly to production databases.

Sensitive production actions require:

```text
authentication

authorization

audit logging

time-limited access where possible
```

---

## 30. Security Failure Rules

```text
Invalid token
→ 401

Authenticated but unauthorized
→ 403

Suspicious financial operation
→ HOLD / REVIEW

Compromised credential
→ revoke session

Webhook signature invalid
→ reject

Security dependency uncertain
→ fail safely for sensitive operations
```

---

## 31. Core Rules

```text
Never trust the client.

Never trust network location alone.

Never store plaintext passwords.

Never commit secrets.

Never put sensitive data in events unnecessarily.

Never allow direct admin balance edits.

Never rely only on the API Gateway for authorization.

Every financial operation requires explicit authorization.

Every privileged action must be auditable.
```
