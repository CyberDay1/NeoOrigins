// Regression net for the "Blockly invented a value the author never wrote" defect.
// Framework-free to match the sibling suites: throw on failure, exit non-zero.
//
//   npm run check:blocks
//
// The block view has to put SOMETHING in every widget, so `encodeLeaf` seeds an
// unset optional with `default ?? 0 / false / ''`. `readInto` used to write all
// of those straight back, and `pruneForWire` keeps `0` and `false` on purpose,
// so one block drag turned `{"multiplier":0.5}` into a power that also carried
// `set_total: 0` — damage forced to exactly zero. The property under test is
// therefore round-trip identity: draftToState → stateToDraft must not add keys.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { buildBlockRegistry, regKey, renderOf } from '../blockRegistry.js';
import { draftToState, stateToDraft, powerBlockId, type AuthoredFields } from '../blockState.js';
import type { FormFieldSpec } from '../../schema/FormFieldSpec.js';
import type { PowerDraft } from '../../stores/originDraft.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const schemaDir = resolve(__dirname, '../../../../..', 'docs/schema');
const read = (n: string) => JSON.parse(readFileSync(resolve(schemaDir, n), 'utf-8'));

const powerSchema = read('power.schema.json');
const reg = buildBlockRegistry(powerSchema, {
	action: read('action.schema.json'),
	condition: read('condition.schema.json'),
	blockCondition: read('block_condition.schema.json'),
	itemCondition: read('item_condition.schema.json'),
	itemAction: read('item_action.schema.json'),
	fieldDocs: read('field_docs.json')
});

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

/**
 * One canvas cycle: load the powers, then serialize straight back with no edit.
 * Mirrors BlockCanvas — same authored index for both halves, same per-block
 * preserve map — so what this returns is what a stray block drag would store.
 */
function roundTrip(powers: PowerDraft[]): PowerDraft[] {
	const authored: AuthoredFields = new Map();
	const ws = draftToState(reg, powers, authored);
	const preserve = new Map<string, Record<string, unknown>>();
	powers.forEach((p, i) => preserve.set(powerBlockId(i), p.fields ?? {}));
	return stateToDraft(reg, ws, preserve, authored);
}

const power = (type: string, fields: Record<string, unknown>): PowerDraft => ({
	id: 'p',
	type,
	fields
});

// ── the reported case ───────────────────────────────────────────────────────

console.log('blockState round-trip — the reported defect');

check('a halve-damage power does not gain set_total / max_total / min_total', () => {
	const before = { multiplier: 0.5 };
	const after = roundTrip([power('neoorigins:modify_damage', before)])[0].fields;
	for (const k of ['set_total', 'max_total', 'min_total']) {
		assert(
			!(k in after),
			`${k} was invented as ${JSON.stringify(after[k])}. It is optional with NO schema ` +
				'default, so an explicit 0 means "force the damage to exactly 0" — the power ' +
				'stops halving damage and grants total immunity instead'
		);
	}
});

check('the whole modify_damage draft is identity (no key added, none lost)', () => {
	const before = { multiplier: 0.5 };
	const after = roundTrip([power('neoorigins:modify_damage', before)])[0].fields;
	assert(
		JSON.stringify(after) === JSON.stringify(before),
		`expected ${JSON.stringify(before)}, got ${JSON.stringify(after)}`
	);
});

check('an untouched attribute_modifier gains no equipment/location gate', () => {
	const before = { attribute: 'minecraft:generic.max_health', amount: 4 };
	const after = roundTrip([power('neoorigins:attribute_modifier', before)])[0].fields;
	for (const k of ['equipment_condition', 'location_condition']) {
		assert(
			!(k in after),
			`${k} was invented as ${JSON.stringify(after[k])} — an equipment_condition ` +
				'defaulting to slot "mainhand" silently disables the modifier whenever the ' +
				'main hand is empty'
		);
	}
	assert(
		JSON.stringify(after) === JSON.stringify(before),
		`expected ${JSON.stringify(before)}, got ${JSON.stringify(after)}`
	);
});

// ── whole-schema sweep ──────────────────────────────────────────────────────

console.log('blockState round-trip — every power type in the schema');

function powerTypes(): string[] {
	return ((powerSchema.oneOf ?? []) as { properties?: { type?: { const?: unknown } } }[])
		.map((b) => b.properties?.type?.const)
		.filter((t): t is string => typeof t === 'string');
}

const types = powerTypes();
const modelled = types.filter((t) => reg.blockTypeForId.has(regKey('power', t)));

check('the sweep resolves the power branches (a broken sweep passes vacuously)', () => {
	assert(modelled.length > 100, `only ${modelled.length} modelled power types — sweep is broken`);
	console.log(`         ${modelled.length}/${types.length} power types modelled`);
});

