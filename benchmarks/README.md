# Communication benchmark protocol

The benchmark compares the legacy JSON-line implementation with the Spring-free TinyRPC runtime on the same machine and fixed Java runtime. The executable v3.1 protocol is frozen in [`METROLOGY_V3.md`](METROLOGY_V3.md). Its first formal attempt is preserved in [`v3-attempt-1`](v3-attempt-1/README.md): all 12 pairs completed, but the overall gate **failed** because the p95 latency upper confidence bound was 109,875 ns, exceeding the unchanged 76,020.5 ns margin. Memory, RSS, startup, and structural gates passed for that measured candidate. The [Oracle 8u431 continuation cohort](continuation-oracle8u431-attempt1/README.md) subsequently passed the same executable method and unchanged gates on a separately recorded host/runtime for exact source `c2347e818c890feb88d9377ad5f7026bc0f65f8b`. Historical failure remains preserved; the fresh evidence establishes latency non-inferiority and resource improvement only within its recorded environment.

- Warm-up calls per process: 200
- Measured loopback calls per process: 1,000
- Matched fresh-process pairs: exactly 12, alternating legacy-candidate and candidate-legacy
- Java flags: `-Xms64m -Xmx256m -XX:+UseSerialGC`
- Uncertainty: fixed-seed (`20261012`) 95% percentile bootstrap over the 12 matched-pair median differences
- Memory pass: upper confidence bound of `(new - baseline)` is below zero
- Startup/latency pass: upper confidence bound of `(new - baseline)` is within the frozen baseline-derived non-inferiority margin

The v2 envelopes and failed first post-change result remain immutable audit evidence. The v3.1 identities, POMs, dependency manifests, harnesses, coordinator scripts, raw order, and candidate structure report are frozen separately. Results describe this loopback benchmark on the recorded host; they are not universal hardware claims.

The structural report keeps three quantities distinct: the JDK-only core JAR, the sum of the four Spring-free TinyRPC JARs, and the full runtime dependency closure. The frozen artifact gate compares the new core JAR with the combined legacy application JARs; it does not present the core-only result as the size of the complete distribution.

