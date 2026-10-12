import fs from 'node:fs';
import crypto from 'node:crypto';

const [recordsPath, identitiesPath, structurePath, lineagePath, outputPath] = process.argv.slice(2);
if (!outputPath) {
  throw new Error('usage: analyze.mjs <records.jsonl> <identities.json> <candidate-structure.json> <lineage.json> <output.json>');
}
const identities = JSON.parse(fs.readFileSync(identitiesPath, 'utf8'));
const structure = JSON.parse(fs.readFileSync(structurePath, 'utf8'));
const lineage = JSON.parse(fs.readFileSync(lineagePath, 'utf8'));
const sha256 = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
if (sha256(structurePath) !== identities.candidate.structure.sha256) throw new Error('candidate structure identity mismatch');
if (sha256(lineagePath) !== identities.coordinator.files['benchmarks/v3/attempt-lineage.json']) throw new Error('lineage identity mismatch');
const lines = fs.readFileSync(recordsPath, 'utf8').trim().split(/\r?\n/).filter(Boolean);
if (lines.length !== 24) throw new Error(`expected exactly 24 records, found ${lines.length}`);
const records = lines.map((line, index) => {
  const record = JSON.parse(line);
  const sequence = index + 1;
  const pairIndex = Math.floor(index / 2) + 1;
  const plannedOrder = pairIndex % 2 ? 'legacy-candidate' : 'candidate-legacy';
  const expectedImplementation = plannedOrder.split('-')[(sequence - 1) % 2];
  if (record.schemaVersion !== '3.0.0' || record.actualSequence !== sequence || record.pairIndex !== pairIndex
      || record.plannedOrder !== plannedOrder || record.implementation !== expectedImplementation) {
    throw new Error(`record ${sequence} violates the frozen AB/BA sequence`);
  }
  const expected = identities[record.implementation];
  if (!expected || record.sourceCommit !== expected.commit || record.harnessSha256 !== expected.harness.sha256) {
    throw new Error(`record ${sequence} identity mismatch`);
  }
  if (!/^[a-f0-9]{64}$/.test(record.environmentId) || record.environmentId !== recordsEnvironment(lines[0])) {
    throw new Error(`record ${sequence} environment mismatch`);
  }
  if (!Number.isInteger(record.startedAtEpochMs) || !Number.isInteger(record.endedAtEpochMs)
      || record.endedAtEpochMs < record.startedAtEpochMs) throw new Error(`record ${sequence} has invalid timestamps`);
  validateRawContract(record.raw, sequence);
  return record;
});

function recordsEnvironment(firstLine) {
  return JSON.parse(firstLine).environmentId;
}

function validateRawContract(raw, sequence) {
  const frames = ['readinessRequestFrameBytes', 'readinessResponseFrameBytes', 'warmupRequestFrameBytes',
    'warmupResponseFrameBytes', 'measuredRequestFrameBytes', 'measuredResponseFrameBytes'];
  if (!raw || raw.logicalEchoValidated !== true || raw.measuredCalls !== 1000) {
    throw new Error(`record ${sequence} did not validate the logical echo workload`);
  }
  for (const field of frames) if (!Number.isInteger(raw[field]) || raw[field] < 1) throw new Error(`record ${sequence} invalid ${field}`);
}

const median = (values) => {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted.length % 2 ? sorted[(sorted.length - 1) / 2]
    : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2;
};
const quantile = (values, q) => {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.min(sorted.length - 1, Math.max(0, Math.ceil(q * sorted.length) - 1))];
};
const summarize = (values) => ({ median: median(values), min: Math.min(...values), max: Math.max(...values), p95: quantile(values, 0.95) });
const metric = (record, name) => {
  const value = record.raw[name];
  if (!Number.isFinite(value) || value <= 0) throw new Error(`invalid ${name} in sequence ${record.actualSequence}`);
  return value;
};

