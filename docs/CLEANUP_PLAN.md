# Communication runtime cleanup plan

This plan freezes the behavior that must survive the runtime split. It precedes implementation changes.

## Behavior locks

- Preserve the existing UTF-8 JSON-line request and response shape as an explicit legacy protocol.
- Preserve request correlation, bounded timeout, reconnect-after-disconnect, duplicate-response tolerance, and deterministic shutdown.
- Keep malformed input and one channel failure scoped to that connection and its pending requests.
- Reject unregistered services and methods before reflection.

The existing `RpcLoopbackIntegrationTest`, `ClientPromiseTest`, and logging tests are the initial regression lock. New adversarial tests will cover frame, pending-request, queued-write, dispatch, cancellation, and event-loop boundaries before the legacy implementation is retired.

## Smell-focused passes

1. Capture the untouched implementation's dependency closure, artifact bytes, startup, idle heap/RSS, loopback latency, and throughput. Freeze the acceptance envelope before runtime optimization.
2. Introduce Spring-free `tiny-rpc-core`, `tiny-rpc-codec`, `tiny-rpc-netty`, and `tiny-rpc-telemetry` modules. Keep Spring integration optional.
3. Move process-global request and connection state behind owned client/server lifecycles.
4. Replace arbitrary class loading with an allowlisted service registry and run provider callbacks away from Netty event loops.
5. Add payload-free telemetry at the actual encoded-frame boundary.
6. Retain the legacy adapter and examples, then remove duplicated runtime paths only after regression and benchmark gates pass.

## Dependency rule

The new core runtime may use only the JDK. The Netty transport and JSON codec reuse the repository's existing dependency families and remain Java 8 compatible. No new third-party library is introduced for this cleanup.

