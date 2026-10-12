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
const status = execFileSync('git', ['-C', root, 'status', '--porcelain', '--untracked-files=no'], { encoding: 'utf8' });
if (status.trim()) throw new Error(`${implementation} tracked worktree is dirty`);
const coordinatorRoot = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../..');
for (const [file, digest] of Object.entries(identities.coordinator.files)) {
  const actual = crypto.createHash('sha256').update(fs.readFileSync(path.join(coordinatorRoot, file))).digest('hex');
  if (actual !== digest) throw new Error(`coordinator mismatch: ${file}`);
}
