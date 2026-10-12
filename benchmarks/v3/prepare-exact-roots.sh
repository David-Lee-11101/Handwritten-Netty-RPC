#!/usr/bin/env bash
set -euo pipefail

coordinator_root="$(cd "$(dirname "$0")/../.." && pwd)"
legacy_root="${V3_LEGACY_ROOT:?Set V3_LEGACY_ROOT to the exact detached legacy worktree}"
candidate_root="${V3_CANDIDATE_ROOT:?Set V3_CANDIDATE_ROOT to the exact detached candidate worktree}"
java_home_override="${BENCHMARK_JAVA_HOME:?Set BENCHMARK_JAVA_HOME to Oracle Java 8u441}"
maven_binary="${BENCHMARK_MAVEN:?Set BENCHMARK_MAVEN to the fixed Maven executable}"
maven_settings="${BENCHMARK_MAVEN_SETTINGS:?Set BENCHMARK_MAVEN_SETTINGS to the reviewed Maven Central settings file}"
node_binary="${BENCHMARK_NODE:?Set BENCHMARK_NODE to the fixed Node executable}"
export JAVA_HOME="$java_home_override"

prepare_legacy() {
  rm -rf "$legacy_root/Consumer/target/v3-runtime-deps" "$legacy_root/Consumer/target/v3-classpath.txt" \
    "$legacy_root/Consumer/target/v3-runtime-manifest.json" "$legacy_root/Consumer/target/v3-structure.json"
  "$maven_binary" -q -s "$maven_settings" -f "$legacy_root/pom.xml" clean install -DskipTests
  "$maven_binary" -q -s "$maven_settings" -f "$legacy_root/pom.xml" -pl Consumer dependency:build-classpath \
    -DincludeScope=runtime -Dmdep.outputFile=target/v3-classpath.txt
  "$maven_binary" -q -s "$maven_settings" -f "$legacy_root/pom.xml" -pl Consumer,Provider dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory="$legacy_root/Consumer/target/v3-runtime-deps"
  "$node_binary" "$coordinator_root/benchmarks/v3/runtime-manifest.mjs" \
    "$legacy_root/Consumer/target/v3-runtime-deps" "$legacy_root/Consumer/target/v3-runtime-manifest.json"
}

prepare_candidate() {
  rm -rf "$candidate_root/tiny-rpc-netty/target/v3-runtime-deps" "$candidate_root/tiny-rpc-netty/target/v3-classpath.txt" \
    "$candidate_root/tiny-rpc-netty/target/v3-runtime-manifest.json" "$candidate_root/tiny-rpc-netty/target/v3-structure.json"
  "$maven_binary" -q -s "$maven_settings" -f "$candidate_root/pom.xml" clean install -DskipTests
  "$maven_binary" -q -s "$maven_settings" -f "$candidate_root/pom.xml" -pl tiny-rpc-netty dependency:build-classpath \
    -DincludeScope=runtime -Dmdep.outputFile=target/v3-classpath.txt
  "$maven_binary" -q -s "$maven_settings" -f "$candidate_root/pom.xml" -pl tiny-rpc-netty dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory="$candidate_root/tiny-rpc-netty/target/v3-runtime-deps"
  "$node_binary" "$coordinator_root/benchmarks/v3/runtime-manifest.mjs" \
    "$candidate_root/tiny-rpc-netty/target/v3-runtime-deps" "$candidate_root/tiny-rpc-netty/target/v3-runtime-manifest.json"
  "$node_binary" "$coordinator_root/benchmarks/measure-structure.mjs" \
    "$candidate_root/tiny-rpc-netty/target/v3-runtime-deps" "$candidate_root/tiny-rpc-netty/target/v3-structure.json" \
    "$candidate_root/tiny-rpc-core/target/tiny-rpc-core-0.0.1-SNAPSHOT.jar" \
    "$candidate_root/tiny-rpc-codec/target/tiny-rpc-codec-0.0.1-SNAPSHOT.jar" \
    "$candidate_root/tiny-rpc-netty/target/tiny-rpc-netty-0.0.1-SNAPSHOT.jar" \
    "$candidate_root/tiny-rpc-telemetry/target/tiny-rpc-telemetry-0.0.1-SNAPSHOT.jar"
}

prepare_legacy
prepare_candidate
printf 'Prepared exact legacy and candidate roots without collecting benchmark samples.\n'
