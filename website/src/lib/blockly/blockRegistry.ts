// Code-generates Blockly block definitions from the SAME FieldSpec registry
// that drives the form editor and the JSON schema. One source of truth: every
// power / action / condition type in the three schema documents becomes a
// Blockly block whose inputs mirror that type's `FormFieldSpec[]`.
//
// Mapping (Scratch/Snap! analogues):
//   - power     → a standalone stack block (no output / no prev-next): each
//                 power is its own root on the canvas.
//   - condition → a value/reporter block (`output: 'Condition'`) that plugs
//                 into condition slots.
//   - action    → a statement block (`prev/next: 'Action'`) that snaps into
//                 the C-shaped mouth of a power / combinator.
//
// Field → input mapping:
//   - leaf (BOOLEAN/INTEGER/NUMBER/ENUM/STRING/RawJson) → inline field widget.
//   - REF condition           → value input  (check 'Condition').
//   - REF action              → statement input (check 'Action', holds one).
//   - ARRAY_REF action        → statement input (check 'Action', a stack).
//   - ARRAY_REF condition     → statement input (check 'CondItem'); each entry
//                               is a `neo_cond_item` wrapper holding one
//                               condition value (value blocks can't stack, so
//                               the wrapper provides the prev/next surface).
//   - OBJECT (fixed children) → its leaf children are flattened onto the parent
//                               block as inline fields named `<obj>.<child>`
//                               (Blockly fields are flat). Round-trips into a
//                               nested object value in blockState. The current
//                               OBJECT shapes (item_stack / effect_instance /
//                               modifier / hud_render) have only leaf children.

import type { FormFieldSpec } from '$lib/schema/FormFieldSpec';
import { parsePowerSchema, parseRefSchema, refTypeOptions } from '$lib/schema/SchemaFormModel';
import type { RefSchemas } from '$lib/schema/refSchemaContext';

export type BlockKind =
	| 'power'
	| 'action'
	| 'condition'
	| 'block_condition'
	| 'item_condition'
	| 'item_action';

/** Synthetic wrapper block that gives condition-list entries a prev/next surface. */
export const COND_ITEM_TYPE = 'neo_cond_item';

/** Wrapper giving block_condition-list entries (and/or `conditions`) a prev/next surface. */
export const BLOCK_COND_ITEM_TYPE = 'neo_block_cond_item';

/** Wrapper giving item_condition-list entries (and/or `conditions`) a prev/next surface. */
export const ITEM_COND_ITEM_TYPE = 'neo_item_cond_item';

/** Wrapper giving scalar-string-list entries (e.g. `biomes`) a prev/next surface;
 *  unlike the condition wrappers it holds the value in a text FIELD, not an input. */
export const STR_ITEM_TYPE = 'neo_str_item';

/** Field name carrying a power block's local id. */
export const POWER_ID_FIELD = '__id';

/** Category colours (block backgrounds). Condition = teal, action = violet,
 *  block_condition = amber, item_condition = green, item_action = magenta
 *  (distinct so nested tests/actions read at a glance). */
const COLOUR: Record<BlockKind, string> = {
	power: '#5d6b87',
	condition: '#15a89b',
	action: '#7c5cff',
	block_condition: '#c8881f',
	item_condition: '#4f9d3a',
	item_action: '#b5478f'
};

export interface BlockRegistry {
	/** Block definition JSON to feed `defineBlocksWithJsonArray`. */
	defs: object[];
	/** Categorised toolbox JSON for `Blockly.inject`. */
	toolbox: object;
	/** typeId → generated Blockly block type. */
	blockTypeForId: Map<string, string>;
	/** Generated Blockly block type → typeId. */
	idForBlockType: Map<string, string>;
	/** typeId → its parsed field list (for serialization). */
	fieldsByTypeId: Map<string, FormFieldSpec[]>;
	/** typeId → which kind it is. */
	kindByTypeId: Map<string, BlockKind>;
}

