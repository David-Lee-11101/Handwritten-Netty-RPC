# Metrology v3.1 proposal

This executable proposal corrects one measurement asymmetry and leaves every earlier artifact unchanged. The first strict post-change run remains a real failed result under `post-attempt-1-*`: idle heap failed, while RSS, p95 latency, and the structural gates passed. Its startup comparison is not interpreted because the legacy v2 timer began after two owned Netty event-loop groups were created. That finding does not erase the heap failure or any raw sample.

The frozen identities are in [`v3/identities.json`](v3/identities.json). The legacy measurement commit `a630be38072d9a96ed70ac942883972f8cb16ed8` descends from the untouched production baseline `27779a51bfa38822922d3a57e365d2956b0546b0` and changes only `LegacyBenchmarkMain`. It keeps the original Netty 4.1.48 and Fastjson 1.2.58 POMs. The candidate is exactly `9fb450b9ddac79558f9946849712ee464530c8ee`. Full commits, harness hashes, POM hashes, runtime manifests, the candidate structure report, and coordinator script hashes are machine checked before a run.

Both timers begin as the first statement in `main`, before owned runtime groups are constructed. They stop only after the first readiness response is decoded and its exact echo value is validated. Every one of the 200 warm-up calls and 1,000 measured calls validates the returned value (`warmup-N` or `value-N`). The protocols have different envelopes, so this is a matched logical workload rather than equal wire bytes. The actual encoded request and response frame sizes for readiness, the first warm-up call, and the first measured call are captured and disclosed for each implementation.

[`v3/run-paired.sh`](v3/run-paired.sh) creates a new attempt directory and executes exactly 12 adjacent pairs in alternating AB/BA order. Each record carries pair index, planned order, actual sequence, implementation, full source commit, harness hash, environment identity, start/end timestamps, and the raw benchmark object. Per-process stdout and stderr remain in the attempt directory. A missing or failed process leaves an incomplete attempt for audit; it is never silently retried or overwritten.

[`v3/analyze.mjs`](v3/analyze.mjs) rejects missing, duplicate, reordered, cross-environment, or wrong-identity records. It computes fresh legacy summaries and the capped non-inferiority margins before accessing candidate performance values. It then bootstraps the 12 actual matched-pair differences with seed `20261012` and 10,000 resamples. Idle heap and RSS require an upper 95% bound below zero. Startup and p95 latency use the fresh legacy dispersion with the audited 84 ns clock bound, capped at the stricter v2 limits of 9,823,021.5 ns and 76,020.5 ns.

The structural limits remain numeric and unchanged: the core JAR must be below 34,238 bytes, the runtime-only dependency closure must be below 22,563,354 bytes, and the Spring-free core runtime may contain no Spring artifact. The core JAR, complete four-module distribution, and runtime dependency closure are reported separately. A small core JAR is not described as a reduction of the complete distribution.

Preparation is reproducible and does not collect samples:

```bash
V3_LEGACY_ROOT=/absolute/path/to/legacy \
V3_CANDIDATE_ROOT=/absolute/path/to/candidate \
BENCHMARK_JAVA_HOME=/absolute/path/to/jdk8 \
BENCHMARK_MAVEN=/absolute/path/to/mvn \
BENCHMARK_MAVEN_SETTINGS=/absolute/path/to/settings.xml \
BENCHMARK_NODE=/absolute/path/to/node \
benchmarks/v3/prepare-exact-roots.sh
```

No v3.1 sample may be collected until an independent reviewer approves this frozen method. No low-footprint, memory, startup, or latency claim is permitted unless all gates pass on the frozen candidate identity.
