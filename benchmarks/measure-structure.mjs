import fs from 'node:fs';
import path from 'node:path';

const [runtimeSourcePath, outputPath, ...artifactPaths] = process.argv.slice(2);
if (!runtimeSourcePath || !outputPath || artifactPaths.length === 0) {
  throw new Error('usage: node measure-structure.mjs <runtime-classpath-or-directory> <output.json> <artifact>...');
}

const runtimeSource = fs.statSync(runtimeSourcePath);
const dependencyPaths = runtimeSource.isDirectory()
  ? fs.readdirSync(runtimeSourcePath)
      .filter((entry) => entry.endsWith('.jar'))
      .map((entry) => path.join(runtimeSourcePath, entry))
  : fs.readFileSync(runtimeSourcePath, 'utf8').trim().split(path.delimiter).filter(Boolean);
const dependencies = [...new Set(dependencyPaths)]
  .map((entry) => ({ file: path.basename(entry), bytes: fs.statSync(entry).size }))
  .sort((left, right) => left.file.localeCompare(right.file));
const artifacts = artifactPaths
  .map((entry) => ({ file: path.basename(entry), bytes: fs.statSync(entry).size }))
  .sort((left, right) => left.file.localeCompare(right.file));
const coreArtifacts = artifacts.filter((entry) => /^tiny-rpc-core-.+\.jar$/.test(entry.file));
if (coreArtifacts.length !== 1) {
  throw new Error(`expected exactly one tiny-rpc-core artifact, found ${coreArtifacts.length}`);
}
const report = {
  schemaVersion: '1.0.0',
  runtimeDependencyCount: dependencies.length,
  runtimeDependencyClosureBytes: dependencies.reduce((total, entry) => total + entry.bytes, 0),
  coreArtifactBytes: coreArtifacts[0].bytes,
  distributionArtifactBytes: artifacts.reduce((total, entry) => total + entry.bytes, 0),
  combinedArtifactBytes: artifacts.reduce((total, entry) => total + entry.bytes, 0),
  artifacts,
  dependencies
};
fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`);

