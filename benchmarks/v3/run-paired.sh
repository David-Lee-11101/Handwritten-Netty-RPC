#!/usr/bin/env bash
set -euo pipefail

coordinator_root="$(cd "$(dirname "$0")/../.." && pwd)"
identities="${V3_IDENTITIES:?Set V3_IDENTITIES to the frozen identity manifest}"
legacy_root="${V3_LEGACY_ROOT:?Set V3_LEGACY_ROOT to the exact detached legacy worktree}"
candidate_root="${V3_CANDIDATE_ROOT:?Set V3_CANDIDATE_ROOT to the exact detached candidate worktree}"
attempt_dir="${V3_ATTEMPT_DIR:?Set V3_ATTEMPT_DIR to a new evidence directory}"
java_home_override="${BENCHMARK_JAVA_HOME:?Set BENCHMARK_JAVA_HOME to Oracle Java 8u441}"
node_binary="${BENCHMARK_NODE:?Set BENCHMARK_NODE to the fixed Node executable}"

mkdir "$attempt_dir"
records="$attempt_dir/records.jsonl"
: > "$records"
cp "$identities" "$attempt_dir/identities.json"
"$node_binary" "$coordinator_root/benchmarks/v3/verify-identities.mjs" "$identities" legacy "$legacy_root"
"$node_binary" "$coordinator_root/benchmarks/v3/verify-identities.mjs" "$identities" candidate "$candidate_root"
identities_sha="$(shasum -a 256 "$identities" | awk '{print $1}')"
java_version="$($java_home_override/bin/java -version 2>&1 | tr '\n' ' ')"
"$node_binary" "$coordinator_root/benchmarks/v3/environment.mjs" \
  "$attempt_dir/environment.json" "$java_version" "$identities_sha"
environment_id="$($node_binary -e "process.stdout.write(JSON.parse(require('fs').readFileSync(process.argv[1])).environmentId)" "$attempt_dir/environment.json")"

legacy_commit="$($node_binary -e "process.stdout.write(JSON.parse(require('fs').readFileSync(process.argv[1])).legacy.commit)" "$identities")"
candidate_commit="$($node_binary -e "process.stdout.write(JSON.parse(require('fs').readFileSync(process.argv[1])).candidate.commit)" "$identities")"
legacy_harness="$($node_binary -e "process.stdout.write(JSON.parse(require('fs').readFileSync(process.argv[1])).legacy.harness.sha256)" "$identities")"
candidate_harness="$($node_binary -e "process.stdout.write(JSON.parse(require('fs').readFileSync(process.argv[1])).candidate.harness.sha256)" "$identities")"
legacy_classpath="$legacy_root/Consumer/target/test-classes:$legacy_root/Consumer/target/classes:$(tr -d '\r\n' < "$legacy_root/Consumer/target/v3-classpath.txt")"
candidate_classpath="$candidate_root/tiny-rpc-netty/target/test-classes:$candidate_root/tiny-rpc-netty/target/classes:$(tr -d '\r\n' < "$candidate_root/tiny-rpc-netty/target/v3-classpath.txt")"

sequence=0
run_one() {
  local pair="$1" order="$2" implementation="$3" classpath="$4" main_class="$5" commit="$6" harness="$7"
  sequence=$((sequence + 1))
  local started ended raw stdout_file stderr_file result_lines
  stdout_file="$attempt_dir/run-$(printf '%02d' "$sequence")-$implementation.stdout.log"
  stderr_file="$attempt_dir/run-$(printf '%02d' "$sequence")-$implementation.stderr.log"
  started="$($node_binary -e 'process.stdout.write(String(Date.now()))')"
  if ! "$java_home_override/bin/java" -Xms64m -Xmx256m -XX:+UseSerialGC \
    -Dorg.slf4j.simpleLogger.defaultLogLevel=warn -cp "$classpath" "$main_class" >"$stdout_file" 2>"$stderr_file"; then
    printf 'process failed for pair %s %s; logs retained in %s\n' "$pair" "$implementation" "$attempt_dir" >&2
    exit 3
  fi
  ended="$($node_binary -e 'process.stdout.write(String(Date.now()))')"
  result_lines="$(sed -n 's/^BENCHMARK_JSON=//p' "$stdout_file")"
  if [[ "$(printf '%s\n' "$result_lines" | awk 'NF { count++ } END { print count + 0 }')" != 1 ]]; then
    printf 'expected one result for pair %s %s; logs retained in %s\n' "$pair" "$implementation" "$attempt_dir" >&2
    exit 3
  fi
  raw="$result_lines"
  "$node_binary" "$coordinator_root/benchmarks/v3/record-run.mjs" "$records" "$pair" "$order" "$sequence" \
    "$implementation" "$commit" "$harness" "$environment_id" "$started" "$ended" "$raw"
}

for pair in $(seq 1 12); do
  if (( pair % 2 == 1 )); then
    order="legacy-candidate"
    run_one "$pair" "$order" legacy "$legacy_classpath" com.seclab.nettyClient.LegacyBenchmarkMain "$legacy_commit" "$legacy_harness"
    run_one "$pair" "$order" candidate "$candidate_classpath" io.smarthome.tinyrpc.netty.TinyRpcBenchmarkMain "$candidate_commit" "$candidate_harness"
  else
    order="candidate-legacy"
    run_one "$pair" "$order" candidate "$candidate_classpath" io.smarthome.tinyrpc.netty.TinyRpcBenchmarkMain "$candidate_commit" "$candidate_harness"
    run_one "$pair" "$order" legacy "$legacy_classpath" com.seclab.nettyClient.LegacyBenchmarkMain "$legacy_commit" "$legacy_harness"
  fi
done
printf 'Wrote 12 complete AB/BA pairs to %s\n' "$records"
