import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

const [legacyRoot, candidateRoot, outputPath] = process.argv.slice(2);
if (!outputPath) throw new Error('usage: freeze-identities.mjs <legacy-root> <candidate-root> <output.json>');
const coordinatorRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..');
const sha256 = (root, file) => crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
const head = (root) => execFileSync('git', ['-C', root, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
const assertClean = (root) => {
  const status = execFileSync('git', ['-C', root, 'status', '--porcelain', '--untracked-files=no'], { encoding: 'utf8' }).trim();
  if (status) throw new Error(`tracked worktree is dirty: ${root}`);
};
assertClean(legacyRoot); assertClean(candidateRoot);
const legacyChanges = execFileSync('git', ['-C', legacyRoot, 'diff', '--name-only',
  '27779a51bfa38822922d3a57e365d2956b0546b0..HEAD'], { encoding: 'utf8' }).trim().split(/\r?\n/).filter(Boolean);
if (legacyChanges.length !== 1 || legacyChanges[0] !== 'Consumer/src/test/java/com/seclab/nettyClient/LegacyBenchmarkMain.java') {
  throw new Error(`legacy measurement commit must differ from production baseline only by its benchmark harness: ${legacyChanges.join(',')}`);
}
const files = ['benchmarks/v3/analyze.mjs', 'benchmarks/v3/attempt-lineage.json', 'benchmarks/v3/environment.mjs',
  'benchmarks/v3/prepare-exact-roots.sh', 'benchmarks/v3/record-run.mjs', 'benchmarks/v3/run-paired.sh',
  'benchmarks/v3/runtime-manifest.mjs', 'benchmarks/v3/verify-identities.mjs'];
const identity = {
  schemaVersion: '3.1.0', protocolId: 'tiny-rpc-metrology-v3.1-abba',
  coordinator: { commit: head(coordinatorRoot), files: Object.fromEntries(files.map((file) => [file, sha256(coordinatorRoot, file)])) },
  legacy: {
    productionBaseCommit: '27779a51bfa38822922d3a57e365d2956b0546b0', commit: head(legacyRoot),
    harness: { path: 'Consumer/src/test/java/com/seclab/nettyClient/LegacyBenchmarkMain.java', sha256: sha256(legacyRoot, 'Consumer/src/test/java/com/seclab/nettyClient/LegacyBenchmarkMain.java') },
    poms: Object.fromEntries(['pom.xml', 'Consumer/pom.xml', 'Provider/pom.xml'].map((file) => [file, sha256(legacyRoot, file)])),
    runtimeManifest: { path: 'Consumer/target/v3-runtime-manifest.json', sha256: sha256(legacyRoot, 'Consumer/target/v3-runtime-manifest.json') }
  },
  candidate: {
    commit: head(candidateRoot),
    harness: { path: 'tiny-rpc-netty/src/test/java/io/smarthome/tinyrpc/netty/TinyRpcBenchmarkMain.java', sha256: sha256(candidateRoot, 'tiny-rpc-netty/src/test/java/io/smarthome/tinyrpc/netty/TinyRpcBenchmarkMain.java') },
    poms: Object.fromEntries(['pom.xml', 'tiny-rpc-core/pom.xml', 'tiny-rpc-codec/pom.xml', 'tiny-rpc-netty/pom.xml', 'tiny-rpc-telemetry/pom.xml']
      .map((file) => [file, sha256(candidateRoot, file)])),
    runtimeManifest: { path: 'tiny-rpc-netty/target/v3-runtime-manifest.json', sha256: sha256(candidateRoot, 'tiny-rpc-netty/target/v3-runtime-manifest.json') },
    structure: { path: 'tiny-rpc-netty/target/v3-structure.json', sha256: sha256(candidateRoot, 'tiny-rpc-netty/target/v3-structure.json') }
  },
  structuralCaps: { coreArtifactBytesExclusive: 34238, runtimeDependencyClosureBytesExclusive: 22563354, springArtifactsInCoreRuntime: 0 }
};
fs.writeFileSync(outputPath, `${JSON.stringify(identity, null, 2)}\n`, { flag: 'wx' });
