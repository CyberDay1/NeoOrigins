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

import { parsePowerSchema, parseRefSchema, refTypeOptions } from '../SchemaFormModel.js';
import type { FormFieldSpec } from '../FormFieldSpec.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
// website/src/lib/schema/__tests__/ → repo root → docs/schema
const repoRoot = resolve(__dirname, '../../../../..');
const schemaDir = resolve(repoRoot, 'docs/schema');

const powerSchema = JSON.parse(
	readFileSync(resolve(schemaDir, 'power.schema.json'), 'utf-8')
);
const actionSchema = JSON.parse(
	readFileSync(resolve(schemaDir, 'action.schema.json'), 'utf-8')
);
const conditionSchema = JSON.parse(
	readFileSync(resolve(schemaDir, 'condition.schema.json'), 'utf-8')
);
const blockConditionSchema = JSON.parse(
	readFileSync(resolve(schemaDir, 'block_condition.schema.json'), 'utf-8')
);
const itemConditionSchema = JSON.parse(
	readFileSync(resolve(schemaDir, 'item_condition.schema.json'), 'utf-8')
);
const itemActionSchema = JSON.parse(
	readFileSync(resolve(schemaDir, 'item_action.schema.json'), 'utf-8')
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

check('neoorigins:resource — hud_render is OBJECT with label/color/should_render children', () => {
	const fields = parsePowerSchema(powerSchema, fieldDocs, 'neoorigins:resource');
	const hud = findField(fields, 'hud_render');
	assert(hud.kind === 'OBJECT', `hud_render should be OBJECT, got ${hud.kind}`);
	if (hud.kind === 'OBJECT') {
		const childNames = hud.children.map((c) => c.name);
		for (const c of ['label', 'color', 'should_render']) {
			assert(childNames.includes(c),
				`hud_render child ${c} missing (have: ${childNames.join(', ')})`);
		}
		const label = findField(hud.children, 'label');
		assert(label.kind === 'STRING', `hud_render.label should be STRING, got ${label.kind}`);
		const shouldRender = findField(hud.children, 'should_render');
		assert(shouldRender.kind === 'BOOLEAN',
			`hud_render.should_render should be BOOLEAN, got ${shouldRender.kind}`);
		if (shouldRender.kind === 'BOOLEAN') {
			assert(shouldRender.default === true,
				`hud_render.should_render.default should be true, got ${shouldRender.default}`);
		}
	}
});

// ── D4: cross-document action/condition refs → REF / ARRAY_REF ───────────────

console.log('\nparsePowerSchema — D4 cross-document refs');

// The power schema spells these as the Apoli "one or many" idiom
// (`oneOf: [{$ref: X}, {type: array, items: {$ref: X}}]`), NOT a bare `$ref`, so
// they classify as ARRAY_REF with `scalarOrArray: true` — one add/remove list,
// serialized back as a bare object when there is exactly one entry.
check('neoorigins:condition_passive — condition→ARRAY_REF(condition), entity_action/else_action→ARRAY_REF(action)', () => {
	const fields = parsePowerSchema(powerSchema, fieldDocs, 'neoorigins:condition_passive');
	const cond = findField(fields, 'condition');
	assert(cond.kind === 'ARRAY_REF', `condition should be ARRAY_REF, got ${cond.kind}`);
	if (cond.kind === 'ARRAY_REF') {
		assert(cond.refDoc === 'condition', `condition.refDoc should be 'condition', got ${cond.refDoc}`);
		assert(cond.scalarOrArray, 'condition should be flagged scalarOrArray');
	}
	for (const actionField of ['entity_action', 'else_action']) {
		const f = findField(fields, actionField);
		assert(f.kind === 'ARRAY_REF', `${actionField} should be ARRAY_REF, got ${f.kind}`);
		if (f.kind === 'ARRAY_REF') {
			assert(f.refDoc === 'action', `${actionField}.refDoc should be 'action', got ${f.refDoc}`);
			assert(f.scalarOrArray, `${actionField} should be flagged scalarOrArray`);
		}
	}
});

check('scalar-or-array ref fields never degrade to RawJson — whole-schema sweep', () => {
	// Structural sweep: walk every power branch, find every property whose
	// `oneOf` is entirely `$ref` / array-of-the-same-`$ref` branches, and assert
	// the walker classified it as ARRAY_REF. Guards the regression where the
	// `oneOf`→MIXED early-return shadowed the `$ref` branch and dropped all 20
	// of these into raw-JSON textareas. Deliberately schema-content-agnostic so
	// it keeps working as the generator adds fields.
	const branches = (powerSchema as { oneOf?: unknown[] }).oneOf ?? [];
	let checked = 0;
	for (const branch of branches) {
		if (!branch || typeof branch !== 'object') continue;
		const b = branch as { properties?: Record<string, unknown>; $comment?: unknown };
		const typeProp = b.properties?.['type'] as { const?: string; enum?: string[] } | undefined;
		const typeId = typeProp?.const ?? typeProp?.enum?.[0]
			?? (typeof b.$comment === 'string' ? b.$comment.split(/[\s\u2014]/)[0] : undefined);
		if (!typeId) continue;
		const scalarOrArrayNames: string[] = [];
		for (const [name, raw] of Object.entries(b.properties ?? {})) {
			const p = raw as { oneOf?: unknown[] } | null;
			if (!p || typeof p !== 'object' || !Array.isArray(p.oneOf)) continue;
			const allRefs = p.oneOf.every((o) => {
				const e = o as { $ref?: unknown; type?: unknown; items?: { $ref?: unknown } };
				return typeof e?.$ref === 'string'
					|| (e?.type === 'array' && typeof e?.items?.$ref === 'string');
			});
			if (allRefs) scalarOrArrayNames.push(name);
		}
		if (scalarOrArrayNames.length === 0) continue;
		const fields = parsePowerSchema(powerSchema, fieldDocs, typeId);
		for (const name of scalarOrArrayNames) {
			const f = findField(fields, name);
			assert(f.kind === 'ARRAY_REF',
				`${typeId}.${name} should be ARRAY_REF (scalar-or-array ref), got ${f.kind}` +
					(f.kind === 'RawJson' ? `(${f.reason})` : ''));
			checked++;
		}
	}
	assert(checked >= 20,
		`expected at least the 20 known scalar-or-array ref fields, swept ${checked}`);
});

check('string|object oneOf unions still fall to RawJson(MIXED)', () => {
	// The fix must be narrow: only all-`$ref` unions get promoted. `name` /
	// `description` (string | object) and e.g. `summon_minion.head` stay MIXED.
	const fields = parsePowerSchema(powerSchema, fieldDocs, 'neoorigins:condition_passive');
	const name = findField(fields, 'name');
	assert(name.kind === 'RawJson' && name.reason === 'MIXED',
		`name should stay RawJson(MIXED), got ${name.kind}`);
});

console.log('\nparseRefSchema');

check('action neoorigins:and — actions is ARRAY_REF(action)', () => {
	const fields = parseRefSchema('action', actionSchema, fieldDocs, 'neoorigins:and');
	const actions = findField(fields, 'actions');
	assert(actions.kind === 'ARRAY_REF', `actions should be ARRAY_REF, got ${actions.kind}`);
	if (actions.kind === 'ARRAY_REF') {
		assert(actions.refDoc === 'action', `actions.refDoc should be 'action', got ${actions.refDoc}`);
	}
});

check('action neoorigins:give — stack is OBJECT with item/count children (item_stack shape)', () => {
	const fields = parseRefSchema('action', actionSchema, fieldDocs, 'neoorigins:give');
	const stack = findField(fields, 'stack');
	assert(stack.kind === 'OBJECT', `stack should be OBJECT, got ${stack.kind}`);
	if (stack.kind === 'OBJECT') {
		const childNames = stack.children.map((c) => c.name);
		for (const c of ['item', 'count']) {
			assert(childNames.includes(c), `stack child ${c} missing (have: ${childNames.join(', ')})`);
		}
		const item = findField(stack.children, 'item');
		assert(item.kind === 'STRING', `stack.item should be STRING, got ${item.kind}`);
		const count = findField(stack.children, 'count');
		assert(count.kind === 'INTEGER', `stack.count should be INTEGER, got ${count.kind}`);
	}
});

check('action neoorigins:area_of_effect — entity_condition→REF(condition), block_action_at→REF(action)', () => {
	const aoe = parseRefSchema('action', actionSchema, fieldDocs, 'neoorigins:area_of_effect');
	const entityCond = findField(aoe, 'entity_condition');
	assert(entityCond.kind === 'REF' && entityCond.refDoc === 'condition',
		`area_of_effect.entity_condition should be REF(condition), got ${entityCond.kind}/${entityCond.kind === 'REF' ? entityCond.refDoc : '—'}`);
	const baa = parseRefSchema('action', actionSchema, fieldDocs, 'neoorigins:block_action_at');
	const blockAction = findField(baa, 'block_action');
	assert(blockAction.kind === 'REF' && blockAction.refDoc === 'action',
		`block_action_at.block_action should be REF(action), got ${blockAction.kind}/${blockAction.kind === 'REF' ? blockAction.refDoc : '—'}`);
});

check('action neoorigins:teleport_to_marker — position is OBJECT with x/y/z NUMBER children', () => {
	const fields = parseRefSchema('action', actionSchema, fieldDocs, 'neoorigins:teleport_to_marker');
	const position = findField(fields, 'position');
	assert(position.kind === 'OBJECT', `position should be OBJECT, got ${position.kind}`);
	if (position.kind === 'OBJECT') {
		for (const c of ['x', 'y', 'z']) {
			const child = findField(position.children, c);
			assert(child.kind === 'NUMBER', `position.${c} should be NUMBER, got ${child.kind}`);
		}
	}
});

check('action neoorigins:damage_attacker — source is OBJECT with name STRING child', () => {
	const fields = parseRefSchema('action', actionSchema, fieldDocs, 'neoorigins:damage_attacker');
	const source = findField(fields, 'source');
	assert(source.kind === 'OBJECT', `source should be OBJECT, got ${source.kind}`);
	if (source.kind === 'OBJECT') {
		const name = findField(source.children, 'name');
		assert(name.kind === 'STRING', `source.name should be STRING, got ${name.kind}`);
	}
});

check('action entity-filter conditions → REF(condition), not REF(action) [latent #-ref fix]', () => {
	for (const [type, field] of [
		['neoorigins:chain_to_nearest', 'target_condition'],
		['neoorigins:swap_with_entity', 'target_condition']
	] as const) {
		const fields = parseRefSchema('action', actionSchema, fieldDocs, type);
		const f = findField(fields, field);
		assert(f.kind === 'REF' && f.refDoc === 'condition',
			`${type}.${field} should be REF(condition), got ${f.kind}/${f.kind === 'REF' ? f.refDoc : '—'}`);
	}
});

check('action neoorigins:if_else — condition→REF(condition), if_action/else_action→REF(action)', () => {
	const fields = parseRefSchema('action', actionSchema, fieldDocs, 'neoorigins:if_else');
	const cond = findField(fields, 'condition');
	assert(cond.kind === 'REF' && cond.refDoc === 'condition',
		`condition should be REF(condition), got ${cond.kind}/${cond.kind === 'REF' ? cond.refDoc : '—'}`);
	for (const a of ['if_action', 'else_action']) {
		const f = findField(fields, a);
		assert(f.kind === 'REF' && f.refDoc === 'action',
			`${a} should be REF(action), got ${f.kind}`);
	}
	// `if_else` carries no common-root fields (action root is just `type`).
	assert(!fields.some((f) => f.name === 'name'), 'action branch should not emit common `name`');
});

check('power neoorigins:modify_player_spawn — location.biomes is ARRAY_STRING with pattern', () => {
	const fields = parsePowerSchema(powerSchema, fieldDocs, 'neoorigins:modify_player_spawn');
	const location = findField(fields, 'location');
	assert(location.kind === 'OBJECT', `location should be OBJECT, got ${location.kind}`);
	if (location.kind === 'OBJECT') {
		const biomes = findField(location.children, 'biomes');
		assert(biomes.kind === 'ARRAY_STRING',
			`location.biomes should be ARRAY_STRING, got ${biomes.kind}`);
		if (biomes.kind === 'ARRAY_STRING') {
			assert(biomes.pattern != null && biomes.pattern.includes(':'),
				`location.biomes.pattern should carry the resource-location regex, got ${biomes.pattern}`);
		}
	}
});

check('action apace:and — alias matched via branch type.enum', () => {
	const fields = parseRefSchema('action', actionSchema, fieldDocs, 'apace:and');
	const actions = findField(fields, 'actions');
	assert(actions.kind === 'ARRAY_REF', `apace:and actions should be ARRAY_REF, got ${actions.kind}`);
});

check('condition schema parses + has a type universe', () => {
	const opts = refTypeOptions(conditionSchema);
	assert(opts.length > 0, 'condition type universe empty');
	assert(opts.includes('neoorigins:and'), 'condition enum should include neoorigins:and');
});

check('refTypeOptions dedups the (hand-written) duplicated action enum', () => {
	const opts = refTypeOptions(actionSchema);
	const set = new Set(opts);
	assert(set.size === opts.length, 'refTypeOptions returned duplicates');
	assert(opts.includes('neoorigins:damage'), 'action enum should include neoorigins:damage');
});

check('unknown action type id — explicit error', () => {
	let threw = false;
	try {
		parseRefSchema('action', actionSchema, fieldDocs, 'neoorigins:bogus_action');
	} catch (e) {
		threw = true;
		const msg = e instanceof Error ? e.message : String(e);
		assert(msg.includes('not in schema enum'), `expected "not in schema enum", got: ${msg}`);
	}
	assert(threw, 'expected parseRefSchema to throw on unknown action id');
});

console.log('\nparseRefSchema — block_condition');

check('block_condition neoorigins:and — conditions is ARRAY_REF(block_condition)', () => {
	const fields = parseRefSchema('block_condition', blockConditionSchema, fieldDocs, 'neoorigins:and');
	const conds = findField(fields, 'conditions');
	assert(conds.kind === 'ARRAY_REF', `conditions should be ARRAY_REF, got ${conds.kind}`);
	if (conds.kind === 'ARRAY_REF') {
		assert(conds.refDoc === 'block_condition',
			`conditions.refDoc should be 'block_condition', got ${conds.refDoc}`);
	}
});

check('block_condition neoorigins:in_tag — tag is required STRING', () => {
	const fields = parseRefSchema('block_condition', blockConditionSchema, fieldDocs, 'neoorigins:in_tag');
	const tag = findField(fields, 'tag');
	assert(tag.kind === 'STRING', `tag should be STRING, got ${tag.kind}`);
	assert(tag.required, 'tag should be required on in_tag');
});

check('block_condition type universe includes neoorigins:block', () => {
	const opts = refTypeOptions(blockConditionSchema);
	assert(opts.includes('neoorigins:block'), 'block_condition enum should include neoorigins:block');
});

check('condition neoorigins:on_block — block_condition→REF(block_condition)', () => {
	const fields = parseRefSchema('condition', conditionSchema, fieldDocs, 'neoorigins:on_block');
	const bc = findField(fields, 'block_condition');
	assert(bc.kind === 'REF' && bc.refDoc === 'block_condition',
		`block_condition should be REF(block_condition), got ${bc.kind}/${bc.kind === 'REF' ? bc.refDoc : '—'}`);
});

console.log('\nparseRefSchema — item_condition');

check('item_condition neoorigins:and — conditions is ARRAY_REF(item_condition)', () => {
	const fields = parseRefSchema('item_condition', itemConditionSchema, fieldDocs, 'neoorigins:and');
	const conds = findField(fields, 'conditions');
	assert(conds.kind === 'ARRAY_REF', `conditions should be ARRAY_REF, got ${conds.kind}`);
	if (conds.kind === 'ARRAY_REF') {
		assert(conds.refDoc === 'item_condition',
			`conditions.refDoc should be 'item_condition', got ${conds.refDoc}`);
	}
});

check('item_condition neoorigins:not — condition is REF(item_condition)', () => {
	const fields = parseRefSchema('item_condition', itemConditionSchema, fieldDocs, 'neoorigins:not');
	const cond = findField(fields, 'condition');
	assert(cond.kind === 'REF' && cond.refDoc === 'item_condition',
		`condition should be REF(item_condition), got ${cond.kind}/${cond.kind === 'REF' ? cond.refDoc : '—'}`);
});

check('item_condition neoorigins:enchantment — enchantment is required STRING', () => {
	const fields = parseRefSchema('item_condition', itemConditionSchema, fieldDocs, 'neoorigins:enchantment');
	const ench = findField(fields, 'enchantment');
	assert(ench.kind === 'STRING', `enchantment should be STRING, got ${ench.kind}`);
	assert(ench.required, 'enchantment should be required on enchantment');
});

check('item_condition type universe includes neoorigins:ingredient', () => {
	const opts = refTypeOptions(itemConditionSchema);
	assert(opts.includes('neoorigins:ingredient'),
		'item_condition enum should include neoorigins:ingredient');
});

check('condition neoorigins:equipped_item — item_condition→REF(item_condition)', () => {
	const fields = parseRefSchema('condition', conditionSchema, fieldDocs, 'neoorigins:equipped_item');
	const ic = findField(fields, 'item_condition');
	assert(ic.kind === 'REF' && ic.refDoc === 'item_condition',
		`item_condition should be REF(item_condition), got ${ic.kind}/${ic.kind === 'REF' ? ic.refDoc : '—'}`);
});

console.log('\nparseRefSchema — item_action');

check('item_action neoorigins:and — actions is ARRAY_REF(item_action)', () => {
	const fields = parseRefSchema('item_action', itemActionSchema, fieldDocs, 'neoorigins:and');
	const actions = findField(fields, 'actions');
	assert(actions.kind === 'ARRAY_REF', `actions should be ARRAY_REF, got ${actions.kind}`);
	if (actions.kind === 'ARRAY_REF') {
		assert(actions.refDoc === 'item_action',
			`actions.refDoc should be 'item_action', got ${actions.refDoc}`);
	}
});

check('item_action neoorigins:if_else — if_action REF(item_action), condition REF(item_condition)', () => {
	const fields = parseRefSchema('item_action', itemActionSchema, fieldDocs, 'neoorigins:if_else');
	const ifAction = findField(fields, 'if_action');
	assert(ifAction.kind === 'REF' && ifAction.refDoc === 'item_action',
		`if_action should be REF(item_action), got ${ifAction.kind}/${ifAction.kind === 'REF' ? ifAction.refDoc : '—'}`);
	const cond = findField(fields, 'condition');
	assert(cond.kind === 'REF' && cond.refDoc === 'item_condition',
		`condition should be REF(item_condition), got ${cond.kind}/${cond.kind === 'REF' ? cond.refDoc : '—'}`);
});

check('item_action neoorigins:consume — amount is INTEGER', () => {
	const fields = parseRefSchema('item_action', itemActionSchema, fieldDocs, 'neoorigins:consume');
	const amount = findField(fields, 'amount');
	assert(amount.kind === 'INTEGER', `amount should be INTEGER, got ${amount.kind}`);
});

check('item_action type universe includes neoorigins:damage', () => {
	const opts = refTypeOptions(itemActionSchema);
	assert(opts.includes('neoorigins:damage'), 'item_action enum should include neoorigins:damage');
});

check('action neoorigins:modify_inventory — item_action→REF(item_action)', () => {
	const fields = parseRefSchema('action', actionSchema, fieldDocs, 'neoorigins:modify_inventory');
	const ia = findField(fields, 'item_action');
	assert(ia.kind === 'REF' && ia.refDoc === 'item_action',
		`item_action should be REF(item_action), got ${ia.kind}/${ia.kind === 'REF' ? ia.refDoc : '—'}`);
});

check('action neoorigins:equipped_item_action — item_action→REF(item_action) + equipment_slot ENUM', () => {
	const fields = parseRefSchema('action', actionSchema, fieldDocs, 'neoorigins:equipped_item_action');
	// The canonical key is `item_action`. The legacy `action` key is a
	// PARSER-ONLY alias (see the "item_action" / "action" fallback in
	// src/main/java/com/cyberday1/neoorigins/compat/action/ActionParser.java
	// #parseEquippedItemAction) — deliberately undocumented and absent from the
	// schema, so the editor must never advertise it.
	const act = findField(fields, 'item_action');
	assert(act.kind === 'REF' && act.refDoc === 'item_action',
		`item_action should be REF(item_action), got ${act.kind}/${act.kind === 'REF' ? act.refDoc : '—'}`);
	const names = fields.map((f) => f.name);
	assert(!names.includes('action'),
		`legacy alias \`action\` must stay out of the schema (have: ${names.join(', ')})`);
	const slot = findField(fields, 'equipment_slot');
	assert(slot.kind === 'ENUM', `equipment_slot should be ENUM, got ${slot.kind}`);
	if (slot.kind === 'ENUM') {
		assert(slot.options.includes('mainhand'),
			`equipment_slot options should include mainhand, got ${slot.options.join(', ')}`);
	}
});

console.log(`\n${passed} passed, ${failed} failed`);
if (failed > 0) {
	process.exit(1);
}
