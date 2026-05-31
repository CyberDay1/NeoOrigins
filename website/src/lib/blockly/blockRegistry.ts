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

import type { FormFieldSpec } from '$lib/schema/FormFieldSpec';
import { parsePowerSchema, parseRefSchema, refTypeOptions } from '$lib/schema/SchemaFormModel';
import type { RefSchemas } from '$lib/schema/refSchemaContext';

export type BlockKind = 'power' | 'action' | 'condition';

/** Synthetic wrapper block that gives condition-list entries a prev/next surface. */
export const COND_ITEM_TYPE = 'neo_cond_item';

/** Field name carrying a power block's local id. */
export const POWER_ID_FIELD = '__id';

/** Category colours (block backgrounds). Condition = teal, action = violet. */
const COLOUR: Record<BlockKind, string> = {
	power: '#5d6b87',
	condition: '#15a89b',
	action: '#7c5cff'
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
	| { kind: 'statement'; check: string };

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
			return field.refDoc === 'condition'
				? { kind: 'value', check: 'Condition' }
				: { kind: 'statement', check: 'Action' };
		case 'ARRAY_REF':
			return field.refDoc === 'condition'
				? { kind: 'statement', check: 'CondItem' }
				: { kind: 'statement', check: 'Action' };
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

/**
 * Build the full registry from the three loaded schemas. Types whose parse
 * throws (malformed branch) still get a header-only block so the palette and
 * deserialization never crash.
 */
export function buildBlockRegistry(
	powerSchema: object,
	schemas: RefSchemas
): BlockRegistry {
	const defs: object[] = [condItemDef()];
	const blockTypeForId = new Map<string, string>();
	const idForBlockType = new Map<string, string>();
	const fieldsByTypeId = new Map<string, FormFieldSpec[]>();
	const kindByTypeId = new Map<string, BlockKind>();

	const toolboxCats: { kind: string; name: string; colour: string; contents: object[] }[] = [
		{ kind: 'category', name: 'Powers', colour: COLOUR.power, contents: [] },
		{ kind: 'category', name: 'Conditions', colour: COLOUR.condition, contents: [] },
		{ kind: 'category', name: 'Actions', colour: COLOUR.action, contents: [] }
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

	return {
		defs,
		toolbox: { kind: 'categoryToolbox', contents: toolboxCats },
		blockTypeForId,
		idForBlockType,
		fieldsByTypeId,
		kindByTypeId
	};
}
