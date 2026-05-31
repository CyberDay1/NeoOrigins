// Converts between the editor's `PowerDraft[]` and Blockly's JSON serialization
// state (`Blockly.serialization.workspaces` save/load shape). Working in the
// plain-state representation keeps both directions uniform and avoids the
// imperative block API.
//
// State node shape (Blockly v12):
//   { type, id?, x?, y?, fields?: {NAME: value}, inputs?: {NAME: {block}}, next?: {block} }

import type { FormFieldSpec } from '$lib/schema/FormFieldSpec';
import type { PowerDraft } from '$lib/stores/originDraft';
import { COND_ITEM_TYPE, POWER_ID_FIELD, renderOf, type BlockRegistry } from './blockRegistry';

interface BlockState {
	type: string;
	id?: string;
	x?: number;
	y?: number;
	fields?: Record<string, unknown>;
	inputs?: Record<string, { block: BlockState }>;
	next?: { block: BlockState };
}

export interface WorkspaceState {
	blocks: { languageVersion: number; blocks: BlockState[] };
}

/**
 * Deterministic Blockly block id for the power at array index `i`. Stamped on
 * load by {@link draftToState} and read back by {@link stateToDraft} so each
 * power block can be matched to the draft it came from across edits — used to
 * carry forward fields the schema doesn't model (e.g. legacy-alias power types
 * whose runtime fields have no schema branch). Blockly's own auto-generated ids
 * are 20-char random strings, so this prefix never collides with user-dragged
 * blocks.
 */
export function powerBlockId(i: number): string {
	return `neopow_${i}`;
}

// ── draft → workspace state ──────────────────────────────────────────────────

/** Encode a leaf field's value into the Blockly field representation. */
function encodeLeaf(field: FormFieldSpec, value: unknown): unknown {
	switch (field.kind) {
		case 'BOOLEAN':
			return value === true || value === 'TRUE';
		case 'INTEGER':
		case 'NUMBER':
			return typeof value === 'number' ? value : (field.default ?? 0);
		case 'ENUM':
			return typeof value === 'string' ? value : (field.default ?? field.options[0] ?? '');
		case 'STRING':
			return typeof value === 'string' ? value : (field.default ?? '');
		case 'RawJson':
			if (value === undefined || value === null) return field.default ?? '';
			return typeof value === 'string' ? value : JSON.stringify(value);
		default:
			return '';
	}
}

/** Build a non-power node (action/condition) from a `{type, …}` value object. */
function buildNode(reg: BlockRegistry, value: Record<string, unknown>): BlockState | null {
	const typeId = typeof value.type === 'string' ? value.type : '';
	const blockType = reg.blockTypeForId.get(typeId);
	const fields = reg.fieldsByTypeId.get(typeId);
	if (!blockType || !fields) return null; // unknown type — skip (rare; custom packs)
	const state: BlockState = { type: blockType };
	fillNode(reg, state, fields, value);
	return state;
}

/** Chain a list of statement nodes via `next` and return the head. */
function chain(nodes: BlockState[]): BlockState | null {
	if (nodes.length === 0) return null;
	for (let i = 0; i < nodes.length - 1; i++) {
		nodes[i].next = { block: nodes[i + 1] };
	}
	return nodes[0];
}

/** Populate a node's fields/inputs from a value object, per the field specs. */
function fillNode(
	reg: BlockRegistry,
	state: BlockState,
	fields: FormFieldSpec[],
	value: Record<string, unknown>
): void {
	const fieldsOut: Record<string, unknown> = state.fields ?? {};
	const inputsOut: Record<string, { block: BlockState }> = {};

	for (const f of fields) {
		const r = renderOf(f);
		const v = value[f.name];
		if (r.kind === 'inline') {
			fieldsOut[f.name] = encodeLeaf(f, v);
		} else if (r.kind === 'value') {
			// single condition reporter
			if (v && typeof v === 'object') {
				const child = buildNode(reg, v as Record<string, unknown>);
				if (child) inputsOut[f.name] = { block: child };
			}
		} else if (r.kind === 'statement' && r.check === 'Action') {
			// single action ref OR action array — both render as an Action stack
			if (Array.isArray(v)) {
				const head = chain(
					v
						.filter((el): el is Record<string, unknown> => !!el && typeof el === 'object')
						.map((el) => buildNode(reg, el))
						.filter((b): b is BlockState => b !== null)
				);
				if (head) inputsOut[f.name] = { block: head };
			} else if (v && typeof v === 'object') {
				const child = buildNode(reg, v as Record<string, unknown>);
				if (child) inputsOut[f.name] = { block: child };
			}
		} else {
			// condition array → stack of cond_item wrappers
			if (Array.isArray(v)) {
				const items: BlockState[] = [];
				for (const el of v) {
					if (!el || typeof el !== 'object') continue;
					const child = buildNode(reg, el as Record<string, unknown>);
					if (!child) continue;
					items.push({ type: COND_ITEM_TYPE, inputs: { ITEM: { block: child } } });
				}
				const head = chain(items);
				if (head) inputsOut[f.name] = { block: head };
			}
		}
	}

	if (Object.keys(fieldsOut).length > 0) state.fields = fieldsOut;
	if (Object.keys(inputsOut).length > 0) state.inputs = inputsOut;
}

