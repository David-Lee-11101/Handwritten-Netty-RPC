import fs from 'node:fs';
import path from 'node:path';

const [classpathPath, outputPath, ...artifactPaths] = process.argv.slice(2);
if (!classpathPath || !outputPath || artifactPaths.length === 0) {
  throw new Error('usage: node measure-structure.mjs <runtime-classpath> <output.json> <artifact>...');
}

const dependencies = [...new Set(fs.readFileSync(classpathPath, 'utf8').trim().split(path.delimiter).filter(Boolean))]
  .map((entry) => ({ file: path.basename(entry), bytes: fs.statSync(entry).size }))
  .sort((left, right) => left.file.localeCompare(right.file));
const artifacts = artifactPaths
  .map((entry) => ({ file: path.basename(entry), bytes: fs.statSync(entry).size }))
  .sort((left, right) => left.file.localeCompare(right.file));
const report = {
  schemaVersion: '1.0.0',
  runtimeDependencyCount: dependencies.length,
  runtimeDependencyClosureBytes: dependencies.reduce((total, entry) => total + entry.bytes, 0),
  combinedArtifactBytes: artifacts.reduce((total, entry) => total + entry.bytes, 0),
  artifacts,
  dependencies
};
fs.writeFileSync(outputPath, `${JSON.stringify(report, null, 2)}\n`);

