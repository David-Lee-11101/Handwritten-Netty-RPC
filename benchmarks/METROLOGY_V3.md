# Metrology v3 proposal

This proposal corrects one measured asymmetry and leaves every earlier artifact unchanged. The first strict post-change run is a real failed result: heap and startup did not pass v2, while RSS, p95 latency, core size, runtime closure, and the Spring-free check passed. That result remains under `post-attempt-1-*`.

The startup comparison was not scope-equivalent. `LegacyBenchmarkMain` created two implementation-owned Netty event-loop groups before `processStart`; `TinyRpcBenchmarkMain` started its clock before constructing the corresponding owned runtime. The corrected harness starts both clocks as the first statement in `main`, before all owned runtime groups, and ends both after the first validated readiness RPC. This changes measurement code only. The untouched legacy production source remains the public source-equivalent baseline at `27779a5`.

The machine-readable protocol is frozen in [`metrology-v3-proposal.json`](metrology-v3-proposal.json) before collecting a new legacy baseline or candidate result. Twelve fresh processes per implementation run as adjacent pairs in one coordinated quiet window, with AB/BA order alternating by pair. Both sides use the same Java 8 build, JVM flags, logical payload sequence, warm-up count, memory sampling point, measured call count, and clock.

The correction cannot relax the original standard. Idle heap and RSS still require the upper 95% bootstrap bound of candidate minus legacy to be below zero. Each non-inferiority margin is derived from the fresh untouched legacy dispersion and the audited 84 ns clock bound, then capped at the stricter v2 margin: 9,823,021.5 ns for startup and 76,020.5 ns for p95 latency. Runtime dependencies are collected with `includeScope=runtime`; JUnit and Hamcrest remain excluded. Core JAR, full four-module distribution, and runtime closure are reported as different quantities.

No v3 baseline or post-change sample may be collected until an independent reviewer accepts this method. An emitted but unfavorable run cannot be discarded or replaced. A process failure invalidates the window and is reported rather than silently retried.