/** Sanitise a fully-qualified type id into a Blockly-legal block type. */
function blockTypeId(kind: BlockKind, typeId: string): string {
	return `neo_${kind[0]}_${typeId.replace(/[^a-zA-Z0-9]/g, '_')}`;
}

function shortName(typeId: string): string {
	return typeId.includes(':') ? typeId.split(':')[1] : typeId;
}

/** How a single field renders as a Blockly arg / input. */
type FieldRender =
	| { kind: 'inline'; arg: Record<string, unknown> }
	| { kind: 'value'; check: string }
	| { kind: 'statement'; check: string }
	| { kind: 'object'; children: FormFieldSpec[] };

/** The flattened Blockly field name for a leaf `child` of OBJECT field `obj`. */
export function objChildFieldName(objName: string, childName: string): string {
	return `${objName}.${childName}`;
}

/** Decide how a FormFieldSpec maps onto Blockly. Shared by defs + serialization. */
export function renderOf(field: FormFieldSpec): FieldRender {
	switch (field.kind) {
		case 'BOOLEAN':
			return {
				kind: 'inline',
				arg: { type: 'field_checkbox', name: field.name, checked: field.default ?? false }
			};
		case 'INTEGER':
			return {
				kind: 'inline',
				arg: { type: 'field_number', name: field.name, value: field.default ?? 0, precision: 1 }
			};
		case 'NUMBER':
			return {
				kind: 'inline',
				arg: { type: 'field_number', name: field.name, value: field.default ?? 0 }
			};
		case 'ENUM':
			if (field.options.length === 0) {
				return { kind: 'inline', arg: { type: 'field_input', name: field.name, text: '' } };
			}
			return {
				kind: 'inline',
				arg: {
					type: 'field_dropdown',
					name: field.name,
					options: field.options.map((o) => [shortName(o), o])
				}
			};
		case 'STRING':
			return {
				kind: 'inline',
				arg: { type: 'field_input', name: field.name, text: field.default ?? '' }
			};
		case 'RawJson':
			return {
				kind: 'inline',
				arg: { type: 'field_input', name: field.name, text: field.default ?? '' }
			};
		case 'REF':
			if (field.refDoc === 'condition') return { kind: 'value', check: 'Condition' };
			if (field.refDoc === 'block_condition') return { kind: 'value', check: 'BlockCondition' };
			if (field.refDoc === 'item_condition') return { kind: 'value', check: 'ItemCondition' };
			if (field.refDoc === 'item_action') return { kind: 'statement', check: 'ItemAction' };
			return { kind: 'statement', check: 'Action' };
		case 'ARRAY_REF':
			if (field.refDoc === 'condition') return { kind: 'statement', check: 'CondItem' };
			if (field.refDoc === 'block_condition') return { kind: 'statement', check: 'BlockCondItem' };
			if (field.refDoc === 'item_condition') return { kind: 'statement', check: 'ItemCondItem' };
			if (field.refDoc === 'item_action') return { kind: 'statement', check: 'ItemAction' };
			return { kind: 'statement', check: 'Action' };
		case 'ARRAY_STRING':
			// A scalar-string list → stack of `neo_str_item` wrappers, each holding
			// one free-text value (e.g. a biome id). Modded/datapack ids work since
			// the field is plain text, never a closed dropdown.
			return { kind: 'statement', check: 'StrItem' };
		case 'OBJECT':
			return { kind: 'object', children: field.children };
	}
}

