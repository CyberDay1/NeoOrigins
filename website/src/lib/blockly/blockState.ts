// Converts between the editor's `PowerDraft[]` and Blockly's JSON serialization
// state (`Blockly.serialization.workspaces` save/load shape). Working in the
// plain-state representation keeps both directions uniform and avoids the
// imperative block API.
//
// State node shape (Blockly v12):
//   { type, id?, x?, y?, fields?: {NAME: value}, inputs?: {NAME: {block}}, next?: {block} }

import type { ArrayRefFieldSpec, FormFieldSpec } from '$lib/schema/FormFieldSpec';
import { asRefList, fromRefList } from '$lib/schema/FormFieldSpec';
import type { PowerDraft } from '$lib/stores/originDraft';
import {
	BLOCK_COND_ITEM_TYPE,
	COND_ITEM_TYPE,
	ITEM_COND_ITEM_TYPE,
	STR_ITEM_TYPE,
	objChildFieldName,
	POWER_ID_FIELD,
	regKey,
	renderOf,
	type BlockKind,
	type BlockRegistry
} from './blockRegistry';

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
			// Unauthored booleans must take the schema default — encoding them as
			// unchecked silently flips default-true fields (e.g. `should_render`).
			return typeof value === 'boolean' ? value : ((field.default ?? false) === true);
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

/** Build a non-power node from a `{type, …}` value object. `kind` is the kind
 *  the containing slot expects — the bare typeId is ambiguous across kinds. */
function buildNode(
	reg: BlockRegistry,
	kind: BlockKind,
	value: Record<string, unknown>
): BlockState | null {
	const typeId = typeof value.type === 'string' ? value.type : '';
	const key = regKey(kind, typeId);
	const blockType = reg.blockTypeForId.get(key);
	const fields = reg.fieldsByTypeId.get(key);
	if (!blockType || !fields) return null; // unknown type — skip (rare; custom packs)
	const state: BlockState = { type: blockType };
	fillNode(reg, state, fields, value);
	return state;
}

