import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const [outputPath, ...specs] = process.argv.slice(2);
if (!outputPath || !specs.length) throw new Error('usage: tree-manifest.mjs <output.json> <label=directory>...');
const sha256 = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex');
const walk = (root, current = root) => fs.readdirSync(current, { withFileTypes: true }).flatMap((entry) => {
  const full = path.join(current, entry.name);
  return entry.isDirectory() ? walk(root, full) : [{ file: path.relative(root, full).split(path.sep).join('/'), bytes: fs.statSync(full).size, sha256: sha256(full) }];
});
const roots = Object.fromEntries(specs.map((spec) => {
  const separator = spec.indexOf('='); if (separator < 1) throw new Error(`invalid tree specification ${spec}`);
  const label = spec.slice(0, separator); const directory = spec.slice(separator + 1);
  const files = walk(directory).sort((left, right) => left.file.localeCompare(right.file));
  if (!files.length) throw new Error(`empty class tree ${label}`);
  return [label, files];
}));
fs.writeFileSync(outputPath, `${JSON.stringify({ schemaVersion: '1.0.0', roots }, null, 2)}\n`, { flag: 'wx' });
