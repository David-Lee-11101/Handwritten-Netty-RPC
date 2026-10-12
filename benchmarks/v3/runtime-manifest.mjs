import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const [directory, outputPath] = process.argv.slice(2);
if (!outputPath) throw new Error('usage: runtime-manifest.mjs <runtime-directory> <output.json>');
const artifacts = fs.readdirSync(directory).filter((file) => file.endsWith('.jar')).sort().map((file) => {
  const content = fs.readFileSync(path.join(directory, file));
  return { file, bytes: content.length, sha256: crypto.createHash('sha256').update(content).digest('hex') };
});
if (!artifacts.length) throw new Error(`no runtime artifacts in ${directory}`);
const report = {
  schemaVersion: '1.0.0', artifactCount: artifacts.length,
  closureBytes: artifacts.reduce((total, artifact) => total + artifact.bytes, 0), artifacts
};
fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`, { flag: 'wx' });
