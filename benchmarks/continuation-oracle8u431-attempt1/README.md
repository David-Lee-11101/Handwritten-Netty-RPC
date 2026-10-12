# Oracle 8u431 continuation cohort, attempt 1

This independently approved new cohort completed all 12 alternating AB/BA pairs without retries or replaced samples. The overall unchanged v3.1 gate **PASS** applies to measured Communication source commit `c2347e818c890feb88d9377ad5f7026bc0f65f8b` and recovered legacy measurement commit `8dccd145a9626c6596af9e067059cdca4c443232` on the recorded Apple M4 / Darwin 27 / Oracle Java 8u431 / Node 25 environment. It does not reproduce the historical Apple M3 Pro / Oracle 8u441 cohort, whose [failed attempt](../v3-attempt-1/README.md) remains unchanged.

| Gate | Fresh upper 95% bound | Required bound | Result |
| --- | ---: | ---: | --- |
| Idle heap difference | -1,851,840 bytes | < 0 | Pass |
| Idle RSS difference | -18,399,232 bytes | < 0 | Pass |
| Startup difference | -32,729,459 ns | ≤ 9,823,021.5 ns | Pass |
| p95 latency difference | +9,854.5 ns | ≤ 20,499.5 ns | Pass |

The latency margin was derived exclusively from this cohort's legacy samples using the frozen formula, capped at 76,020.5 ns. The result establishes latency non-inferiority within that margin; the confidence interval does **not** establish a latency improvement. The 84 ns clock floor was inherited from the frozen protocol; the separately recorded fresh diagnostic also observed a p99 positive clock delta of 84 ns, without changing any gate.

Core artifact size is 7,714 bytes; the complete four-module distribution is 42,958 bytes; the runtime dependency closure is 4,508,246 bytes. The core artifact is below the frozen 34,238-byte core gate and the closure below 22,563,354 bytes. The complete distribution is reported separately and is not claimed to be smaller than the legacy application artifacts. There are no Spring artifacts in the current core runtime.

The directory preserves sanitized derivatives of every stdout/stderr log (with original hashes and redaction receipt), all 24 records, environment and frozen identities, structural report, exact analysis, integrity verification, source/tool hashes, and the preapproved protocol addendum. Local private toolchain paths were redacted in the public preflight export; hashes and source identities were retained. Collection occurred in a parent-coordinated quiet window after source builds completed. These are application loopback measurements, not physical-device or packet-capture results. The original legacy implementation emitted Netty DEBUG startup diagnostics and per-response INFO logging; this behavior is part of the measured implementation. The comparison therefore measures these complete implementations and does not isolate transport cost from logging cost. Unredacted originals remain immutable in private evidence; the public derivatives remove local temporary paths and machine identifiers without changing metrics or the recorded logging behavior.

## Reconstruct the recovered legacy measurement identity

The historical measurement commit was unavailable from the remote. Its production base and exact benchmark harness were recovered. `legacy-harness.patch` changes only that harness relative to preserved production base `27779a51bfa38822922d3a57e365d2956b0546b0`; the harness SHA-256 matches the historical frozen identity. In a clean detached checkout of that base, apply the patch, add the changed harness, and verify `git write-tree` matches the tree in `legacy-measurement-commit.txt`. Then `git hash-object -t commit -w legacy-measurement-commit.txt` reconstructs the exact `8dccd145a9626c6596af9e067059cdca4c443232` measurement identity. This retains truthful provenance without representing the new commit as the unavailable historical one.

## Reproduce the analysis

From the repository root:

```bash
node benchmarks/v3/analyze.mjs \
  benchmarks/continuation-oracle8u431-attempt1/records.jsonl \
  benchmarks/continuation-oracle8u431-attempt1/identities.json \
  benchmarks/continuation-oracle8u431-attempt1/structure.json \
  benchmarks/v3/attempt-lineage.json \
  /tmp/continuation-analysis.json
```

The analyzer creates a new output file and rejects incomplete, reordered, duplicated, incorrectly identified, or cross-environment records. The exact source remains available at the measured commit above. This archive describes that source only. A subsequent exact LF/CRLF frame-cap repair changes the runtime and requires its own new candidate identity and complete attempt; this earlier PASS cannot attest later implementation changes.