/** Build the block definition JSON for one type. */
function buildDef(kind: BlockKind, typeId: string, fields: FormFieldSpec[]): object {
	const args: Record<string, unknown>[] = [];
	const statementRows: { label: string; arg: Record<string, unknown> }[] = [];
	let message = shortName(typeId);
	let n = 0;

	if (kind === 'power') {
		message += ` id %${++n}`;
		args.push({ type: 'field_input', name: POWER_ID_FIELD, text: '' });
	}

	for (const f of fields) {
		const r = renderOf(f);
		if (r.kind === 'inline') {
			message += ` ${f.label} %${++n}`;
			args.push(r.arg);
		} else if (r.kind === 'object') {
			// Flatten the object's leaf children onto this block as inline fields
			// named `<obj>.<child>`. Non-leaf children (REF/ARRAY_REF/nested OBJECT)
			// have no flat-field representation — none exist in current shapes.
			message += ` ${f.label}:`;
			for (const child of r.children) {
				const cr = renderOf(child);
				if (cr.kind !== 'inline') continue;
				message += ` ${child.label} %${++n}`;
				args.push({ ...cr.arg, name: objChildFieldName(f.name, child.name) });
			}
		} else if (r.kind === 'value') {
			message += ` ${f.label} %${++n}`;
			args.push({ type: 'input_value', name: f.name, check: r.check });
		} else {
			// Statement (C-mouth) inputs render best on their own line, after the
			// inline header — defer them.
			statementRows.push({
				label: f.label,
				arg: { type: 'input_statement', name: f.name, check: r.check }
			});
		}
	}

	const def: Record<string, unknown> = {
		type: blockTypeId(kind, typeId),
		colour: COLOUR[kind],
		inputsInline: true,
		tooltip: typeId,
		message0: message,
		args0: args
	};

	statementRows.forEach((row, i) => {
		def[`message${i + 1}`] = `${row.label} %1`;
		def[`args${i + 1}`] = [row.arg];
	});

	if (kind === 'power') {
		// Standalone root block — no connections.
	} else if (kind === 'condition') {
		def.output = 'Condition';
	} else if (kind === 'block_condition') {
		def.output = 'BlockCondition';
	} else if (kind === 'item_condition') {
		def.output = 'ItemCondition';
	} else if (kind === 'item_action') {
		def.previousStatement = 'ItemAction';
		def.nextStatement = 'ItemAction';
	} else {
		def.previousStatement = 'Action';
		def.nextStatement = 'Action';
	}
	return def;
}

/** The fixed wrapper block for condition-list entries. */
function condItemDef(): object {
	return {
		type: COND_ITEM_TYPE,
		colour: COLOUR.condition,
		inputsInline: true,
		previousStatement: 'CondItem',
		nextStatement: 'CondItem',
		message0: 'condition %1',
		args0: [{ type: 'input_value', name: 'ITEM', check: 'Condition' }]
	};
}

/** The fixed wrapper block for block_condition-list entries (and/or `conditions`). */
function blockCondItemDef(): object {
	return {
		type: BLOCK_COND_ITEM_TYPE,
		colour: COLOUR.block_condition,
		inputsInline: true,
		previousStatement: 'BlockCondItem',
		nextStatement: 'BlockCondItem',
		message0: 'block condition %1',
		args0: [{ type: 'input_value', name: 'ITEM', check: 'BlockCondition' }]
	};
}

/** The fixed wrapper block for item_condition-list entries (and/or `conditions`). */
function itemCondItemDef(): object {
	return {
		type: ITEM_COND_ITEM_TYPE,
		colour: COLOUR.item_condition,
		inputsInline: true,
		previousStatement: 'ItemCondItem',
		nextStatement: 'ItemCondItem',
		message0: 'item condition %1',
		args0: [{ type: 'input_value', name: 'ITEM', check: 'ItemCondition' }]
	};
}

/** The fixed wrapper block for scalar-string-list entries (e.g. `biomes`). The
 *  value lives in a text FIELD (not an input), since strings have no value block. */
function strItemDef(): object {
	return {
		type: STR_ITEM_TYPE,
		colour: '#8a8f99',
		inputsInline: true,
		previousStatement: 'StrItem',
		nextStatement: 'StrItem',
		message0: 'entry %1',
		args0: [{ type: 'field_input', name: 'ITEM', text: '' }]
	};
}

/**
 * Build the full registry from the three loaded schemas. Types whose parse
 * throws (malformed branch) still get a header-only block so the palette and
 * deserialization never crash.
 */