/** The block kind a value/statement slot's `check` string accepts. */
function kindForCheck(check: string): BlockKind {
	switch (check) {
		case 'Condition':
		case 'CondItem':
			return 'condition';
		case 'BlockCondition':
		case 'BlockCondItem':
			return 'block_condition';
		case 'ItemCondition':
		case 'ItemCondItem':
			return 'item_condition';
		case 'ItemAction':
			return 'item_action';
		default:
			return 'action';
	}
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
		} else if (r.kind === 'object') {
			// Encode each leaf child into a flat `<obj>.<child>` field. The object
			// value may be absent/partial — encodeLeaf falls back to the child default.
			const objVal = v && typeof v === 'object' ? (v as Record<string, unknown>) : {};
			for (const child of r.children) {
				const cr = renderOf(child);
				if (cr.kind !== 'inline') continue;
				fieldsOut[objChildFieldName(f.name, child.name)] = encodeLeaf(child, objVal[child.name]);
			}
		} else if (r.kind === 'value') {
			// single condition reporter
			if (v && typeof v === 'object') {
				const child = buildNode(reg, kindForCheck(r.check), v as Record<string, unknown>);
				if (child) inputsOut[f.name] = { block: child };
			}
		} else if (r.kind === 'statement' && (r.check === 'Action' || r.check === 'ItemAction')) {
			// single action ref OR action array — both render as a statement stack
			// (entity Action or ItemAction; both chain directly via `next`).
			const childKind = kindForCheck(r.check);
			if (Array.isArray(v)) {
				const head = chain(
					v
						.filter((el): el is Record<string, unknown> => !!el && typeof el === 'object')
						.map((el) => buildNode(reg, childKind, el))
						.filter((b): b is BlockState => b !== null)
				);
				if (head) inputsOut[f.name] = { block: head };
			} else if (v && typeof v === 'object') {
				const child = buildNode(reg, childKind, v as Record<string, unknown>);
				if (child) inputsOut[f.name] = { block: child };
			}
		} else if (f.kind === 'ARRAY_STRING') {
			// scalar-string array (e.g. biomes) -> stack of `neo_str_item` wrappers,
			// each holding one string in its ITEM text field.
			if (Array.isArray(v)) {
				const items: BlockState[] = [];
				for (const el of v) {
					if (typeof el !== 'string') continue;
					items.push({ type: STR_ITEM_TYPE, fields: { ITEM: el } });
				}
				const head = chain(items);
				if (head) inputsOut[f.name] = { block: head };
			}
		} else {
			// condition / block_condition / item_condition array → stack of wrapper
			// blocks. The wrapper type depends on the list's element kind (CondItem
			// holds a Condition value, BlockCondItem a BlockCondition, ItemCondItem
			// an ItemCondition). `asRefList` also admits the bare-object scalar half
			// of the "one or many" idiom, so e.g. a `condition: {…}` on an existing
			// power loads into the stack instead of being silently dropped.
			const condList = asRefList(v);
			if (condList.length > 0) {
				const wrapperType =
					r.kind === 'statement' && r.check === 'BlockCondItem'
						? BLOCK_COND_ITEM_TYPE
						: r.kind === 'statement' && r.check === 'ItemCondItem'
							? ITEM_COND_ITEM_TYPE
							: COND_ITEM_TYPE;
				const itemKind = kindForCheck(r.kind === 'statement' ? r.check : 'CondItem');
				const items: BlockState[] = [];
				for (const el of condList) {
					if (!el || typeof el !== 'object') continue;
					const child = buildNode(reg, itemKind, el as Record<string, unknown>);
					if (!child) continue;
					items.push({ type: wrapperType, inputs: { ITEM: { block: child } } });
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
		const blockType = reg.blockTypeForId.get(regKey('power', power.type));
		const fields = reg.fieldsByTypeId.get(regKey('power', power.type));
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
	const entry = reg.idForBlockType.get(state.type);
	if (!entry) return null;
	const fields = reg.fieldsByTypeId.get(regKey(entry.kind, entry.typeId)) ?? [];
	const out: Record<string, unknown> = { type: entry.typeId };
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
		} else if (r.kind === 'object') {
			// Reassemble the nested object from its flat `<obj>.<child>` fields.
			const obj: Record<string, unknown> = {};
			for (const child of r.children) {
				const cr = renderOf(child);
				if (cr.kind !== 'inline') continue;
				obj[child.name] = decodeLeaf(child, state.fields?.[objChildFieldName(f.name, child.name)]);
			}
			out[f.name] = obj;
		} else if (r.kind === 'value') {
			const child = state.inputs?.[f.name]?.block;
			if (child) {
				const node = readNode(reg, child);
				if (node) out[f.name] = node;
			}
		} else if (r.kind === 'statement' && (r.check === 'Action' || r.check === 'ItemAction')) {
			const head = state.inputs?.[f.name]?.block;
			if (f.kind === 'ARRAY_REF') {
				const arr: unknown[] = [];
				let cur: BlockState | undefined = head;
				while (cur) {
					const node = readNode(reg, cur);
					if (node) arr.push(node);
					cur = cur.next?.block;
				}
				out[f.name] = fromRefList(f, arr);
			} else if (head) {
				// single action ref — take the first block only
				const node = readNode(reg, head);
				if (node) out[f.name] = node;
			}
		} else if (f.kind === 'ARRAY_STRING') {
			// scalar-string array -> walk the str_item stack, pull each ITEM field.
			const arr: string[] = [];
			let cur: BlockState | undefined = state.inputs?.[f.name]?.block;
			while (cur) {
				const sv = cur.fields?.ITEM;
				if (typeof sv === 'string') arr.push(sv);
				cur = cur.next?.block;
			}
			out[f.name] = arr;
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
			// Only ARRAY_REF fields render as a wrapper stack (a single condition
			// REF is a `value` input), so collapsing via the spec is safe here.
			out[f.name] = f.kind === 'ARRAY_REF' ? fromRefList(f as ArrayRefFieldSpec, arr) : arr;
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
		const entry = reg.idForBlockType.get(block.type);
		// Only power blocks are roots — a stray condition/action left loose on the
		// canvas must not be serialized as a power.
		if (!entry || entry.kind !== 'power') continue;
		const fields = reg.fieldsByTypeId.get(regKey('power', entry.typeId)) ?? [];
		const valueObj: Record<string, unknown> = { type: entry.typeId };
		readInto(reg, valueObj, fields, block);
		delete valueObj.type;
		const id = typeof block.fields?.[POWER_ID_FIELD] === 'string'
			? (block.fields[POWER_ID_FIELD] as string)
			: '';
		const preserved = block.id ? preserveByBlockId?.get(block.id) : undefined;
		const merged = preserved ? { ...preserved, ...valueObj } : valueObj;
		powers.push({ id, type: entry.typeId, fields: merged });
	}
	return powers;
}
