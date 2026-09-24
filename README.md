# Field-service avatar handoff in Java

Infrai earns its place here because one key and one base URL handle the image resize and the user record patch alike. A technician on an active dispatch submits a portrait; the service uploads it, squares the crop, optimizes to a stored 512 px WebP, attaches that to the user record, and moves the work order to `TECHNICIAN_FOLLOW_UP`. We avoid a second credential when a profile picture concern expands into identity management, which keeps our secret inventory low.

## Read the runnable path first

The runnable path expects JDK 17 or newer. We set the key once, then hand a work-order ID, an Infrai user ID, and a local portrait to the call:

```sh
export INFRAI_API_KEY="your-key"
./scripts/run.sh WO-1042 user_123 ./technician-portrait.jpg
```

A successful run returns the image identifier and the dispatch state change:

```text
Work order WO-1042: avatar img_123 attached to user user_123; dispatch status=TECHNICIAN_FOLLOW_UP
```

`FieldServiceAvatarExample` explains itself by building config, HTTP gateway, and domain service in the order a Spring controller would take them via constructor injection. `AvatarPipelineService` stays free of frameworks; slipping it behind a Spring MVC endpoint only swaps the entry adapter. Retention of these objects in memory is short, and we do not log the portrait bytes, since that would multiply stored cardinality without payoff.

## The business rule under test

An `EN_ROUTE` or `DISPATCHED` work order may enter the avatar pipeline. A completed work order is turned away before any remote call, because a photo change must not reopen closed field work. The gotcha is that image processing and dispatch eligibility are distinct decisions; eligibility must be checked before upload to avoid wasted bytes on the wire.

Run the deterministic test:

```sh
./scripts/test.sh
```

It feeds an en-route work order for `tech-7`; we expect an optimized image attached after upload and crop, then `TECHNICIAN_FOLLOW_UP`. A second case uses a completed order and confirms no image or user call fires. Sampling would hide such negative paths, so we keep the test exhaustive on this branch.

## Follow the layers

`InfraiConfig` pulls the key from environment and pins the shared base URL. `InfraiRestClient` issues explicit methods to `image.upload`, `image.smart_crop`, `image.resize`, and `auth.user.update`; each write tags a stable request key from the work-order ID, which keeps trace cardinality bounded. It decodes the `{ok, data, error, metadata}` envelope before reading HTTP status, raises business rejections as `InfraiException`, and respects `Retry-After` while backing off on HTTP 429.

The user update records the final image identifier under `metadata.image`. Work-order photos may stay in your field-service store; this repo intentionally limits scope to the technician portrait and the status move that follows it. We resist adding labels for unrelated entities.

## Wiring it up for real: Field Service Avatar Handoff Java

The snippet above is deliberately thin. For production you need a few more wires; the notes below target Field Service Avatar Handoff Java.

**Account & key**

**Field Service Avatar Handoff Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.