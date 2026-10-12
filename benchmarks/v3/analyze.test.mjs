import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { spawnSync } from 'node:child_process';

const script = path.resolve(path.dirname(new URL(import.meta.url).pathname), 'analyze.mjs');
const sha = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const commit = (char) => char.repeat(40);
const harness = (char) => char.repeat(64);
const environmentId = 'e'.repeat(64);
const raw = (implementation, pair) => ({
  logicalEchoValidated: true, measuredCalls: 1000,
  readinessRequestFrameBytes: implementation === 'legacy' ? 167 : 232,
  readinessResponseFrameBytes: implementation === 'legacy' ? 142 : 119,
  warmupRequestFrameBytes: implementation === 'legacy' ? 170 : 235,
  warmupResponseFrameBytes: implementation === 'legacy' ? 145 : 122,
  measuredRequestFrameBytes: implementation === 'legacy' ? 171 : 234,
  measuredResponseFrameBytes: implementation === 'legacy' ? 146 : 121,
  startupNanos: (implementation === 'legacy' ? 120000000 : 115000000) + pair,
  idleHeapBytes: (implementation === 'legacy' ? 5200000 : 3900000) + pair,
  idleRssBytes: (implementation === 'legacy' ? 95000000 : 78000000) + pair,
  p50LatencyNanos: 100000, p95LatencyNanos: (implementation === 'legacy' ? 390000 : 360000) + pair,
  p99LatencyNanos: 500000, throughputPerSecond: implementation === 'legacy' ? 5000 : 5100
});

function fixture() {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), 'tiny-rpc-v3-test-'));
  const lineagePath = path.join(root, 'lineage.json');
  const structurePath = path.join(root, 'structure.json');
  fs.writeFileSync(lineagePath, JSON.stringify({ protocolId: 'tiny-rpc-metrology-v3.1-abba' }));
  fs.writeFileSync(structurePath, JSON.stringify({ schemaVersion: '1.0.0', coreArtifactBytes: 7000,
    runtimeDependencyClosureBytes: 5000000, dependencies: [{ file: 'netty-all.jar' }] }));
  const identities = {
    legacy: { commit: commit('a'), harness: { sha256: harness('b') } },
    candidate: { commit: commit('c'), harness: { sha256: harness('d') }, structure: { sha256: sha(structurePath) } },
    coordinator: { files: { 'benchmarks/v3/attempt-lineage.json': sha(lineagePath) } }
  };
  const identitiesPath = path.join(root, 'identities.json'); fs.writeFileSync(identitiesPath, JSON.stringify(identities));
  const records = []; let sequence = 0;
  for (let pair = 1; pair <= 12; pair++) {
    const order = pair % 2 ? ['legacy', 'candidate'] : ['candidate', 'legacy'];
    for (const implementation of order) records.push({
      schemaVersion: '3.0.0', pairIndex: pair, plannedOrder: order.join('-'), actualSequence: ++sequence,
      implementation, sourceCommit: identities[implementation].commit,
      harnessSha256: identities[implementation].harness.sha256, environmentId,
      startedAtEpochMs: 1000 + sequence, endedAtEpochMs: 2000 + sequence, raw: raw(implementation, pair)
    });
  }
  return { root, lineagePath, structurePath, identitiesPath, identities, records };
}

function execute(f, records, name) {
  const recordsPath = path.join(f.root, `${name}.jsonl`); const outputPath = path.join(f.root, `${name}-output.json`);
  fs.writeFileSync(recordsPath, `${records.map(JSON.stringify).join('\n')}\n`);
  const result = spawnSync(process.execPath, [script, recordsPath, f.identitiesPath, f.structurePath, f.lineagePath, outputPath], { encoding: 'utf8' });
  return { result, outputPath };
}

test('accepts exactly twelve complete alternating matched pairs', () => {
  const f = fixture(); const { result, outputPath } = execute(f, f.records, 'valid');
  assert.equal(result.status, 0, result.stderr); const report = JSON.parse(fs.readFileSync(outputPath));
  assert.equal(report.matchedPairs, 12); assert.equal(report.pass, true);
  assert.equal(report.structure.coreArtifactBytes, 7000);
});

test('rejects missing, reordered, and wrong-identity records', () => {
  for (const mutation of ['missing', 'reordered', 'identity']) {
    const f = fixture(); const records = structuredClone(f.records);
    if (mutation === 'missing') records.pop();
    if (mutation === 'reordered') [records[0], records[1]] = [records[1], records[0]];
    if (mutation === 'identity') records[5].sourceCommit = commit('f');
    const { result, outputPath } = execute(f, records, mutation);
    assert.notEqual(result.status, 0); assert.equal(fs.existsSync(outputPath), false);
  }
});
