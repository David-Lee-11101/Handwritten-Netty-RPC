import fs from 'node:fs';
import crypto from 'node:crypto';
import os from 'node:os';

const [outputPath, javaText, identitiesSha256] = process.argv.slice(2);
if (!identitiesSha256) throw new Error('usage: environment.mjs <output> <java-version> <identities-sha256>');
const environment = {
  schemaVersion: '3.0.0', platform: `${os.platform()} ${os.release()} ${os.arch()}`,
  cpu: os.cpus()[0]?.model, logicalCpuCount: os.cpus().length, memoryBytes: os.totalmem(),
  java: javaText, node: process.version, identitiesSha256
};
const canonical = JSON.stringify(environment);
environment.environmentId = crypto.createHash('sha256').update(canonical).digest('hex');
fs.writeFileSync(outputPath, `${JSON.stringify(environment, null, 2)}\n`, { flag: 'wx' });
