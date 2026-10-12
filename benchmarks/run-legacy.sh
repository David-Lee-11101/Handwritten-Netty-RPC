#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "$0")/.." && pwd)"
output_file="${1:-$repository_root/benchmarks/baseline-runs.jsonl}"
maven_settings="${MAVEN_SETTINGS:?Set MAVEN_SETTINGS to the clean Maven settings file}"
java_home_override="${BENCHMARK_JAVA_HOME:?Set BENCHMARK_JAVA_HOME to the fixed JDK 8 home}"
run_count="${BENCHMARK_RUNS:-12}"

if [[ "$run_count" -lt 10 ]]; then
  echo "BENCHMARK_RUNS must be at least 10" >&2
  exit 2
fi

cd "$repository_root"
export JAVA_HOME="$java_home_override"
export PATH="$JAVA_HOME/bin:/usr/bin:/bin:/usr/sbin:/sbin"

/usr/local/apache-maven-3.9.9/bin/mvn -q -s "$maven_settings" -DskipTests test-compile \
  dependency:build-classpath -Dmdep.includeScope=test \
  -Dmdep.outputFile="$repository_root/Consumer/target/benchmark-classpath.txt" -pl Consumer -am

classpath="$repository_root/Consumer/target/test-classes:$repository_root/Consumer/target/classes:$(tr -d '\r\n' < "$repository_root/Consumer/target/benchmark-classpath.txt")"
temporary_file="$(mktemp "${TMPDIR:-/tmp}/tiny-rpc-baseline.XXXXXX")"
trap 'rm -f "$temporary_file"' EXIT
: > "$temporary_file"

for run in $(seq 1 "$run_count"); do
  result="$($JAVA_HOME/bin/java -Xms64m -Xmx256m -XX:+UseSerialGC -Dorg.slf4j.simpleLogger.defaultLogLevel=warn \
    -cp "$classpath" com.seclab.nettyClient.LegacyBenchmarkMain | sed -n 's/^BENCHMARK_JSON=//p')"
  if [[ -z "$result" ]]; then
    echo "Benchmark run $run did not emit a result" >&2
    exit 3
  fi
  printf '%s\n' "$result" >> "$temporary_file"
done

mv "$temporary_file" "$output_file"
trap - EXIT
printf 'Wrote %s legacy benchmark runs to %s\n' "$run_count" "$output_file"