// Negative control: with no unset-and-defaultless optionals the sweep can't fail.
check('the schema really does ship defaultless optional numerics', () => {
	const hits: string[] = [];
	for (const t of modelled) {
		for (const f of reg.fieldsByTypeId.get(regKey('power', t)) ?? []) {
			const n = f as Extract<FormFieldSpec, { kind: 'NUMBER' | 'INTEGER' }>;
			if ((f.kind === 'NUMBER' || f.kind === 'INTEGER') && !f.required && n.default === null) {
				hits.push(`${t}.${f.name}`);
			}
		}
	}
	assert(hits.length > 0, 'no defaultless optional numerics — the sweep below is vacuous');
	console.log(`         ${hits.length} defaultless optional numeric field(s) take the "?? 0" path`);
});

check('an empty power round-trips to an empty power, for every type', () => {
	const bad: string[] = [];
	for (const t of modelled) {
		const after = roundTrip([power(t, {})])[0]?.fields ?? {};
		// Required fields legitimately survive; anything else is invented.
		const req = new Set(
			(reg.fieldsByTypeId.get(regKey('power', t)) ?? []).filter((f) => f.required).map((f) => f.name)
		);
		const added = Object.keys(after).filter((k) => !req.has(k));
		if (added.length > 0) bad.push(`${t}: +${added.join(',')}`);
	}
	assert(
		bad.length === 0,
		`${bad.length} power type(s) gained keys they were never given:\n           ` +
			bad.slice(0, 8).join('\n           ')
	);
});

// ── controls: the fix must not swallow real data ────────────────────────────

console.log('blockState round-trip — controls');

check('an explicitly authored 0 survives (the reason pruneForWire keeps zeros)', () => {
	const before = { multiplier: 1, set_total: 0, hidden: false };
	const after = roundTrip([power('neoorigins:modify_damage', before)])[0].fields;
	assert(
		JSON.stringify(after) === JSON.stringify(before),
		`an authored stand-in value must survive: expected ${JSON.stringify(before)}, ` +
			`got ${JSON.stringify(after)}`
	);
});

check('authored non-default values of every kind survive', () => {
	const before = {
		attribute: 'minecraft:generic.movement_speed',
		amount: -0.25,
		operation: 'multiply_total',
		equipment_condition: { slot: 'feet' }
	};
	const after = roundTrip([power('neoorigins:attribute_modifier', before)])[0].fields;
	assert(
		JSON.stringify(after) === JSON.stringify(before),
		`expected ${JSON.stringify(before)}, got ${JSON.stringify(after)}`
	);
});

check('a nested condition and a string list still round-trip', () => {
	const before = {
		condition: { type: 'neoorigins:sneaking' },
		required_mods: ['dragonsurvival']
	};
	const after = roundTrip([power('neoorigins:modify_damage', before)])[0].fields;
	assert(
		JSON.stringify(after.condition) === JSON.stringify(before.condition),
		`nested condition lost: got ${JSON.stringify(after.condition)}`
	);
	assert(
		JSON.stringify(after.required_mods) === JSON.stringify(before.required_mods),
		`string list lost: got ${JSON.stringify(after.required_mods)}`
	);
});

check('an edited field is written back (the fix must not freeze the canvas)', () => {
	const ws = draftToState(reg, [power('neoorigins:modify_damage', { multiplier: 0.5 })]);
	// Simulate the author typing into the widget.
	ws.blocks.blocks[0].fields!.set_total = 3;
	ws.blocks.blocks[0].fields!.multiplier = 2;
	const after = stateToDraft(reg, ws)[0].fields;
	assert(after.set_total === 3, `edited set_total lost: ${JSON.stringify(after.set_total)}`);
	assert(after.multiplier === 2, `edited multiplier lost: ${JSON.stringify(after.multiplier)}`);
});

check('unmodelled fields are still carried by the preserve map', () => {
	const powers = [power('neoorigins:modify_damage', { multiplier: 0.5, legacy_extra: 'keep me' })];
	const after = roundTrip(powers)[0].fields;
	assert(after.legacy_extra === 'keep me', 'preserve map regressed');
});

// ── optional object whose children are REQUIRED ──────────────────────────────
//
// A child's `required` only means "required GIVEN the object exists", so it must
// not vote on whether the object exists. `teleport_to_marker.position` is the
// live case: x/y/z are required, and ActionParser switches to absolute coords on
// `json.has("position")` alone, so an invented {0,0,0} drops the player at world
// origin instead of moving them by dy.

console.log('\nblockState round-trip — optional object with required children');

const marker = (action: Record<string, unknown>) =>
	(roundTrip([power('neoorigins:active_ability', { entity_action: action })])[0].fields
		.entity_action ?? {}) as Record<string, unknown>;

check('a relative teleport does not gain an absolute position', () => {
	const after = marker({ type: 'neoorigins:teleport_to_marker', dx: 0, dy: 10, dz: 0 });
	assert(
		!('position' in after),
		`position was invented as ${JSON.stringify(after.position)} — presence alone flips ` +
			'teleport_to_marker into absolute mode, dropping the player at world origin'
	);
});

