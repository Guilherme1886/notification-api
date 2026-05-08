# notification-api

A multi-channel notification service that accepts delivery requests through a REST API, renders message templates, dispatches them through pluggable channel senders (email, SMS, push), and tracks every attempt through a strict state machine with retries and a dead-letter queue.

The service is designed as a study of clean layering, the Strategy pattern, and operationally-honest async processing — every notification is durable in PostgreSQL from the moment it is accepted, every attempt is counted, and every terminal outcome (delivered or dead-lettered) is observable through the API.

---

## Live demo

**Base URL:** https://notification-api-ucxm.onrender.com

> Hosted on Render's free tier, which **spins the instance down after ~15 minutes of inactivity**. The first request after a cold start can take 30–60 seconds while the container boots and Flyway runs its migrations. Subsequent requests respond in milliseconds.

### Try it with curl

Create an email template:

```bash
curl -X POST https://notification-api-ucxm.onrender.com/templates \
  -H 'Content-Type: application/json' \
  -d '{
    "id": "payment_approved",
    "name": "Pagamento Aprovado",
    "channel": "EMAIL",
    "subject": "Seu pagamento foi aprovado",
    "body": "Olá {{nome}}, seu pagamento de {{valor}} foi aprovado."
  }'
```

Queue a notification using that template:

```bash
curl -X POST https://notification-api-ucxm.onrender.com/notifications \
  -H 'Content-Type: application/json' \
  -d '{
    "recipientId": "11111111-1111-1111-1111-111111111111",
    "channel": "EMAIL",
    "templateId": "payment_approved",
    "variables": { "nome": "Guilherme", "valor": "R$ 300,00" }
  }'
```

The response carries the notification `id` and a `Location` header. Poll the status with:

```bash
curl https://notification-api-ucxm.onrender.com/notifications/<id>
```

It transitions `PENDING -> PROCESSING -> DELIVERED` (the live senders are logging stubs, so every attempt succeeds unless the sender is intentionally broken).

---

## Stack

- **Java 21** (records, pattern-matched `switch`, virtual-thread-friendly executors)
- **Spring Boot 3.3.5** — web, validation, data-jpa, async
- **PostgreSQL 16** with **Flyway** migrations
- **Hibernate 6** + **hypersistence-utils** for native `JSONB` mapping
- **HikariCP** connection pool
- **Gradle (Kotlin DSL)** with the JVM toolchain pinned to 21
- **JUnit 5** + **Testcontainers** + **Awaitility** for integration tests
- **Docker Compose** for local infrastructure

---

## Architecture

The codebase is split into four layers under `com.example.notification`:

```
+------------------------------------------------------------------+
|                              api                                 |
|  REST controllers, DTOs, exception handlers (HTTP boundary)      |
|  TemplateController, NotificationController, ApiExceptionHandler |
+----------------------------------+-------------------------------+
                                   |
                                   v
+------------------------------------------------------------------+
|                          application                             |
|  Use cases orchestrating domain + ports                          |
|  SendNotificationUseCase, RetryNotificationUseCase,              |
|  GetNotificationUseCase, TemplateUseCase                         |
|  Port: NotificationSender                                        |
+----------------------------------+-------------------------------+
                                   |
                                   v
+------------------------------------------------------------------+
|                            domain                                |
|  Pure business types — no framework imports                      |
|  Notification, Template, NotificationStatus, NotificationChannel |
|  Repository interfaces (NotificationRepository, TemplateReposit) |
+----------------------------------+-------------------------------+
                                   ^
                                   |
+------------------------------------------------------------------+
|                             infra                                |
|  Adapters: JPA repositories, JPA entities, sender implementations|
|  NotificationRepositoryImpl, TemplateRepositoryImpl,             |
|  EmailSender, SmsSender, PushSender, SenderRegistry, AsyncConfig |
+------------------------------------------------------------------+
```

