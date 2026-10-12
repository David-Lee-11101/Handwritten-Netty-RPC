# Communication benchmark protocol

The benchmark compares the legacy JSON-line implementation with the Spring-free TinyRPC runtime on the same machine and fixed Java runtime. The executable v3.1 protocol is frozen in [`METROLOGY_V3.md`](METROLOGY_V3.md). Its first formal attempt is preserved in [`v3-attempt-1`](v3-attempt-1/README.md): all 12 pairs completed, but the overall gate **failed** because the p95 latency upper confidence bound was 109,875 ns, exceeding the unchanged 76,020.5 ns margin. Memory, RSS, startup, and structural gates passed for that measured candidate. The [final Oracle 8u431 continuation cohort](continuation-oracle8u431-attempt3/README.md) passes all unchanged gates for exact source `83ffe6d77bf4cd249db863f4df1e9597938b59ad` after a tested fixed-Pattern optimization. The [intermediate failure](continuation-oracle8u431-attempt2/README.md) and all earlier evidence remain preserved. Resource improvement and latency non-inferiority apply only to the recorded environment and include existing logging behavior; no throughput superiority is claimed.

- Warm-up calls per process: 200
- Measured loopback calls per process: 1,000
- Matched fresh-process pairs: exactly 12, alternating legacy-candidate and candidate-legacy
- Java flags: `-Xms64m -Xmx256m -XX:+UseSerialGC`
- Uncertainty: fixed-seed (`20261012`) 95% percentile bootstrap over the 12 matched-pair median differences
- Memory pass: upper confidence bound of `(new - baseline)` is below zero
- Startup/latency pass: upper confidence bound of `(new - baseline)` is within the frozen baseline-derived non-inferiority margin

The v2 envelopes and failed first post-change result remain immutable audit evidence. The v3.1 identities, POMs, dependency manifests, harnesses, coordinator scripts, raw order, and candidate structure report are frozen separately. Results describe this loopback benchmark on the recorded host; they are not universal hardware claims.

The structural report keeps three quantities distinct: the JDK-only core JAR, the sum of the four Spring-free TinyRPC JARs, and the full runtime dependency closure. The frozen artifact gate compares the new core JAR with the combined legacy application JARs; it does not present the core-only result as the size of the complete distribution.

