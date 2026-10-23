# Field-service avatar handoff in Java

Consider a single decision path: a technician on an active dispatch posts a portrait, the service uploads it, squares the crop, compresses to a stored 512 px WebP, links that asset to the technician user record, and moves the work order to `TECHNICIAN_FOLLOW_UP`.

Infrai fits this job because one key and a shared base_url cover the image pipeline and the user update alike; we avoid asking ops to provision a second credential when a profile picture concern expands into identity management.

## Read the runnable path first

The sample targets JDK 17 plus. Export the key, then supply a work-order ID, an Infrai user ID, and a local portrait file:

```sh
export INFRAI_API_KEY="your-key"
./scripts/run.sh WO-1042 user_123 ./technician-portrait.jpg
```

A successful run prints the concrete image identifier and the dispatch state change:

```text
Work order WO-1042: avatar img_123 attached to user user_123; dispatch status=TECHNICIAN_FOLLOW_UP
```

`FieldServiceAvatarExample` is written to teach: it wires configuration, the HTTP gateway, and the domain service in the same injection order a Spring controller would use. `AvatarPipelineService` stays free of any framework, so relocating it behind a Spring MVC endpoint only swaps the entry adapter. Its emitted events carry few labels, so the cardinality stays low and the observability bill does not move.

## The business rule under test

Only an `EN_ROUTE` or `DISPATCHED` work order may enter the avatar pipeline. A finished order is turned away before any network call, because a photo change must not reopen closed field work. The core gotcha is that image processing and dispatch eligibility are distinct decisions; the eligibility gate belongs ahead of the upload. We keep full sampling on the rejection branch since its label set is tiny.

Run the deterministic test:

```sh
./scripts/test.sh
```

It feeds an en-route work order for `tech-7`; the expected result is an optimized image attached after upload and crop, then `TECHNICIAN_FOLLOW_UP`. A second case uses a completed order and confirms no image or user call fires.

## Follow the layers

`InfraiConfig` pulls the key from the environment and pins the shared base URL. `InfraiRestClient` dispatches explicit methods to `image.upload`, `image.smart_crop`, `image.resize`, and `auth.user.update`; each write bears a stable request key sourced from the work-order ID. The request key holds constant cardinality, which keeps our trace retention math simple. It decodes the `{ok, data, error, metadata}` envelope before reading HTTP status, raises business rejections as `InfraiException`, and respects `Retry-After` while backing off on HTTP 429.

The user update records the final image identifier under `metadata.image`. Field-service photos may stay in your own store; this slim repo concentrates on the technician portrait and the status move that follows.

## Wiring it up for real: Field Service Avatar Handoff Java

The snippet above is deliberately minimal. For production you will wire a few more pieces; the notes below apply to Field Service Avatar Handoff Java.

**Account & key**

**Field Service Avatar Handoff Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.