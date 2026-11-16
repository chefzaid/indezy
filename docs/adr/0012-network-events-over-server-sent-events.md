# ADR 0012: Network Events Over Server-Sent Events And PostgreSQL Notifications

- Status: Accepted
- Date: 2026-11-16

## Context

Members of the network write to each other, and a page that polls every 10 seconds shows a message late and costs a
request every few seconds for each open conversation. The owner chose server-sent events over WebSockets (TODO.md,
Needs Input, 2026-11-16). The API runs behind Traefik, with the SSO forward-auth middleware, and on more than one replica
in the high-availability overlay, so the replica holding a member's stream is not always the one that saved the change.
The API authenticates each request with a bearer token, which the browser's `EventSource` cannot send.

## Decision

- `GET /api/network/stream` answers `text/event-stream` (`SseEmitter`): `ready` once open, then `message` and `read`
  with the conversation, `typing` with the conversation and a first name, and `notice`; never a message's text. The
  page then reads what changed through the usual endpoints and rights.
- A stream lasts 15 minutes before the page opens another, gets a comment every 25 seconds so that proxies keep it
  open, and is closed when the replica stops; a member holds 5 at most. `X-Accel-Buffering: no` and `Cache-Control:
  no-cache` keep proxies from holding events.
- Events go out after their transaction commits. On PostgreSQL each one is a `NOTIFY` on `indezy_network` with the
  members it is for; every replica listens on one connection of its own (`NetworkEventRelay`) and tells its own
  streams (`NetworkEventHub`). On another database (the tests' H2) the replica tells its own streams at once.
- The page reads the stream with `fetch`, which carries the token, and opens it again after it ends (at once after a
  lasting stream; 2 seconds after a failure, then twice longer each time, a minute at most; never after 401, 403 or
  404). While it is closed the pages poll as before.
- A stream's later dispatches (its timeout, its end) are let through Spring Security's `ASYNC` dispatcher type: the
  request was authorized when the stream opened.

## Rationale

- One-way events over plain HTTP need no ingress change, and sending stays a POST with its validation and limits.
- PostgreSQL notifications carry events between replicas with the database already there: no broker to run.
- Events that name only what changed keep the access rules in one place, the endpoints the page already calls.

## Consequences

- Each replica holds one database connection for listening, outside the pool's turnover.
- A notification payload is limited to 8000 bytes; an event names at most a group's 20 members, far below it.
- An event lost while a page reconnects is caught up by the page's next read, since it reads the state rather than
  replaying events.
- The PostgreSQL driver is a compile dependency, for its notification API.
