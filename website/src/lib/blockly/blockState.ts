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
	objItemBlockType,
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

/**
 * Which keys each block was actually built from: block id → the Blockly field /
 * input names that were PRESENT in the source JSON. Nested object children are
 * keyed by their flattened `<obj>.<child>` name.
 *
 * A Blockly field cannot render "unset" — {@link encodeLeaf} must put some value
 * in the widget — so on the way back out {@link readInto} treats a field still
 * sitting on that stand-in as unauthored and omits it. This map is what rescues
 * the author who genuinely wrote the stand-in value (`"set_total": 0`): the key
 * was present at build time, so it is emitted. Built once by
 * {@link draftToState} and kept for the session, so it survives every
 * workspace → store push (the canvas is loaded once and never reloaded).
 *
 * Residual limit: typing the stand-in value into a field that already shows it
 * is not observable, so it stays absent. Same compromise `mirrorSeedFor` makes
 * for booleans in the form view.
 */
export type AuthoredFields = Map<string, Set<string>>;

// ── draft → workspace state ──────────────────────────────────────────────────

/** Build-side state: registry, the authored index being filled, and the id counter. */
interface BuildCtx {
	reg: BlockRegistry;
	authored?: AuthoredFields;
	next: number;
}

/** Deterministic id for a non-power node, so the authored index can key on it. */
function nextNodeId(ctx: BuildCtx): string {
	return `neoblk_${ctx.next++}`;
}

/** True when `value` carries `key` as a real (non-undefined) authored entry. */
function present(value: Record<string, unknown>, key: string): boolean {
	return Object.prototype.hasOwnProperty.call(value, key) && value[key] !== undefined;
}

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
	ctx: BuildCtx,
	kind: BlockKind,
	value: Record<string, unknown>
): BlockState | null {
	const typeId = typeof value.type === 'string' ? value.type : '';
	const key = regKey(kind, typeId);
	const blockType = ctx.reg.blockTypeForId.get(key);
	const fields = ctx.reg.fieldsByTypeId.get(key);
	if (!blockType || !fields) return null; // unknown type — skip (rare; custom packs)
	const state: BlockState = { type: blockType, id: nextNodeId(ctx) };
	fillNode(ctx, state, fields, value);
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
	ctx: BuildCtx,
	state: BlockState,
	fields: FormFieldSpec[],
	value: Record<string, unknown>
): void {
	const fieldsOut: Record<string, unknown> = state.fields ?? {};
	const inputsOut: Record<string, { block: BlockState }> = {};
	// Keys that were really in the source JSON — see AuthoredFields.
	const authored = ctx.authored && state.id ? new Set<string>() : null;

	for (const f of fields) {
		const r = renderOf(f);
		const v = value[f.name];
		if (authored && present(value, f.name)) authored.add(f.name);
		if (r.kind === 'inline') {
			fieldsOut[f.name] = encodeLeaf(f, v);
		} else if (r.kind === 'object') {
			// Encode each leaf child into a flat `<obj>.<child>` field. The object
			// value may be absent/partial — encodeLeaf falls back to the child default.
			const objVal = v && typeof v === 'object' ? (v as Record<string, unknown>) : {};
			for (const child of r.children) {
				const cr = renderOf(child);
				if (cr.kind !== 'inline') continue;
				const key = objChildFieldName(f.name, child.name);
				fieldsOut[key] = encodeLeaf(child, objVal[child.name]);
				if (authored && present(objVal, child.name)) authored.add(key);
			}
		} else if (r.kind === 'value') {
			// single condition reporter
			if (v && typeof v === 'object') {
				const child = buildNode(ctx, kindForCheck(r.check), v as Record<string, unknown>);
				if (child) inputsOut[f.name] = { block: child };
			}
		} else if (r.kind === 'array_object') {
			// list of fixed-shape objects (e.g. `tiers`) → stack of the generated
			// per-field wrapper blocks. Each entry is filled through this same
			// function against the ELEMENT's field list, so an element's leaf
			// fields and nested lists encode exactly as a top-level block's do.
			if (Array.isArray(v)) {
				const wrapperType = objItemBlockType(state.type, f.name);
				const entries: BlockState[] = [];
				for (const el of v) {
					if (!el || typeof el !== 'object') continue;
					const wrapper: BlockState = { type: wrapperType, id: nextNodeId(ctx) };
					fillNode(ctx, wrapper, r.children, el as Record<string, unknown>);
					entries.push(wrapper);
				}
				const head = chain(entries);
				if (head) inputsOut[f.name] = { block: head };
			}
		} else if (r.kind === 'statement' && (r.check === 'Action' || r.check === 'ItemAction')) {
			// single action ref OR action array — both render as a statement stack
			// (entity Action or ItemAction; both chain directly via `next`).
			const childKind = kindForCheck(r.check);
			if (Array.isArray(v)) {
				const head = chain(
					v
						.filter((el): el is Record<string, unknown> => !!el && typeof el === 'object')
						.map((el) => buildNode(ctx, childKind, el))
						.filter((b): b is BlockState => b !== null)
				);
				if (head) inputsOut[f.name] = { block: head };
			} else if (v && typeof v === 'object') {
				const child = buildNode(ctx, childKind, v as Record<string, unknown>);
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
					const child = buildNode(ctx, itemKind, el as Record<string, unknown>);
					if (!child) continue;
					items.push({ type: wrapperType, id: nextNodeId(ctx), inputs: { ITEM: { block: child } } });
				}
				const head = chain(items);
				if (head) inputsOut[f.name] = { block: head };
			}
		}
	}

	if (Object.keys(fieldsOut).length > 0) state.fields = fieldsOut;
	if (Object.keys(inputsOut).length > 0) state.inputs = inputsOut;
	if (authored && authored.size > 0 && state.id) ctx.authored?.set(state.id, authored);
}