Dependencies point inward: `api -> application -> domain`, and `infra` implements `domain` ports. The domain layer has zero Spring/Hibernate dependencies and is composed of `record`s and enums.

---

## Strategy Pattern: how senders are selected

Each delivery channel has its own `NotificationSender` implementation:

```java
public interface NotificationSender {
    NotificationChannel channel();
    void send(Notification notification, Template template);
}
```

Concrete strategies live in `infra.sender`:

- `EmailSender`  -> `NotificationChannel.EMAIL`
- `SmsSender`    -> `NotificationChannel.SMS`
- `PushSender`   -> `NotificationChannel.PUSH`

`SenderRegistry` collects every `NotificationSender` bean Spring discovers and exposes them keyed by channel:

```java
@Bean
public Map<NotificationChannel, NotificationSender> sendersByChannel(List<NotificationSender> senders) {
    return senders.stream().collect(Collectors.toUnmodifiableMap(
            NotificationSender::channel,
            Function.identity()
    ));
}
```

`SendNotificationUseCase` looks up the right strategy at dispatch time:

```java
var sender = senders.get(processing.channel());
sender.send(processing, template);
```

Adding a new channel means writing one class and dropping it into the package — no `switch`, no registration glue, no controller change.

---

## Endpoints

All bodies are JSON. Errors return `{ "error": "<message>" }`.

### `POST /templates`

Creates a reusable template. Body uses Mustache-like `{{variable}}` placeholders that are interpolated at dispatch time.

```http
POST /templates
Content-Type: application/json

{
  "id": "payment_approved",
  "name": "Pagamento Aprovado",
  "channel": "EMAIL",
  "subject": "Seu pagamento foi aprovado",
  "body": "Olá {{nome}}, seu pagamento de {{valor}} foi aprovado."
}
```

`201 Created`:

```json
{
  "id": "payment_approved",
  "name": "Pagamento Aprovado",
  "channel": "EMAIL",
  "subject": "Seu pagamento foi aprovado",
  "body": "Olá {{nome}}, seu pagamento de {{valor}} foi aprovado.",
  "createdAt": "2026-04-29T01:21:00.940Z"
}
```

### `GET /templates/{id}`

Returns the template by id. `404 Not Found` if it does not exist.

```json
{
  "id": "payment_approved",
  "name": "Pagamento Aprovado",
  "channel": "EMAIL",
  "subject": "Seu pagamento foi aprovado",
  "body": "Olá {{nome}}, seu pagamento de {{valor}} foi aprovado.",
  "createdAt": "2026-04-29T01:21:00.940Z"
}
```

### `POST /notifications`

Queues a notification for delivery. The response carries a `Location` header pointing at `GET /notifications/{id}`. `maxAttempts` is optional (defaults to `5`).

```http
POST /notifications
Content-Type: application/json

{
  "recipientId": "11111111-1111-1111-1111-111111111111",
  "channel": "EMAIL",
  "templateId": "payment_approved",
  "variables": { "nome": "Guilherme", "valor": "R$ 300,00" },
  "maxAttempts": 5
}
```

`202 Accepted`:

```json
{
  "id": "8bc8318f-cad0-4313-8c8c-709898b80302",
  "recipientId": "11111111-1111-1111-1111-111111111111",
  "channel": "EMAIL",
  "templateId": "payment_approved",
  "variables": { "nome": "Guilherme", "valor": "R$ 300,00" },
  "status": "PENDING",
  "attempts": 0,
  "maxAttempts": 5,
  "lastError": null,
  "createdAt": "2026-04-29T01:21:04.131Z",
  "deliveredAt": null
}
```

Failure modes:

- `404 Not Found` — `templateId` does not exist
- `400 Bad Request` — channel mismatch between request and template, or invalid payload

### `GET /notifications/{id}`

Returns the current state of a notification. Same shape as the `POST` response, with `status` advancing through the state machine.

### `POST /notifications/{id}/retry`

