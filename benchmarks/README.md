# Communication benchmark protocol

The benchmark compares the untouched legacy JSON-line implementation with the Spring-free TinyRPC runtime on the same machine and fixed Java runtime.

- Warm-up calls per process: 200
- Measured loopback calls per process: 1,000
- Independent process runs: 12 (minimum accepted: 10)
- Java flags: `-Xms64m -Xmx256m -XX:+UseSerialGC`
- Uncertainty: fixed-seed (`20261012`) 95% percentile bootstrap over matched-run median differences
- Memory pass: upper confidence bound of `(new - baseline)` is below zero
- Startup/latency pass: upper confidence bound of `(new - baseline)` is within the frozen baseline-derived non-inferiority margin

The acceptance envelope is frozen in `benchmarks/acceptance-envelope.json` before runtime optimization. Results describe this loopback benchmark on the recorded host; they are not universal hardware claims.

