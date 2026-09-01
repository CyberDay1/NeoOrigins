// Regression net for the "unchecked box cannot mean false" defect.
// Framework-free to match the sibling suites: throw on failure, exit non-zero.
//
//   npm run check:seed
//
// hub: neoorigins/unset-fields.md

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { mirrorSeedFor } from '../fieldSeed.js';
import { parsePowerSchema, parseRefSchema } from '../SchemaFormModel.js';
import type { FormFieldSpec, BooleanFieldSpec } from '../FormFieldSpec.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '../../../../..');
const schemaDir = resolve(repoRoot, 'docs/schema');

const read = (n: string) => JSON.parse(readFileSync(resolve(schemaDir, n), 'utf-8'));
const powerSchema = read('power.schema.json');
const actionSchema = read('action.schema.json');
const conditionSchema = read('condition.schema.json');
const fieldDocs = read('field_docs.json');

let failed = 0;
let passed = 0;

function check(label: string, fn: () => void): void {
	try {
		fn();
		passed++;
		console.log(`  pass  ${label}`);
	} catch (e) {
		failed++;
		console.error(`  FAIL  ${label}`);
		console.error('         ' + (e instanceof Error ? e.message : String(e)));
	}
}

function assert(cond: unknown, msg: string): asserts cond {
	if (!cond) throw new Error(msg);
}

function bool(name: string, def: boolean | null): BooleanFieldSpec {
	return {
		kind: 'BOOLEAN',
		name,
		path: name,
		label: name,
		description: '',
		required: false,
		default: def
	};
}

// ── the reported case ───────────────────────────────────────────────────────

console.log('mirrorSeedFor — the reported defect');

check('a default-true boolean seeds CHECKED, not unchecked', () => {
	assert(
		mirrorSeedFor(bool('no_gravity', true)) === true,
		'default-true boolean must seed true; seeding false is the reported bug — ' +
			'it draws unchecked, so unchecking is a no-op and `false` is unauthorable'
	);
});

check('neoorigins:phantom_form no_gravity seeds true (the exact field reported)', () => {
	const fields = parsePowerSchema(powerSchema, fieldDocs, 'neoorigins:phantom_form');
	const f = fields.find((x) => x.name === 'no_gravity');
	assert(f, 'no_gravity not found on neoorigins:phantom_form');
	assert(f.kind === 'BOOLEAN', `no_gravity should be BOOLEAN, got ${f.kind}`);
	assert(f.default === true, 'schema default for no_gravity should be true');
	assert(mirrorSeedFor(f) === true, 'no_gravity must seed CHECKED — matches codec default true');
});

// ── controls: the fix must not flip anything else ───────────────────────────

console.log('mirrorSeedFor — controls');

check('a default-false boolean still seeds unchecked', () => {
	assert(mirrorSeedFor(bool('hidden', false)) === false, 'default-false must seed false');
});

check('a boolean with no declared default still seeds unchecked', () => {
	assert(mirrorSeedFor(bool('undeclared', null)) === false, 'absent default must seed false');
});

check('non-boolean kinds keep their unset-shaped seed', () => {
	const base = { name: 'x', path: 'x', label: 'x', description: '', required: false };
	const cases: [FormFieldSpec, unknown][] = [
		[{ ...base, kind: 'INTEGER', default: 5, min: null, max: null } as FormFieldSpec, null],
		[{ ...base, kind: 'NUMBER', default: 1.5, min: null, max: null } as FormFieldSpec, null],
		[{ ...base, kind: 'STRING', default: 'abc' } as FormFieldSpec, ''],
		[{ ...base, kind: 'ENUM', default: 'b', options: ['a', 'b'] } as FormFieldSpec, '']
	];
	for (const [spec, want] of cases) {
		const got = mirrorSeedFor(spec);
		assert(
			got === want,
			`${spec.kind} must seed ${JSON.stringify(want)} (unset-shaped so pruneForWire drops ` +
				`the key and the runtime default applies), got ${JSON.stringify(got)}`
		);
	}
	assert(
		JSON.stringify(mirrorSeedFor({ ...base, kind: 'OBJECT', children: [] } as FormFieldSpec)) === '{}',
		'OBJECT must seed {}'
	);
});

