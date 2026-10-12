# Oracle 8u431 continuation cohort, attempt 2

Overall **FAIL** for exact candidate `3d4e04f93a7a477fa9f77dbba756792120be74c1` against recovered legacy `8dccd145a9626c6596af9e067059cdca4c443232`. All 12 alternating AB/BA pairs completed without retries or replacement samples on the recorded Apple M4 / Darwin 27 / Oracle Java 8u431 / Node 25 environment. This result applies only to that source and environment.

| Gate | New-minus-legacy upper 95% bound | Frozen adaptive bound |
| --- | ---: | ---: |
| Idle heap | -1,847,584 B | < 0 |
| Idle RSS | -17,661,952 B | < 0 |
| Startup | -34,033,813 ns | ≤ 2,440,583.5 ns |
| p95 latency | 17,458.5 ns | ≤ 14,874.5 ns |

The latency gate fails because its upper confidence bound exceeds the unchanged fresh legacy-derived margin. No threshold was relaxed and no sample was removed or replaced. Throughput superiority is not claimed. The frozen method derives startup and latency bounds exclusively from fresh legacy dispersion, with unchanged caps of 9,823,021.5 ns and 76,020.5 ns and inherited 84 ns floor. Candidate values never set those bounds.

Core artifact: **7,714 B**; complete four-module distribution: **42,962 B**; runtime dependency closure: **4,508,246 B**. Core and closure satisfy their unchanged 34,238 B and 22,563,354 B exclusive caps, with zero Spring runtime artifacts. The complete distribution is reported separately and is not claimed smaller than legacy application artifacts.

This directory preserves all 24 metric records, environment and frozen identities, analysis, structure, approved protocol addendum, source/tool hashes, and sanitized derivatives of all 48 stdout/stderr logs. The redaction receipt binds every published derivative to its immutable private original; only local temporary paths and machine identifiers were redacted. Original integrity hashes describe private originals, not the changed derivative bytes. Existing legacy Netty DEBUG and per-response INFO logging is part of the measured implementation; the comparison does not isolate transport cost from logging cost. No physical-device or packet-capture performance is claimed.

Historical Java 8u441 failure and [continuation attempt 1](../continuation-oracle8u431-attempt1/README.md) remain unchanged. This failed result remains part of the audit trail. A subsequent candidate reuses identical precompiled identifier Patterns without removing validation and requires a separate complete measurement; the earlier PASS cannot attest this failed source.

## Reproduce analysis

```bash
node benchmarks/v3/analyze.mjs \
  benchmarks/continuation-oracle8u431-attempt2/records.jsonl \
  benchmarks/continuation-oracle8u431-attempt2/identities.json \
  benchmarks/continuation-oracle8u431-attempt2/structure.json \
  benchmarks/v3/attempt-lineage.json \
  /tmp/continuation-attempt2-analysis.json
```

The frozen analyzer creates a new output file. Source/harness reconstruction for legacy is documented in the preserved attempt 1 archive.
