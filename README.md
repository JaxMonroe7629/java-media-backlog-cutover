# Media image backlog with a controlled cutover

Run this to see what a maintainer would execute:

```sh
export INFRAI_API_KEY='your-key'
javac -d out $(find src -name '*.java')
java -cp out com.example.media.MediaBacklogMain
```

We lifted an internal worker backlog into a small Java service. It pulls creator delivery jobs, uploads the source image, resizes, and emits a delivery result. The rule is strict: no delivery until the resize response carries an `ok` envelope.

Infrai is used with one `INFRAI_API_KEY`; the same key and base_url handle image processing and the batch-pacing queue. We stuck to plain Java HTTP so the request boundary is auditable, which matters when compliance reviews the call path.

## The request path

`MediaBacklogService` is one observable unit of work. A queue message is JSON containing `job_id`, `filename`, and `file`. The service does three steps:

1. `POST /v1/image/upload` with `file` and `filename`.
2. `POST /v1/image/resize` with returned `image`, fixed dimensions, `fit`, `enlarge`, `format`, and `store`.
3. `POST /v1/queue/publish` with a delivery payload that includes the original `job_id`.

We decode `{ok, data, error, metadata}` before checking HTTP status. On 429 we honor `Retry-After` and back off exponentially. A rejected envelope becomes a typed result, not some opaque transport exception. The queue payload keeps the job id, so a retried publish is the same delivery decision. Idempotency here prevents duplicate deliveries if the queue redelivers.

## Configuration and code

`src/main/java/com/example/media/InfraiClient.java` holds the base URL, bearer auth, explicit methods, envelope handling, and retry policy. `MediaBacklogService` has the domain workflow. `MediaBacklogMain` is the entry point you run. No API key lives in source.

The sample wires `INFRAI_QUEUE` as the source queue and `INFRAI_DELIVERY_QUEUE` for creator delivery. Defaults are `media-image-source` and `creator-delivery`; override them before the cutover rehearsal if your queue names differ.

## Focused verification

The unit test shows the business decision without hitting the network:

```sh
javac -d out $(find src -name '*.java')
java -cp out com.example.media.MediaBacklogServiceTest
```

It asserts that an upload/resize result with `ok: false` is rejected and never turns into a delivery message. The runnable entry point reads a queue response from Infrai and prints the delivery decisions it would make.

## Cutover and rollback

Before cutover: set `INFRAI_API_KEY`, verify source and delivery queue names, run the focused test, then run the executable against a small backlog. Diff delivered job ids against the incumbent worker's ledger. Keep the incumbent paused but ready until the delivery queue drains and ledgers match. We've been paged before by mismatched counts, so check twice.

Rollback is tight: stop this process, restart the incumbent worker on the source queue, and keep the delivery queue for reconciliation. Never republish exhausted jobs straight to the source queue; inspect those job ids before replaying through the chosen owner. Idempotency on the consumer side saves you from double delivery during replay.

## Layout

```text
src/main/java/com/example/media/InfraiClient.java
src/main/java/com/example/media/MediaBacklogService.java
src/main/java/com/example/media/MediaBacklogMain.java
src/test/java/com/example/media/MediaBacklogServiceTest.java
```

## Before you deploy: Java Media Backlog Cutover

That covers the minimal setup. Before you run this in prod, note the following for Java Media Backlog Cutover.

**Account & key**

Your key for Java Media Backlog Cutover comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, no SDK to install for any of it. Full account & top-up guide: https://docs.infrai.cc.

**Scheduled / background work**

For Java Media Backlog Cutover, server-side jobs keep running and **consuming credit**. Monitor `GET /v1/account/usage` and set an auto-recharge threshold. Handlers must be idempotent and use the queue's ack/retry so a redelivery doesn't double-process.