// ── whole-schema sweep + negative control ───────────────────────────────────

console.log('mirrorSeedFor — every boolean in the shipped schemas');

function everyBoolean(): { type: string; field: BooleanFieldSpec }[] {
	const out: { type: string; field: BooleanFieldSpec }[] = [];
	const sweep = (types: string[], get: (t: string) => FormFieldSpec[]) => {
		for (const t of types) {
			let fields: FormFieldSpec[];
			try {
				fields = get(t);
			} catch {
				// Counted, not silently dropped: a blanket catch here once hid a
				// wrong-argument-order call while still reporting green.
				unmodelled.push(t);
				continue;
			}
			const walk = (fs: FormFieldSpec[]) => {
				for (const f of fs) {
					if (f.kind === 'BOOLEAN') out.push({ type: t, field: f });
					else if (f.kind === 'OBJECT') walk(f.children);
				}
			};
			walk(fields);
		}
	};
	// Power branches use `const`; action and condition branches use `enum`.
	// Reading only `const` silently skipped two of the three schemas.
	const typesOf = (schema: {
		oneOf?: { properties?: { type?: { const?: string; enum?: unknown[] } } }[];
	}) =>
		(schema.oneOf ?? [])
			.map((b) => {
				const t = b.properties?.type;
				if (typeof t?.const === 'string') return t.const;
				const first = t?.enum?.find((x) => typeof x === 'string');
				return typeof first === 'string' ? first : undefined;
			})
			.filter((x): x is string => typeof x === 'string');

	const powerTypes = typesOf(powerSchema);
	const actionTypes = typesOf(actionSchema);
	const conditionTypes = typesOf(conditionSchema);
	coverage.power = powerTypes.length;
	coverage.action = actionTypes.length;
	coverage.condition = conditionTypes.length;

	sweep(powerTypes, (t) => parsePowerSchema(powerSchema, fieldDocs, t));
	sweep(actionTypes, (t) => parseRefSchema('action', actionSchema, fieldDocs, t));
	sweep(conditionTypes, (t) => parseRefSchema('condition', conditionSchema, fieldDocs, t));
	return out;
}

const coverage: Record<string, number> = { power: 0, action: 0, condition: 0 };
const unmodelled: string[] = [];
const booleans = everyBoolean();

check('almost every type models cleanly (a mass failure is a broken sweep)', () => {
	const total = coverage.power + coverage.action + coverage.condition;
	assert(
		unmodelled.length < total * 0.1,
		`${unmodelled.length}/${total} types failed to model — that is a broken sweep, ` +
			`not exotic schemas. First few: ${unmodelled.slice(0, 5).join(', ')}`
	);
	if (unmodelled.length > 0) {
		console.log(`         ${unmodelled.length}/${total} types unmodelled (tolerated)`);
	}
});

// A sweep that stops reaching a schema makes every assertion below vacuous.
check('the sweep reaches all three schemas', () => {
	for (const [name, n] of Object.entries(coverage)) {
		assert(n > 0, `resolved 0 types from ${name}.schema.json — the sweep is not reaching it`);
	}
	console.log(
		`         types swept: power ${coverage.power}, action ${coverage.action}, ` +
			`condition ${coverage.condition}`
	);
});

check('the sweep actually found booleans (guards against a vacuous pass)', () => {
	assert(booleans.length > 0, 'found no boolean fields at all — the sweep is broken, not clean');
});

// Negative control: with no default-true booleans the check above is vacuous.
check('at least one shipped boolean defaults to true (the gate can fail)', () => {
	const trues = booleans.filter((b) => b.field.default === true);
	assert(
		trues.length > 0,
		'no default-true booleans found — the regression above would be vacuous'
	);
	console.log(
		`         ${trues.length} default-true booleans across ${booleans.length} total ` +
			'— each was unauthorable-as-false before this fix'
	);
});

check('every boolean seeds to exactly its schema default', () => {
	const bad = booleans.filter((b) => mirrorSeedFor(b.field) !== (b.field.default ?? false));
	assert(
		bad.length === 0,
		`${bad.length} boolean(s) seed to something other than their default: ` +
			bad.slice(0, 5).map((b) => `${b.type}.${b.field.name}`).join(', ')
	);
});

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed > 0 ? 1 : 0);