export function buildBlockRegistry(
	powerSchema: object,
	schemas: RefSchemas
): BlockRegistry {
	const defs: object[] = [condItemDef(), blockCondItemDef(), itemCondItemDef(), strItemDef()];
	const blockTypeForId = new Map<string, string>();
	const idForBlockType = new Map<string, string>();
	const fieldsByTypeId = new Map<string, FormFieldSpec[]>();
	const kindByTypeId = new Map<string, BlockKind>();

	const toolboxCats: { kind: string; name: string; colour: string; contents: object[] }[] = [
		{ kind: 'category', name: 'Powers', colour: COLOUR.power, contents: [] },
		{ kind: 'category', name: 'Conditions', colour: COLOUR.condition, contents: [] },
		{ kind: 'category', name: 'Actions', colour: COLOUR.action, contents: [] },
		{ kind: 'category', name: 'Block Conditions', colour: COLOUR.block_condition, contents: [] },
		{ kind: 'category', name: 'Item Conditions', colour: COLOUR.item_condition, contents: [] },
		{ kind: 'category', name: 'Item Actions', colour: COLOUR.item_action, contents: [] }
	];

	const register = (
		kind: BlockKind,
		typeId: string,
		fields: FormFieldSpec[],
		catIndex: number
	) => {
		const bt = blockTypeId(kind, typeId);
		blockTypeForId.set(typeId, bt);
		idForBlockType.set(bt, typeId);
		fieldsByTypeId.set(typeId, fields);
		kindByTypeId.set(typeId, kind);
		defs.push(buildDef(kind, typeId, fields));
		// Keep the palette readable: only surface neoorigins-namespaced ids
		// (the `apace:` aliases share a branch and would just be noise), but
		// still register every id above so any saved draft loads.
		if (typeId.startsWith('neoorigins:')) {
			toolboxCats[catIndex].contents.push({ kind: 'block', type: bt });
		}
	};

	for (const t of refTypeOptions(powerSchema)) {
		let fields: FormFieldSpec[] = [];
		try {
			fields = parsePowerSchema(powerSchema, schemas.fieldDocs, t);
		} catch {
			fields = [];
		}
		register('power', t, fields, 0);
	}
	for (const t of refTypeOptions(schemas.condition)) {
		let fields: FormFieldSpec[] = [];
		try {
			fields = parseRefSchema('condition', schemas.condition, schemas.fieldDocs, t);
		} catch {
			fields = [];
		}
		register('condition', t, fields, 1);
	}
	for (const t of refTypeOptions(schemas.action)) {
		let fields: FormFieldSpec[] = [];
		try {
			fields = parseRefSchema('action', schemas.action, schemas.fieldDocs, t);
		} catch {
			fields = [];
		}
		register('action', t, fields, 2);
	}
	for (const t of refTypeOptions(schemas.blockCondition)) {
		let fields: FormFieldSpec[] = [];
		try {
			fields = parseRefSchema('block_condition', schemas.blockCondition, schemas.fieldDocs, t);
		} catch {
			fields = [];
		}
		register('block_condition', t, fields, 3);
	}
	for (const t of refTypeOptions(schemas.itemCondition)) {
		let fields: FormFieldSpec[] = [];
		try {
			fields = parseRefSchema('item_condition', schemas.itemCondition, schemas.fieldDocs, t);
		} catch {
			fields = [];
		}
		register('item_condition', t, fields, 4);
	}
	for (const t of refTypeOptions(schemas.itemAction)) {
		let fields: FormFieldSpec[] = [];
		try {
			fields = parseRefSchema('item_action', schemas.itemAction, schemas.fieldDocs, t);
		} catch {
			fields = [];
		}
		register('item_action', t, fields, 5);
	}

	return {
		defs,
		toolbox: { kind: 'categoryToolbox', contents: toolboxCats },
		blockTypeForId,
		idForBlockType,
		fieldsByTypeId,
		kindByTypeId
	};
}
