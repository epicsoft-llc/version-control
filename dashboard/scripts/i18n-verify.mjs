// Checks the translation files of the interface, against the code and among
// themselves. Run with: npm run i18n:verify
//
// Offline and dependency free, so it can run in every build. Against the code
// it catches the one failure ngx-translate hides: a missing key is rendered as
// the key itself, so the screen shows "masterData.dependencies.incomplete"
// where a sentence belongs, and nothing anywhere says that something is wrong.
// The other direction finds texts no code asks for any more.
//
// Dynamic keys are resolved rather than skipped. A template writes
// `'constraintState.' + check.state`, and the values that prefix can take are
// the members of the TypeScript union of the same name, so the union is read
// out of the DTO files and every member checked.
//
// Among themselves, every file carries every key, each with a text and the
// same placeholders, one key per line in alphabetical order. The files are
// written by hand: the order keeps them line by line side by side, so a review
// sees a missing text, and it turns a key written twice into an error — JSON
// keeps the second one and drops the first without a word.

import {readdir, readFile} from 'node:fs/promises';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const projectDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sourceDir = join(projectDir, 'src', 'app');
const bundleDir = join(projectDir, 'src', 'assets', 'i18n');

/** The language the interface falls back to; a key missing here is visible. */
const FALLBACK = 'en';

/** `{{ 'key' | translate }}`, `[title]="'key' | translate"`. */
const PIPED = /'([a-zA-Z][a-zA-Z0-9]*(?:\.[a-zA-Z0-9_]+)+)'\s*\|\s*translate/g;

/**
 * `this.translate.instant('key')` and its siblings. The receiver has to carry
 * the word in its name: a bare `…('key')` would take in every dotted string
 * that happens to close a call — `form.get('component.name')`,
 * `transform(d, 'YYYY.MM.DD')` — and report keys nobody ever used. The name
 * itself may be the whole word, so nothing is required in front of it.
 */
const CALLED = /[\w$]*[Tt]ranslate[\w$]*\s*\.\s*(?:instant|get|stream)\(\s*'([^']+)'/g;

/**
 * `'constraintState.' + check.state` — a prefix joined with a value at runtime.
 * More than one segment is allowed on purpose: `'check.state.' + check.state`
 * is the obvious next refactor, and a pattern that only accepts one segment
 * would collect nothing from it and report neither the keys nor a problem.
 */
const DYNAMIC = /'([a-zA-Z][a-zA-Z0-9]*(?:\.[a-zA-Z0-9_]+)*\.)'\s*\+/g;

/** The key at the start of a line of a bundle, as it is written. */
const LINE_KEY = /^\s*"((?:[^"\\]|\\.)*)"\s*:/gm;

/** `{{name}}` — the names have to match from one language to the next. */
const PLACEHOLDER = /\{\{\s*([\w.]+)\s*\}\}/g;

/** Templates and components; the specs are not the interface. */
async function sourceFiles(directory) {
  const entries = await readdir(directory, {withFileTypes: true});
  const files = await Promise.all(entries.map((entry) => {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) {
      return sourceFiles(path);
    }
    return /\.(html|ts)$/.test(entry.name) && !entry.name.endsWith('.spec.ts') ? [path] : [];
  }));
  return files.flat();
}

/** The members of `export type Name = 'A' | 'B';`, wherever it is declared. */
async function unionMembers(typeName) {
  const files = await sourceFiles(join(projectDir, 'src', 'app', 'repositories'));
  for (const file of files) {
    const text = await readFile(file, 'utf8');
    const declaration = new RegExp(`export type ${typeName}\\s*=([^;]+);`).exec(text);
    if (declaration) {
      return [...declaration[1].matchAll(/'([^']+)'/g)].map((match) => match[1]);
    }
  }
  return null;
}

const problems = [];

// ---- the files among themselves ---------------------------------------------
const bundles = new Map();
for (const name of (await readdir(bundleDir)).filter((file) => file.endsWith('.json')).sort()) {
  const text = await readFile(join(bundleDir, name), 'utf8');
  const bundle = JSON.parse(text);
  if (typeof bundle !== 'object' || bundle === null || Object.values(bundle).some((value) => typeof value !== 'string')) {
    problems.push(`${name}: not a flat object of texts`);
    continue;
  }
  bundles.set(name, bundle);
  const written = [...text.matchAll(LINE_KEY)].map((match) => match[1]);
  const keys = Object.keys(bundle);
  if (new Set(written).size !== keys.length || written.some((key) => !(key in bundle))) {
    problems.push(`${name}: write one key per line`);
  }
  written.forEach((key, index) => {
    const before = written[index - 1];
    if (index > 0 && key <= before) {
      problems.push(`${name}: '${key}' is ${key === before ? 'written twice' : `out of order, after '${before}'`}`);
    }
  });
  keys.filter((key) => bundle[key].trim() === '').forEach((key) => problems.push(`${name}: '${key}' has no text`));
}

const allKeys = [...new Set([...bundles.values()].flatMap((bundle) => Object.keys(bundle)))].sort();
for (const [name, bundle] of bundles) {
  allKeys.filter((key) => !(key in bundle)).forEach((key) => problems.push(`${name}: '${key}' is missing`));
}
for (const key of allKeys) {
  const forms = [...bundles].filter(([, bundle]) => key in bundle)
                            .map(([name, bundle]) => [name, [...bundle[key].matchAll(PLACEHOLDER)].map((match) => match[1]).sort().join(',')]);
  if (new Set(forms.map(([, found]) => found)).size > 1) {
    problems.push(`'${key}' has different placeholders: ${forms.map(([name, found]) => `${name} {${found}}`).join(', ')}`);
  }
}

// ---- the files against the code ---------------------------------------------
const files = await sourceFiles(sourceDir);
const code = (await Promise.all(files.map((file) => readFile(file, 'utf8')))).join('\n');
const used = new Set();
const dynamicPrefixes = new Set();
for (const pattern of [PIPED, CALLED]) {
  for (const match of code.matchAll(pattern)) {
    used.add(match[1]);
  }
}
for (const match of code.matchAll(DYNAMIC)) {
  dynamicPrefixes.add(match[1]);
}

for (const prefix of dynamicPrefixes) {
  // constraintState. -> ConstraintState, check.state. -> CheckState: every
  // segment capitalised and joined. A prefix with no such type behind it is
  // reported rather than passed over.
  const typeName = prefix.slice(0, -1).split('.')
                         .map((segment) => segment[0].toUpperCase() + segment.slice(1))
                         .join('');
  const members = await unionMembers(typeName);
  if (members === null) {
    problems.push(`dynamic key prefix ${prefix} could not be resolved: no union type ${typeName} found`);
    continue;
  }
  members.forEach((member) => used.add(prefix + member));
}

const fallback = bundles.get(`${FALLBACK}.json`) ?? {};
[...used].filter((key) => !(key in fallback)).sort()
         .forEach((key) => problems.push(`'${key}' is used by the interface and missing from ${FALLBACK}.json`));
// A key handed around before it reaches the pipe counts as used once it is
// written as a literal anywhere in the code.
Object.keys(fallback).filter((key) => !used.has(key) && !code.includes(`'${key}'`) && !code.includes(`"${key}"`))
      .forEach((key) => problems.push(`'${key}' is used nowhere in the interface`));

if (problems.length > 0) {
  console.error(`i18n: ${problems.length} problem(s) in src/assets/i18n:`);
  problems.forEach((problem) => console.error(`  ${problem}`));
  process.exit(1);
}

console.log(`i18n: ${used.size} keys used, ${allKeys.length} in ${[...bundles.keys()].join(', ')}, complete and in order.`);
