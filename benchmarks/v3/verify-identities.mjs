import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const [identitiesPath, implementation, root] = process.argv.slice(2);
const identities = JSON.parse(fs.readFileSync(identitiesPath, 'utf8'));
const expected = identities[implementation];
if (!expected) throw new Error(`unknown implementation ${implementation}`);
const head = execFileSync('git', ['-C', root, 'rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
if (head !== expected.commit) throw new Error(`${implementation} commit mismatch: ${head}`);
const hash = (file) => crypto.createHash('sha256').update(fs.readFileSync(path.join(root, file))).digest('hex');
if (hash(expected.harness.path) !== expected.harness.sha256) throw new Error(`${implementation} harness mismatch`);
for (const [file, digest] of Object.entries(expected.poms)) if (hash(file) !== digest) throw new Error(`${implementation} POM mismatch: ${file}`);
const runtimeManifest = path.join(root, expected.runtimeManifest.path);
if (hash(expected.runtimeManifest.path) !== expected.runtimeManifest.sha256)
  throw new Error(`${implementation} runtime manifest mismatch: ${runtimeManifest}`);
const runtime = JSON.parse(fs.readFileSync(runtimeManifest, 'utf8'));
const runtimeDirectory = path.join(root, expected.runtimeManifest.directory);
const actualRuntimeFiles = fs.readdirSync(runtimeDirectory).filter((file) => file.endsWith('.jar')).sort();
if (JSON.stringify(actualRuntimeFiles) !== JSON.stringify(runtime.artifacts.map((artifact) => artifact.file))) {
  throw new Error(`${implementation} runtime directory contents mismatch`);
}
for (const artifact of runtime.artifacts) {
  const file = path.join(runtimeDirectory, artifact.file); const content = fs.readFileSync(file);
  const digest = crypto.createHash('sha256').update(content).digest('hex');
  if (content.length !== artifact.bytes || digest !== artifact.sha256) throw new Error(`${implementation} runtime artifact mismatch: ${artifact.file}`);
}
if (hash(expected.classManifest.path) !== expected.classManifest.sha256) throw new Error(`${implementation} class manifest mismatch`);
const classManifest = JSON.parse(fs.readFileSync(path.join(root, expected.classManifest.path), 'utf8'));
const collect = (directory, current = directory) => fs.readdirSync(current, { withFileTypes: true }).flatMap((entry) => {
  const full = path.join(current, entry.name);
  return entry.isDirectory() ? collect(directory, full) : [{ file: path.relative(directory, full).split(path.sep).join('/'), full }];
});
for (const [label, relativeRoot] of Object.entries(expected.classManifest.roots)) {
  const directory = path.join(root, relativeRoot); const actual = collect(directory).sort((a, b) => a.file.localeCompare(b.file));
  const frozen = classManifest.roots[label];
  if (!frozen || JSON.stringify(actual.map((entry) => entry.file)) !== JSON.stringify(frozen.map((entry) => entry.file))) {
    throw new Error(`${implementation} class tree contents mismatch: ${label}`);
  }
  for (let index = 0; index < actual.length; index++) {
    const content = fs.readFileSync(actual[index].full); const digest = crypto.createHash('sha256').update(content).digest('hex');
    if (content.length !== frozen[index].bytes || digest !== frozen[index].sha256) {
      throw new Error(`${implementation} class file mismatch: ${label}/${actual[index].file}`);
    }
  }
}
const status = execFileSync('git', ['-C', root, 'status', '--porcelain', '--untracked-files=no'], { encoding: 'utf8' });
if (status.trim()) throw new Error(`${implementation} tracked worktree is dirty`);
const coordinatorRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..');
for (const [file, digest] of Object.entries(identities.coordinator.files)) {
  const actual = crypto.createHash('sha256').update(fs.readFileSync(path.join(coordinatorRoot, file))).digest('hex');
  if (actual !== digest) throw new Error(`coordinator mismatch: ${file}`);
}
