# Oracle 8u431 continuation cohort, attempt 3

Overall **PASS** for exact candidate `83ffe6d77bf4cd249db863f4df1e9597938b59ad` against recovered legacy `8dccd145a9626c6596af9e067059cdca4c443232`. All 12 alternating AB/BA pairs completed without retries or replacement samples on the recorded Apple M4 / Darwin 27 / Oracle Java 8u431 / Node 25 environment. This result applies only to that source and environment.

| Gate | New-minus-legacy upper 95% bound | Frozen adaptive bound |
| --- | ---: | ---: |
| Idle heap | -1,786,088 B | < 0 |
| Idle RSS | -18,137,088 B | < 0 |
| Startup | -32,936,083.5 ns | ≤ 9,823,021.5 ns |
| p95 latency | -1,855 ns | ≤ 29,020.5 ns |

The latency confidence interval is entirely below zero in this recorded cohort. This is environment-specific evidence and includes the existing runtime logging differences; it does not establish universal transport superiority. Throughput superiority is not claimed. The frozen method derives startup and latency bounds exclusively from fresh legacy dispersion, with unchanged caps of 9,823,021.5 ns and 76,020.5 ns and inherited 84 ns floor. Candidate values never set those bounds.

Core artifact: **8,004 B**; complete four-module distribution: **43,252 B**; runtime dependency closure: **4,508,536 B**. Core and closure satisfy their unchanged 34,238 B and 22,563,354 B exclusive caps, with zero Spring runtime artifacts. The complete distribution is reported separately and is not claimed smaller than legacy application artifacts.

This directory preserves all 24 metric records, environment and frozen identities, analysis, structure, approved protocol addendum, source/tool hashes, and sanitized derivatives of all 48 stdout/stderr logs. The redaction receipt binds every published derivative to its immutable private original; only local temporary paths and machine identifiers were redacted. Original integrity hashes describe private originals, not the changed derivative bytes. Existing legacy Netty DEBUG and per-response INFO logging is part of the measured implementation; the comparison does not isolate transport cost from logging cost. No physical-device or packet-capture performance is claimed.

Historical Java 8u441 failure and [continuation attempt 1](../continuation-oracle8u431-attempt1/README.md) remain unchanged. [Attempt 2](../continuation-oracle8u431-attempt2/README.md) failed its latency gate. This new candidate is a tested source optimization, not a replacement of those samples. The later evidence-only publication must attest unchanged production/test Java and POM files against the measured source above.

## Reproduce analysis

```bash
node benchmarks/v3/analyze.mjs \
  benchmarks/continuation-oracle8u431-attempt3/records.jsonl \
  benchmarks/continuation-oracle8u431-attempt3/identities.json \
  benchmarks/continuation-oracle8u431-attempt3/structure.json \
  benchmarks/v3/attempt-lineage.json \
  /tmp/continuation-attempt3-analysis.json
```

The frozen analyzer creates a new output file. Source/harness reconstruction for legacy is documented in the preserved attempt 1 archive.
