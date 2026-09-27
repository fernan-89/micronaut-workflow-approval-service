# ADR-032: Client Integration Is Synchronous HTTP, Not an Outbox/NATS Event

## Status
Accepted

## Context
`it-change-management` (GMUD) needs to know an `ApprovalRequest`'s outcome as soon as it resolves, so
it can react — advancing a `ChangeRequest` to `APPROVED` or `REJECTED` — without a polling loop or a
noticeable delay. The platform's event backbone (kit ADR-003: `OutboxStore` + NATS JetStream,
established in `it-hardware-maintenance` → `it-asset-registry`, Journey 6) is the default integration
pattern between services, but it is built for one producer feeding an unknown number of eventually-
consistent consumers, not for a single caller that needs the answer in the same request.

## Decision
- This service exposes no outbox producer and publishes no events. `it-change-management` calls
  `PUT /workflow-approval/v1/{id}/decision/capture` directly (a declarative HTTP client, mirroring
  the existing `HashServicePort`/`HashServiceAdapter` pattern) and reads the returned
  `ApprovalRequestResponse.status` in the same response.
- `CaptureDecisionUseCase` returns the post-decision state specifically so this synchronous read-back
  works — see ADR-031.
- If a second client Service Domain later needs approval outcomes asynchronously (e.g. for a dashboard
  that must not block on this service's availability), an outbox producer can be added without
  changing this contract; GMUD would keep using the synchronous path since it genuinely needs the
  answer inline.

## Consequences
- Positive: GMUD reacts to an approval decision in the same request/response cycle it submitted; no
  event schema, no NATS subject, no idempotent-consumer bookkeeping (`ProcessedEventRepository`) is
  needed for this integration.
- Positive: keeps this journey's scope tractable — two new services (this one and GMUD) is already a
  large unit of work; an event round-trip between them would have doubled the surface for no present
  benefit.
- Negative: `it-change-management` has a hard runtime dependency on this service's availability for
  `approval/capture` to succeed — a synchronous coupling the event backbone would have avoided. Accepted
  for v1, consistent with the platform's other direct-call integrations (e.g. `HashServicePort`).
- Negative: this service cannot notify any future third-party consumer that it doesn't already know
  about; add an outbox producer if and when that need appears (see ADR-034 in `it-hardware-maintenance`
  for the platform's existing best-effort outbox pattern to follow).