Manually re-enqueues a notification that ended in `FAILED` while still having retries left. Returns `202 Accepted`.

- `409 Conflict` if status is `PENDING`, `PROCESSING`, `DELIVERED`, or `DEAD_LETTERED`
- `409 Conflict` if no retries remain on a `FAILED` notification

### `GET /notifications/dead-letter`

Returns every notification currently in `DEAD_LETTERED` status as a JSON array — operational view for inspection and triage.

---

## Notification state machine

```
                 +---------+
                 | PENDING |<----------+
                 +----+----+           |
                      |                |
                      v                |
                +------------+         |
                | PROCESSING |         |
                +-----+------+         |
                      |                |
        +-------------+-------------+  |
        |                           |  |
        v                           v  |
  +-----------+                +--------+
  | DELIVERED |                | FAILED |
  +-----------+                +---+----+
   (terminal)                      |
                          retries  |  no retries
                          left     |  left
                                   |
                       +-----------+-----------+
                       |                       |
                       v                       v
                  (back to PENDING)     +---------------+
                                        | DEAD_LETTERED |
                                        +---------------+
                                          (terminal)
```

Allowed transitions (enforced by `NotificationStatus.canTransitionTo`):

| From         | To                      |
|--------------|-------------------------|
| `PENDING`    | `PROCESSING`            |
| `PROCESSING` | `DELIVERED`, `FAILED`   |
| `FAILED`     | `PENDING`, `DEAD_LETTERED` |
| `DELIVERED`  | (terminal)              |
| `DEAD_LETTERED` | (terminal)           |

`attempts` increments on the `PENDING -> PROCESSING` edge, so it always reflects the number of dispatches actually performed.

---

## Exponential backoff

Retries are spaced apart by `2^attempts` seconds:

| Attempt # | Wait before this attempt |
|-----------|--------------------------|
| 1         | 0 s (immediate)          |
| 2         | 2 s                      |
| 3         | 4 s                      |
| 4         | 8 s                      |
| 5         | 16 s                     |

Implemented in `SendNotificationUseCase.sleepBackoff`:

```java
var seconds = (long) Math.pow(2, attempts);
Thread.sleep(seconds * 1000L);
```

The flow on each failure:

1. `dispatch()` catches the sender exception and calls `fail(notification, error)`.
2. `fail()` writes `FAILED` with `lastError`.
3. If `attempts < maxAttempts`, it transitions back to `PENDING` and re-enqueues `processAsync` with the next attempt number.
4. `processAsync` sleeps for `2^attempts` seconds before re-dispatching.
5. If retries are exhausted, the notification is moved to `DEAD_LETTERED` and stays there.

---

## Dead Letter Queue

A notification that exhausts every retry without succeeding is transitioned to `DEAD_LETTERED` instead of being silently dropped or retried forever.

**Why it exists:**

- **Bounded blast radius** — a permanently broken integration cannot saturate the dispatch pool with infinite retries.
- **Observability** — dead-lettered records are queryable via `GET /notifications/dead-letter`, so operators can see what failed without grepping logs.
- **Recoverability** — the row keeps the original payload, every attempt count, and the last error message. Once the underlying issue is fixed, an external job (or a future admin endpoint) can replay them.

`DEAD_LETTERED` is a terminal status: the runtime never moves a record out of it on its own.

---

## Running locally

### Prerequisites

- JDK 21
- Docker (for PostgreSQL)

### Start the database

```bash
docker compose up -d
```

This starts `postgres:16-alpine` on `localhost:5432` with database `notifications` (user `notifications`, password `notifications`).

### Start the application

```bash
./gradlew bootRun
```

Or build and run the jar:

```bash
./gradlew bootJar
java -jar build/libs/notification-api-0.0.1-SNAPSHOT.jar
```

The service listens on `http://localhost:8080`. Flyway applies `V1__init.sql` on first boot.

### Override defaults

Standard Spring properties work as environment variables:

```bash
SERVER_PORT=8081 \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/notifications \
./gradlew bootRun
```

---

## Running the tests

### All tests

```bash
./gradlew test
```

### Unit tests only

```bash
./gradlew test --tests "com.example.notification.SenderStrategyTest" \
              --tests "com.example.notification.NotificationStatusTransitionsTest"
```

These do not require Docker — they exercise the domain and the strategy lookup in isolation.

### Integration tests

```bash
./gradlew test --tests "com.example.notification.NotificationFlowIntegrationTest"
```

`@Testcontainers` boots a fresh `postgres:16-alpine` container per test class and wires it to Spring via `@ServiceConnection`. Flyway migrates the schema, the database is truncated in `@BeforeEach`, and four end-to-end scenarios run:

1. Full delivery flow — `DELIVERED`, `attempts = 1`
2. Missing template — `404`, no rows persisted
3. Dead-letter after `maxAttempts` failures — `DEAD_LETTERED`, `attempts = maxAttempts`
4. Retry with backoff — fail twice, succeed on the third — `DELIVERED`, `attempts = 3`

---

## Technical decisions

### Why the Strategy pattern for senders

A `switch` on `channel` would put every channel's transport details in one place. Strategy keeps each sender in its own class, lets channels evolve independently (different SDKs, different timeouts, different retries), and makes adding a new channel a one-file change. `SenderRegistry` populates the lookup map from the Spring context, so registration is automatic.

### Why `@Async`

`POST /notifications` is a queueing operation, not a delivery operation. Returning `202 Accepted` immediately decouples the caller from third-party latency and rate limits, and lets the service apply backoff without holding HTTP threads. The notification is persisted before any dispatch happens, so durability does not depend on the executor surviving.

### Why exponential backoff

Constant-interval retries punish a degraded downstream just as hard as a healthy one. Doubling the wait — 2, 4, 8, 16 seconds — gives transient failures (a slow DNS, a brief 5xx, a token refresh) time to clear without storming the dependency, while still bounding the total time to dead-letter (under a minute at the default `maxAttempts = 5`).

### Why a Dead Letter Queue

Without a terminal failure state, a permanently broken integration produces either infinite retries (resource leak) or silent data loss (worse). `DEAD_LETTERED` makes failure explicit, observable through the API, and recoverable: the row keeps the original payload and the last error, ready to be replayed once the root cause is fixed.

### Why Testcontainers

H2 and other in-memory substitutes do not understand `JSONB`, native UUIDs, `TIMESTAMPTZ`, or Flyway's PostgreSQL dialect — all of which this service relies on. Testcontainers runs the same Postgres image used in production, against the same migrations, so a green integration test means the schema, the JPA mappings, and the JSON serialization all actually work together.

---

## Next steps

### Kafka as a persistent queue

Today the in-process `ThreadPoolTaskExecutor` is the queue: if the JVM dies between `POST /notifications` and the first dispatch, the row is in the database in `PENDING` but no executor is holding it any longer. Moving dispatch to Kafka (one topic per channel, consumer groups for horizontal scale) gives at-least-once delivery, replayability, and isolates a slow channel from the others.

### Real channel integrations

Replace the logging stubs in `EmailSender`, `SmsSender`, and `PushSender` with concrete clients:

- **Email** — Resend, AWS SES, or SMTP
- **SMS** — Twilio or AWS SNS
- **Push** — Firebase Cloud Messaging or APNs

Each integration should map its provider-specific errors onto retryable vs. non-retryable failure (a `400` from the provider should fast-track to `DEAD_LETTERED`; a `429` or `5xx` should retry).

### Recovery job for stuck `PROCESSING` records

If the JVM crashes mid-dispatch, a notification can stay in `PROCESSING` forever. A scheduled job (`@Scheduled`, or a separate worker) should sweep `PROCESSING` rows older than a threshold, transition them back to `PENDING`, and re-enqueue them — with the same `attempts` count, so the existing retry budget still applies.
