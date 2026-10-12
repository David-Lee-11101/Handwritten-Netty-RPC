import fs from 'node:fs';
import crypto from 'node:crypto';

const [inputPath, outputPath] = process.argv.slice(2);
if (!inputPath || !outputPath) {
  throw new Error('usage: node analyze-baseline.mjs <runs.jsonl> <acceptance-envelope.json>');
}

const runs = fs.readFileSync(inputPath, 'utf8').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
if (runs.length < 10) {
  throw new Error(`at least 10 process runs are required; received ${runs.length}`);
}

const metricNames = ['startupNanos', 'idleHeapBytes', 'idleRssBytes', 'p50LatencyNanos', 'p95LatencyNanos', 'p99LatencyNanos', 'throughputPerSecond'];
const sorted = (name) => runs.map((run) => Number(run[name])).sort((left, right) => left - right);
const median = (values) => values.length % 2 === 0
  ? (values[values.length / 2 - 1] + values[values.length / 2]) / 2
  : values[Math.floor(values.length / 2)];
const quantile = (values, q) => values[Math.min(values.length - 1, Math.max(0, Math.ceil(q * values.length) - 1))];
const summaries = Object.fromEntries(metricNames.map((name) => {
  const values = sorted(name);
  return [name, { median: median(values), min: values[0], max: values.at(-1), p95: quantile(values, 0.95) }];
}));

// Frozen from untouched baseline repeatability only. Measurement resolution is 1 ms.
const resolutionNanos = 1_000_000;
const startupDispersion = summaries.startupNanos.p95 - summaries.startupNanos.median;
const latencyDispersion = summaries.p95LatencyNanos.p95 - summaries.p95LatencyNanos.median;
const envelope = {
  schemaVersion: '1.0.0',
  state: 'frozen-before-runtime-optimization',
  method: {
    matchedRuns: runs.length,
    warmupCallsPerRun: 200,
    measuredCallsPerRun: runs[0].measuredCalls,
    bootstrap: { seed: 20261012, confidence: 0.95, resamples: 10000, statistic: 'matched-run median difference' },
    jvmFlags: ['-Xms64m', '-Xmx256m', '-XX:+UseSerialGC'],
    memoryPass: 'upper95CI(new-minus-baseline)<0',
    nonInferiorityPass: 'upper95CI(new-minus-baseline)<=frozenMarginNanos'
  },
  baseline: summaries,
  frozenMargins: {
    startupNanos: Math.max(resolutionNanos, startupDispersion),
    p95LatencyNanos: Math.max(resolutionNanos, latencyDispersion)
  },
  mandatoryStructure: {
    springArtifactsInCoreRuntime: 0,
    maxEncodedFrameBytes: 1048576,
    maxPendingRequests: 256,
    maxQueuedWritesPerChannel: 256,
    coreArtifactBytes: 'strictly-less-than-baseline-combined-artifact-bytes',
    runtimeDependencyClosureBytes: 'strictly-less-than-baseline-runtime-dependency-closure-bytes'
  },
  inputSha256: crypto.createHash('sha256').update(fs.readFileSync(inputPath)).digest('hex')
};
fs.writeFileSync(outputPath, `${JSON.stringify(envelope, null, 2)}\n`, { flag: 'wx' });