// The frozen margins are computed exclusively from legacy records before candidate metric values are accessed.
const legacy = records.filter((record) => record.implementation === 'legacy');
const legacyStartup = legacy.map((record) => metric(record, 'startupNanos'));
const legacyLatency = legacy.map((record) => metric(record, 'p95LatencyNanos'));
const legacySummaries = {
  startupNanos: summarize(legacyStartup),
  p95LatencyNanos: summarize(legacyLatency)
};
const margins = {
  startupNanos: Math.min(9823021.5, Math.max(84, legacySummaries.startupNanos.p95 - legacySummaries.startupNanos.median)),
  p95LatencyNanos: Math.min(76020.5, Math.max(84, legacySummaries.p95LatencyNanos.p95 - legacySummaries.p95LatencyNanos.median))
};

const candidate = records.filter((record) => record.implementation === 'candidate');
const pairByIndex = (items) => new Map(items.map((record) => [record.pairIndex, record]));
const legacyByPair = pairByIndex(legacy);
const candidateByPair = pairByIndex(candidate);
const seedFor = (name) => [...name].reduce((seed, char) => ((seed * 33) ^ char.charCodeAt(0)) >>> 0, 20261012);
const bootstrap = (name) => {
  const differences = [];
  for (let pair = 1; pair <= 12; pair++) {
    const left = legacyByPair.get(pair); const right = candidateByPair.get(pair);
    if (!left || !right) throw new Error(`incomplete pair ${pair}`);
    differences.push(metric(right, name) - metric(left, name));
  }
  let state = seedFor(name);
  const random = () => { state ^= state << 13; state ^= state >>> 17; state ^= state << 5; return (state >>> 0) / 0x100000000; };
  const samples = [];
  for (let sample = 0; sample < 10000; sample++) {
    const drawn = [];
    for (let index = 0; index < 12; index++) drawn.push(differences[Math.floor(random() * 12)]);
    samples.push(median(drawn));
  }
  return { pairedDifferences: differences, medianDifference: median(differences), lower95: quantile(samples, 0.025), upper95: quantile(samples, 0.975) };
};
const metricNames = ['idleHeapBytes', 'idleRssBytes', 'startupNanos', 'p95LatencyNanos', 'throughputPerSecond'];
const metrics = Object.fromEntries(metricNames.map((name) => [name, bootstrap(name)]));
if (structure.schemaVersion !== '1.0.0' || !Array.isArray(structure.dependencies)) throw new Error('invalid candidate structure report');
const checks = {
  idleHeapImproved: metrics.idleHeapBytes.upper95 < 0,
  idleRssImproved: metrics.idleRssBytes.upper95 < 0,
  startupNonInferior: metrics.startupNanos.upper95 <= margins.startupNanos,
  latencyNonInferior: metrics.p95LatencyNanos.upper95 <= margins.p95LatencyNanos,
  coreArtifactSmaller: structure.coreArtifactBytes < 34238,
  runtimeClosureSmaller: structure.runtimeDependencyClosureBytes < 22563354,
  springFreeCoreRuntime: structure.dependencies.every((entry) => !String(entry.file).toLowerCase().includes('spring'))
};
const frames = Object.fromEntries(['legacy', 'candidate'].map((implementation) => {
  const raw = records.find((record) => record.implementation === implementation).raw;
  return [implementation, Object.fromEntries(Object.entries(raw).filter(([key]) => key.endsWith('FrameBytes')))];
}));
const report = {
  schemaVersion: '3.0.0', method: 'metrology-v3.1-abba', matchedPairs: 12,
  identities: { legacy: identities.legacy.commit, candidate: identities.candidate.commit },
  lineageProtocol: lineage.protocolId, bootstrap: { seed: 20261012, resamples: 10000, statistic: 'matched-pair median difference' },
  legacySummaries, margins, metrics, representativeEncodedFrames: frames, structure, checks,
  pass: Object.values(checks).every(Boolean)
};
fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`, { flag: 'wx' });
