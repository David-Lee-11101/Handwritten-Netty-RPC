import fs from 'node:fs';

const [outputPath, pairIndexText, plannedOrder, sequenceText, implementation, sourceCommit,
  harnessSha256, environmentId, startedAtEpochMs, endedAtEpochMs, rawText] = process.argv.slice(2);
if (!rawText) throw new Error('record-run requires complete run metadata and raw JSON');
const raw = JSON.parse(rawText);
const numericFields = ['startupNanos', 'idleHeapBytes', 'idleRssBytes', 'p50LatencyNanos',
  'p95LatencyNanos', 'p99LatencyNanos', 'throughputPerSecond'];
const frameFields = ['readinessRequestFrameBytes', 'readinessResponseFrameBytes', 'warmupRequestFrameBytes',
  'warmupResponseFrameBytes', 'measuredRequestFrameBytes', 'measuredResponseFrameBytes'];
if (raw.logicalEchoValidated !== true || raw.measuredCalls !== 1000) throw new Error('logical workload validation failed');
for (const field of frameFields) if (!Number.isInteger(raw[field]) || raw[field] < 1) throw new Error(`invalid ${field}`);
for (const field of numericFields) if (!Number.isFinite(raw[field]) || raw[field] <= 0) throw new Error(`invalid ${field}`);
const record = {
  schemaVersion: '3.0.0', pairIndex: Number(pairIndexText), plannedOrder,
  actualSequence: Number(sequenceText), implementation, sourceCommit, harnessSha256, environmentId,
  startedAtEpochMs: Number(startedAtEpochMs), endedAtEpochMs: Number(endedAtEpochMs), raw
};
fs.appendFileSync(outputPath, `${JSON.stringify(record)}\n`, 'utf8');
