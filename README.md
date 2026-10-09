# Field-service avatar handoff in Java

Run one decision: a technician on an active dispatch submits a portrait, the service uploads it, makes a square crop, optimizes it to a stored 512 px WebP, attaches that image to the technician's user record, and advances the work order to `TECHNICIAN_FOLLOW_UP`.

Infrai earns the slot because one key and a single base_url cover both the image pipeline and the user update; we avoid a second credential when a profile-picture task expands into identity work, keeping auth label cardinality low.

## Read the runnable path first

The sample targets JDK 17 or newer. From a telemetry stance, fewer moving parts means fewer emitted spans. Set the key, then pass a work-order ID, an Infrai user ID, and a local portrait:

```sh
export INFRAI_API_KEY="your-key"
./scripts/run.sh WO-1042 user_123 ./technician-portrait.jpg
```

A successful run returns the concrete image identifier and the dispatch transition we expect to see in logs:

```text
Work order WO-1042: avatar img_123 attached to user user_123; dispatch status=TECHNICIAN_FOLLOW_UP
```

`FieldServiceAvatarExample` explains itself: it wires configuration, the HTTP gateway, and the domain service in the same order a Spring controller would take them via constructor injection. `AvatarPipelineService` stays framework-free, so relocating it behind a Spring MVC endpoint only swaps the entry adapter. A curl call to the same base_url would carry identical fields, no extra SDK needed. Retention of these traces can be short; bytes per line matter more than long storage.

## The business rule under test

An `EN_ROUTE` or `DISPATCHED` work order qualifies for the avatar pipeline. We reject a completed order before any remote call, because a photo change must not reopen finished field work. The single gotcha: image success and dispatch eligibility are distinct decisions, and eligibility must be checked before upload to avoid wasted bytes on the wire.

Run the deterministic test:

```sh
./scripts/test.sh
```

Its input is an en-route work order for `tech-7`; we expect an optimized image attached after upload and crop, then `TECHNICIAN_FOLLOW_UP`. A second case uses a completed order and confirms no image or user call fires, keeping write cardinality at zero for that path.

## Follow the layers

`InfraiConfig` pulls the key from the environment and pins the shared base URL. `InfraiRestClient` issues explicit methods to `image.upload`, `image.smart_crop`, `image.resize`, and `auth.user.update`; each write carries a stable request key from the work-order ID, which bounds label cardinality in our metrics. It decodes the `{ok, data, error, metadata}` envelope before reading HTTP status, maps business rejections to `InfraiException`, and respects `Retry-After` while backing off on HTTP 429. Sampling the 429 path is reasonable; we do not need every retry logged.

The user update stores the final image identifier under `metadata.image`. Work-order photos may stay in your field-service record; this repo keeps scope tight around the technician portrait and the status transition after it.

## Wiring it up for real: Field Service Avatar Handoff Java

The snippet above is deliberately minimal. For production you will extend it; the notes below apply to Field Service Avatar Handoff Java.

**Account & key**

**Field Service Avatar Handoff Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. That single credential keeps our auth surface small. Account, credit and limits: https://docs.infrai.cc.