check('an authored position round-trips verbatim', () => {
	const after = marker({ type: 'neoorigins:teleport_to_marker', position: { x: 1, y: 2, z: 3 } });
	assert(
		JSON.stringify(after.position) === JSON.stringify({ x: 1, y: 2, z: 3 }),
		`authored position lost: ${JSON.stringify(after.position)}`
	);
});

check('an authored all-zero position survives (it looks exactly like unset)', () => {
	const after = marker({ type: 'neoorigins:teleport_to_marker', position: { x: 0, y: 0, z: 0 } });
	assert(
		JSON.stringify(after.position) === JSON.stringify({ x: 0, y: 0, z: 0 }),
		`authored {0,0,0} dropped as if unset: ${JSON.stringify(after.position)} — this is the ` +
			'case the authored index exists to rescue'
	);
});

check('a partial position keeps its required siblings so it stays schema-valid', () => {
	const after = marker({ type: 'neoorigins:teleport_to_marker', position: { y: 64 } });
	assert(
		JSON.stringify(after.position) === JSON.stringify({ x: 0, y: 64, z: 0 }),
		`expected x/z filled in, got ${JSON.stringify(after.position)}`
	);
});

// ── MIXED boolean|string CHOICE (render_elytra: never / flying / always) ────
//
// A closed choice is the one RawJson shape that must NOT edit as a free-text field:
// its options cover the whole value space, so the block gets a Blockly dropdown and
// "always" stops being discoverable only by reading the docs. The rule is structural
// (arms exactly boolean+string, string arm names its values), never a name check, so
// the three powers that declare the field all pick it up.
//
// The load half is the dangerous one: a legacy boolean has to be spelled as its
// option before it reaches the widget. `JSON.stringify(true)` gives "true", which
// matches nothing in the dropdown, and Blockly falls back to the FIRST option —
// `never` — so one block drag would have stripped the wings off a shipped origin.

console.log('\nblockState — MIXED boolean|string choice');

function fieldOf(type: string, name: string): FormFieldSpec {
	const f = (reg.fieldsByTypeId.get(regKey('power', type)) ?? []).find((x) => x.name === name);
	assert(f, `${type} has no field ${name}`);
	return f;
}

const elytraPowers = ['neoorigins:elytra_flight', 'neoorigins:natural_glide', 'neoorigins:flight'];

for (const t of elytraPowers) {
	check(`${t} render_elytra is a dropdown, not a text field`, () => {
		const r = renderOf(fieldOf(t, 'render_elytra'));
		assert(r.kind === 'inline', `render_elytra should be an inline widget, got ${r.kind}`);
		if (r.kind !== 'inline') return;
		assert(
			r.arg.type === 'field_dropdown',
			`expected field_dropdown, got ${String(r.arg.type)} — "always" is untypeable otherwise`
		);
		const opts = (r.arg.options as [string, string][]).map(([, v]) => v);
		assert(
			opts.join(',') === 'never,flying,always',
			`expected never,flying,always — got ${opts.join(',')}`
		);
	});
}

const elytra = (fields: Record<string, unknown>) =>
	roundTrip([power('neoorigins:elytra_flight', fields)])[0].fields.render_elytra;

check('a legacy boolean loads onto its spelling and saves as the string', () => {
	assert(
		elytra({ render_elytra: true }) === 'flying',
		`true should save as "flying", got ${JSON.stringify(elytra({ render_elytra: true }))}`
	);
	assert(
		elytra({ render_elytra: false }) === 'never',
		`false should save as "never" — the first option is what a boolean-shaped value ` +
			`would land on by accident, so getting it right matters; got ` +
			JSON.stringify(elytra({ render_elytra: false }))
	);
});

check('the third state survives the canvas', () => {
	assert(elytra({ render_elytra: 'always' }) === 'always', 'always must round-trip verbatim');
	assert(elytra({ render_elytra: 'never' }) === 'never', 'never must round-trip verbatim');
});

check('an untouched elytra_flight gains no render_elytra', () => {
	const after = roundTrip([power('neoorigins:elytra_flight', {})])[0].fields;
	assert(
		!('render_elytra' in after),
		`render_elytra was invented as ${JSON.stringify(after.render_elytra)} — the power ` +
			'already defaults to flying, so writing it is an override nobody asked for'
	);
});

check('an OPEN MIXED union keeps its text field and its raw round-trip', () => {
	const r = renderOf(fieldOf('neoorigins:active_ability', 'key'));
	assert(r.kind === 'inline', 'key should stay an inline widget');
	if (r.kind !== 'inline') return;
	assert(
		r.arg.type === 'field_input',
		`an integer|string|object union must keep the free-text escape, got ${String(r.arg.type)}`
	);
	const before = { key: { key: 'key.neoorigins.primary' } };
	const after = roundTrip([power('neoorigins:active_ability', before)])[0].fields;
	assert(
		JSON.stringify(after.key) === JSON.stringify(before.key),
		`object-shaped key lost: ${JSON.stringify(after.key)}`
	);
});

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed > 0 ? 1 : 0);