/**
 * Convert the full powers array into a loadable workspace state.
 *
 * Pass `authored` to have the load record which keys each block really carried;
 * hand the SAME map to {@link stateToDraft} so an explicitly-authored default
 * survives the round trip. See {@link AuthoredFields}.
 */
export function draftToState(
	reg: BlockRegistry,
	powers: PowerDraft[],
	authored?: AuthoredFields
): WorkspaceState {
	const ctx: BuildCtx = { reg, authored, next: 0 };
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
		fillNode(ctx, state, fields, power.fields ?? {});
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

/** Read-side state: registry plus the authored index from the matching load. */
interface ReadCtx {
	reg: BlockRegistry;
	authored?: AuthoredFields;
}

/** The value an absent field renders as, and therefore decodes back to. */
function unsetValue(field: FormFieldSpec): unknown {
	return decodeLeaf(field, encodeLeaf(field, undefined));
}

function sameValue(a: unknown, b: unknown): boolean {
	return a === b || JSON.stringify(a) === JSON.stringify(b);
}

/** True when this block was built with `key` present in its source JSON. */
function wasAuthored(ctx: ReadCtx, state: BlockState, key: string): boolean {
	if (!ctx.authored || !state.id) return false;
	return ctx.authored.get(state.id)?.has(key) === true;
}

/**
 * Whether a leaf's read-back value is real authored data. A value still equal to
 * the unset stand-in is only emitted if the source JSON carried the key (or the
 * schema requires it) — otherwise writing it would invent an override the author
 * never asked for (e.g. `modify_damage.set_total: 0`).
 */
function keepLeaf(
	ctx: ReadCtx,
	state: BlockState,
	field: FormFieldSpec,
	decoded: unknown,
	key: string
): boolean {
	return field.required || wasAuthored(ctx, state, key) || !sameValue(decoded, unsetValue(field));
}

/** As {@link keepLeaf}, for the container kinds whose unset shape is empty. */
function keepEmpty(ctx: ReadCtx, state: BlockState, field: FormFieldSpec): boolean {
	return field.required || wasAuthored(ctx, state, field.name);
}

/** Read a node's value object (`{type, …}`) out of a block state. */
function readNode(ctx: ReadCtx, state: BlockState): Record<string, unknown> | null {
	const entry = ctx.reg.idForBlockType.get(state.type);
	if (!entry) return null;
	const fields = ctx.reg.fieldsByTypeId.get(regKey(entry.kind, entry.typeId)) ?? [];
	const out: Record<string, unknown> = { type: entry.typeId };
	readInto(ctx, out, fields, state);
	return out;
}

/** Read fields/inputs of a block state into a value object per the field specs. */
function readInto(
	ctx: ReadCtx,
	out: Record<string, unknown>,
	fields: FormFieldSpec[],
	state: BlockState
): void {
	for (const f of fields) {
		const r = renderOf(f);
		if (r.kind === 'inline') {
			const decoded = decodeLeaf(f, state.fields?.[f.name]);
			if (keepLeaf(ctx, state, f, decoded, f.name)) out[f.name] = decoded;
		} else if (r.kind === 'object') {
			// Reassemble the nested object from its flat `<obj>.<child>` fields.
			// A child's `required` means "required GIVEN the object exists", so it
			// must not vote on whether the object exists — otherwise an optional
			// object with required children is emitted every time. That is real:
			// `teleport_to_marker.position` has required x/y/z, and the runtime
			// switches to absolute coords on `json.has("position")` alone, so an
			// invented `{0,0,0}` teleports to world origin instead of by dy.
			const obj: Record<string, unknown> = {};
			let touched = false;
			for (const child of r.children) {
				const cr = renderOf(child);
				if (cr.kind !== 'inline') continue;
				const key = objChildFieldName(f.name, child.name);
				const decoded = decodeLeaf(child, state.fields?.[key]);
				if (wasAuthored(ctx, state, key) || !sameValue(decoded, unsetValue(child))) {
					touched = true;
					obj[child.name] = decoded;
				} else if (child.required) {
					// Only to keep the object valid if it survives on other evidence.
					obj[child.name] = decoded;
				}
			}
			// An all-unset object is no object — emitting it would silently arm an
			// optional gate (e.g. an `equipment_condition` nobody wrote).
			if (touched || keepEmpty(ctx, state, f)) out[f.name] = obj;
		} else if (r.kind === 'value') {
			const child = state.inputs?.[f.name]?.block;
			if (child) {
				const node = readNode(ctx, child);
				if (node) out[f.name] = node;
			}
		} else if (r.kind === 'array_object') {
			// Walk the element-wrapper stack; each wrapper reads back through this
			// same function against the element's field list into one plain object.
			const arr: unknown[] = [];
			let cur: BlockState | undefined = state.inputs?.[f.name]?.block;
			while (cur) {
				const el: Record<string, unknown> = {};
				readInto(ctx, el, r.children, cur);
				arr.push(el);
				cur = cur.next?.block;
			}
			if (arr.length > 0 || keepEmpty(ctx, state, f)) out[f.name] = arr;
		} else if (r.kind === 'statement' && (r.check === 'Action' || r.check === 'ItemAction')) {
			const head = state.inputs?.[f.name]?.block;
			if (f.kind === 'ARRAY_REF') {
				const arr: unknown[] = [];
				let cur: BlockState | undefined = head;
				while (cur) {
					const node = readNode(ctx, cur);
					if (node) arr.push(node);
					cur = cur.next?.block;
				}
				if (arr.length > 0 || keepEmpty(ctx, state, f)) out[f.name] = fromRefList(f, arr);
			} else if (head) {
				// single action ref — take the first block only
				const node = readNode(ctx, head);
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
			if (arr.length > 0 || keepEmpty(ctx, state, f)) out[f.name] = arr;
		} else {
			// condition array — walk the cond_item stack, pull each ITEM
			const arr: unknown[] = [];
			let cur: BlockState | undefined = state.inputs?.[f.name]?.block;
			while (cur) {
				const inner = cur.inputs?.ITEM?.block;
				if (inner) {
					const node = readNode(ctx, inner);
					if (node) arr.push(node);
				}
				cur = cur.next?.block;
			}
			// Only ARRAY_REF fields render as a wrapper stack (a single condition
			// REF is a `value` input), so collapsing via the spec is safe here.
			if (arr.length > 0 || keepEmpty(ctx, state, f)) {
				out[f.name] = f.kind === 'ARRAY_REF' ? fromRefList(f as ArrayRefFieldSpec, arr) : arr;
			}
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
 *
 * `authored` is the index {@link draftToState} filled on load. Without it every
 * field still sitting on its display stand-in reads back as unset, which is the
 * safe default; with it, a stand-in value the author really wrote is kept.
 */
export function stateToDraft(
	reg: BlockRegistry,
	ws: WorkspaceState,
	preserveByBlockId?: Map<string, Record<string, unknown>>,
	authored?: AuthoredFields
): PowerDraft[] {
	const ctx: ReadCtx = { reg, authored };
	const powers: PowerDraft[] = [];
	for (const block of ws.blocks?.blocks ?? []) {
		const entry = reg.idForBlockType.get(block.type);
		// Only power blocks are roots — a stray condition/action left loose on the
		// canvas must not be serialized as a power.
		if (!entry || entry.kind !== 'power') continue;
		const fields = reg.fieldsByTypeId.get(regKey('power', entry.typeId)) ?? [];
		const valueObj: Record<string, unknown> = { type: entry.typeId };
		readInto(ctx, valueObj, fields, block);
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