/** Convert the full powers array into a loadable workspace state. */
export function draftToState(reg: BlockRegistry, powers: PowerDraft[]): WorkspaceState {
	const blocks: BlockState[] = [];
	powers.forEach((power, i) => {
		const blockType = reg.blockTypeForId.get(power.type);
		const fields = reg.fieldsByTypeId.get(power.type);
		if (!blockType || !fields) return; // unknown power type — skipped in this view
		const state: BlockState = {
			type: blockType,
			id: powerBlockId(i),
			x: 40,
			y: 40 + i * 240,
			fields: { [POWER_ID_FIELD]: power.id }
		};
		// Render every field the schema models. Fields the schema does NOT model
		// (legacy-alias power types carry runtime fields with no schema branch)
		// aren't drawn here — they're carried forward on save via the per-block
		// preserve map keyed by `state.id` (see stateToDraft).
		fillNode(reg, state, fields, power.fields ?? {});
		blocks.push(state);
	});
	return { blocks: { languageVersion: 0, blocks } };
}

// ── workspace state → draft ──────────────────────────────────────────────────

/** Decode a leaf field value out of the Blockly field representation. */
function decodeLeaf(field: FormFieldSpec, value: unknown): unknown {
	switch (field.kind) {
		case 'BOOLEAN':
			return value === true || value === 'TRUE';
		case 'INTEGER':
		case 'NUMBER':
			return typeof value === 'number' ? value : Number(value);
		case 'ENUM':
		case 'STRING':
			return value == null ? '' : String(value);
		case 'RawJson': {
			const s = value == null ? '' : String(value);
			try {
				return s === '' ? '' : JSON.parse(s);
			} catch {
				return s; // keep raw text if it isn't valid JSON yet
			}
		}
		default:
			return value;
	}
}

/** Read a node's value object (`{type, …}`) out of a block state. */
function readNode(reg: BlockRegistry, state: BlockState): Record<string, unknown> | null {
	const typeId = reg.idForBlockType.get(state.type);
	if (!typeId) return null;
	const fields = reg.fieldsByTypeId.get(typeId) ?? [];
	const out: Record<string, unknown> = { type: typeId };
	readInto(reg, out, fields, state);
	return out;
}

/** Read fields/inputs of a block state into a value object per the field specs. */
function readInto(
	reg: BlockRegistry,
	out: Record<string, unknown>,
	fields: FormFieldSpec[],
	state: BlockState
): void {
	for (const f of fields) {
		const r = renderOf(f);
		if (r.kind === 'inline') {
			const raw = state.fields?.[f.name];
			out[f.name] = decodeLeaf(f, raw);
		} else if (r.kind === 'value') {
			const child = state.inputs?.[f.name]?.block;
			if (child) {
				const node = readNode(reg, child);
				if (node) out[f.name] = node;
			}
		} else if (r.kind === 'statement' && r.check === 'Action') {
			const head = state.inputs?.[f.name]?.block;
			if (f.kind === 'ARRAY_REF') {
				const arr: unknown[] = [];
				let cur: BlockState | undefined = head;
				while (cur) {
					const node = readNode(reg, cur);
					if (node) arr.push(node);
					cur = cur.next?.block;
				}
				out[f.name] = arr;
			} else if (head) {
				// single action ref — take the first block only
				const node = readNode(reg, head);
				if (node) out[f.name] = node;
			}
		} else {
			// condition array — walk the cond_item stack, pull each ITEM
			const arr: unknown[] = [];
			let cur: BlockState | undefined = state.inputs?.[f.name]?.block;
			while (cur) {
				const inner = cur.inputs?.ITEM?.block;
				if (inner) {
					const node = readNode(reg, inner);
					if (node) arr.push(node);
				}
				cur = cur.next?.block;
			}
			out[f.name] = arr;
		}
	}
}

/**
 * Convert a saved workspace state back into the powers array.
 *
 * `preserveByBlockId` maps a power block's id (see {@link powerBlockId}) to the
 * original draft `fields` object it loaded from. Modeled fields read off the
 * block overlay those originals, so any field the schema doesn't model (legacy
 * aliases, hand-authored extras) survives the round-trip instead of being wiped
 * when the canvas re-serializes. Mirrors the form editor's patch-don't-replace
 * behaviour. Omit the map (or miss a block id, e.g. a freshly dragged block) to
 * get a clean rebuild from modeled fields only.
 */
export function stateToDraft(
	reg: BlockRegistry,
	ws: WorkspaceState,
	preserveByBlockId?: Map<string, Record<string, unknown>>
): PowerDraft[] {
	const powers: PowerDraft[] = [];
	for (const block of ws.blocks?.blocks ?? []) {
		const typeId = reg.idForBlockType.get(block.type);
		if (!typeId) continue;
		const fields = reg.fieldsByTypeId.get(typeId) ?? [];
		const valueObj: Record<string, unknown> = { type: typeId };
		readInto(reg, valueObj, fields, block);
		delete valueObj.type;
		const id = typeof block.fields?.[POWER_ID_FIELD] === 'string'
			? (block.fields[POWER_ID_FIELD] as string)
			: '';
		const preserved = block.id ? preserveByBlockId?.get(block.id) : undefined;
		const merged = preserved ? { ...preserved, ...valueObj } : valueObj;
		powers.push({ id, type: typeId, fields: merged });
	}
	return powers;
}
