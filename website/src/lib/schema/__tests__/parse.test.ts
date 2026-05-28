// Smoke tests for `parsePowerSchema` against the real `power.schema.json`
// and `field_docs.json` committed in `docs/schema/`. Run with:
//
//   npm run check:schema
//
// (under the hood: `tsx src/lib/schema/__tests__/parse.test.ts`).
//
// The runner is intentionally framework-free — we throw on failure, log
// successes, and exit non-zero on any assertion. The next agent can wire
// this into svelte-check / a real runner if they want.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { parsePowerSchema } from '../SchemaFormModel.js';
import type { FormFieldSpec } from '../FormFieldSpec.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
// website/src/lib/schema/__tests__/ → repo root → docs/schema
const repoRoot = resolve(__dirname, '../../../../..');
const schemaDir = resolve(repoRoot, 'docs/schema');

const powerSchema = JSON.parse(
	readFileSync(resolve(schemaDir, 'power.schema.json'), 'utf-8')
);
const fieldDocs = JSON.parse(
	readFileSync(resolve(schemaDir, 'field_docs.json'), 'utf-8')
);

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

function findField(fields: FormFieldSpec[], name: string): FormFieldSpec {
	const f = fields.find((x) => x.name === name);
	if (!f) throw new Error(`field not found: ${name} (have: ${fields.map((x) => x.name).join(', ')})`);
	return f;
}

console.log('parsePowerSchema');

check('neoorigins:starting_equipment — branch found, common + branch fields emitted', () => {
	const fields = parsePowerSchema(powerSchema, fieldDocs, 'neoorigins:starting_equipment');
	assert(fields.length > 0, 'empty field list');

	// Common fields first, then branch fields. The common-root properties
	// are: name, description, hidden (in some order — Object.entries
	// preserves declaration order). 'type' is skipped.
	const names = fields.map((f) => f.name);
	assert(names.includes('name'), '`name` missing');
	assert(names.includes('description'), '`description` missing');
	assert(names.includes('hidden'), '`hidden` missing');
	assert(!names.includes('type'), '`type` should NOT be emitted');

	// Branch fields:
	assert(names.includes('grant_id'), '`grant_id` missing');
	assert(names.includes('item'), '`item` missing');
	assert(names.includes('count'), '`count` missing');
	assert(names.includes('stacks'), '`stacks` missing');

	// Kind smoke checks.
	const grantId = findField(fields, 'grant_id');
	assert(grantId.kind === 'STRING', `grant_id should be STRING, got ${grantId.kind}`);
	assert(grantId.required, 'grant_id should be required');

	const count = findField(fields, 'count');
	assert(count.kind === 'INTEGER', `count should be INTEGER, got ${count.kind}`);
	if (count.kind === 'INTEGER') {
		assert(count.min === 1, `count.min should be 1, got ${count.min}`);
	}

	const hidden = findField(fields, 'hidden');
	assert(hidden.kind === 'BOOLEAN', `hidden should be BOOLEAN, got ${hidden.kind}`);
	if (hidden.kind === 'BOOLEAN') {
		assert(hidden.default === false, `hidden.default should be false, got ${hidden.default}`);
	}

	// `stacks` is type: array → falls to RawJson(ARRAY).
	const stacks = findField(fields, 'stacks');
	assert(stacks.kind === 'RawJson', `stacks should be RawJson, got ${stacks.kind}`);
	if (stacks.kind === 'RawJson') {
		assert(stacks.reason === 'ARRAY', `stacks.reason should be ARRAY, got ${stacks.reason}`);
	}

	// `name` / `description` are oneOf string|object → RawJson(MIXED).
	const nameField = findField(fields, 'name');
	assert(nameField.kind === 'RawJson', `name should be RawJson, got ${nameField.kind}`);
	if (nameField.kind === 'RawJson') {
		assert(nameField.reason === 'MIXED', `name.reason should be MIXED, got ${nameField.reason}`);
	}

	// Field-docs label: grant_id has a per-type docstring.
	assert(grantId.description.includes('granted only once'),
		`grant_id description should pull from field_docs.json, got: ${grantId.description}`);
});

check('neoorigins:loot_pool_grant — added 2026-05-28, schema-driven', () => {
	const fields = parsePowerSchema(powerSchema, fieldDocs, 'neoorigins:loot_pool_grant');
	assert(fields.length > 0, 'empty field list');
	const names = fields.map((f) => f.name);
	for (const required of ['grant_id', 'loot_table', 'rolls', 'bonus_rolls', 'cooldown']) {
		assert(names.includes(required), `${required} missing`);
	}

	const lootTable = findField(fields, 'loot_table');
	assert(lootTable.kind === 'STRING', `loot_table should be STRING, got ${lootTable.kind}`);
	if (lootTable.kind === 'STRING') {
		assert(lootTable.pattern !== null, 'loot_table should carry its regex pattern');
	}
	assert(lootTable.required, 'loot_table should be required');

	const rolls = findField(fields, 'rolls');
	assert(rolls.kind === 'INTEGER', `rolls should be INTEGER, got ${rolls.kind}`);
	if (rolls.kind === 'INTEGER') {
		assert(rolls.min === 0, `rolls.min should be 0, got ${rolls.min}`);
	}
});

check('unknown power type id — explicit error', () => {
	let threw = false;
	try {
		parsePowerSchema(powerSchema, fieldDocs, 'neoorigins:bogus_does_not_exist');
	} catch (e) {
		threw = true;
		const msg = e instanceof Error ? e.message : String(e);
		assert(msg.includes('not in schema enum'),
			`expected "not in schema enum", got: ${msg}`);
	}
	assert(threw, 'expected parsePowerSchema to throw on unknown type id');
});

check('fallback branch — power in enum but with no $comment branch returns common fields only', () => {
	// Pick a power that's in the enum but not in any structured oneOf
	// branch. `neoorigins:active_bolt` is in the enum but has no $comment
	// branch (verified by inspection of power.schema.json head).
	const fields = parsePowerSchema(powerSchema, fieldDocs, 'neoorigins:active_bolt');
	const names = fields.map((f) => f.name);
	// Should at least contain the common fields and nothing branch-specific.
	assert(names.includes('name'), 'common `name` missing');
	assert(names.includes('hidden'), 'common `hidden` missing');
	assert(!names.includes('grant_id'), 'should not include branch-specific fields');
});

console.log(`\n${passed} passed, ${failed} failed`);
if (failed > 0) {
	process.exit(1);
}
