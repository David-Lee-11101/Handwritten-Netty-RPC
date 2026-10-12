# v3.1 formal attempt 1

This directory preserves the first formal run of the independently approved `tiny-rpc-metrology-v3.1-abba` method. It contains all 24 process records, stdout and stderr logs, the frozen identities, environment identity, structural report reference through the analysis, and a SHA-256 inventory.

The attempt completed all 12 alternating AB/BA pairs without a retry. The overall result is **FAIL** because the upper 95% bound for the p95 latency difference was 109,875 ns, above the frozen 76,020.5 ns non-inferiority margin. No sample was removed or replaced and no threshold was changed.

The following gates passed:

- Idle heap: upper 95% bound `-1,713,052` bytes
- Idle RSS: upper 95% bound `-17,776,640` bytes
- Startup: upper 95% bound `-47,978,688` ns
- Core artifact: `7,714 < 34,238` bytes
- Runtime dependency closure: `4,508,246 < 22,563,354` bytes
- Spring artifacts in the core runtime: `0`

The complete four-module distribution is 41,666 bytes. That quantity is reported separately from the core artifact and is not described as smaller than the legacy application artifacts.
