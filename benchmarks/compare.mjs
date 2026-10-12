import fs from 'node:fs';

const [baselinePath, currentPath, envelopePath, structurePath, outputPath] = process.argv.slice(2);
if (!outputPath) {
  throw new Error('usage: node compare.mjs <baseline.jsonl> <current.jsonl> <envelope-v2.json> <current-structure.json> <output.json>');
}
const readRuns = (file) => fs.readFileSync(file, 'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
const baseline = readRuns(baselinePath);
const current = readRuns(currentPath);
const envelope = JSON.parse(fs.readFileSync(envelopePath, 'utf8'));
const structure = JSON.parse(fs.readFileSync(structurePath, 'utf8'));
if (baseline.length !== current.length || baseline.length < 10) throw new Error('matched run counts must agree and be at least 10');

let randomState = envelope.method.bootstrap.seed >>> 0;
const random = () => {
  randomState ^= randomState << 13; randomState ^= randomState >>> 17; randomState ^= randomState << 5;
  return (randomState >>> 0) / 0x100000000;
};
const median = (values) => {
  const sorted = [...values].sort((left, right) => left - right);
  return sorted.length % 2 ? sorted[Math.floor(sorted.length / 2)]
    : (sorted[sorted.length / 2 - 1] + sorted[sorted.length / 2]) / 2;
};
const quantile = (values, q) => {
  const sorted = [...values].sort((left, right) => left - right);
  return sorted[Math.min(sorted.length - 1, Math.max(0, Math.ceil(q * sorted.length) - 1))];
};
const interval = (metric) => {
  const paired = current.map((run, index) => Number(run[metric]) - Number(baseline[index][metric]));
  const bootstrapped = [];
  for (let sample = 0; sample < envelope.method.bootstrap.resamples; sample++) {
    const values = [];
    for (let index = 0; index < paired.length; index++) values.push(paired[Math.floor(random() * paired.length)]);
    bootstrapped.push(median(values));
  }
  return { medianDifference: median(paired), lower95: quantile(bootstrapped, 0.025), upper95: quantile(bootstrapped, 0.975) };
};

const metrics = Object.fromEntries(['idleHeapBytes', 'idleRssBytes', 'startupNanos', 'p95LatencyNanos', 'throughputPerSecond']
  .map((metric) => [metric, interval(metric)]));
const checks = {
  idleHeapImproved: metrics.idleHeapBytes.upper95 < 0,
  idleRssImproved: metrics.idleRssBytes.upper95 < 0,
  startupNonInferior: metrics.startupNanos.upper95 <= envelope.frozenMargins.startupNanos,
  latencyNonInferior: metrics.p95LatencyNanos.upper95 <= envelope.frozenMargins.p95LatencyNanos,
  coreArtifactSmaller: structure.coreArtifactBytes < envelope.baselineStructure.combinedArtifactBytes,
  runtimeClosureSmaller: structure.runtimeDependencyClosureBytes < envelope.baselineStructure.runtimeDependencyClosureBytes,
  springFreeCoreRuntime: structure.dependencies.every((entry) => !entry.file.toLowerCase().includes('spring'))
};
const report = {
  schemaVersion: '1.0.0', method: envelope.method, frozenMargins: envelope.frozenMargins,
  matchedRuns: baseline.length, metrics, structure, checks,
  pass: Object.values(checks).every(Boolean)
};
fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`, { flag: 'wx' });

