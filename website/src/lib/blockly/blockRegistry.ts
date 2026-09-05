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
//   - ARRAY_OBJECT           → statement input whose check is a wrapper block
//                               generated for THAT field (objItemBlockType), so
//                               each entry of e.g. `edible_item.tiers` is one
//                               block carrying the element's own fields and only
//                               siblings of the same shape stack together.
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

/**
 * Category colours (block backgrounds), as JS maps.
 *
 * Blockly sets block colour in JS at definition/theme time — it does NOT
 * read CSS custom properties — so the JS map is the source of truth for the
 * canvas. The app.css `--color-blockly-*` tokens mirror these values for any
 * HTML swatches/legend; keep the two in sync.
 *
 * Five palettes ship (Okabe-Ito-derived for the CVD modes):
 *   - DEFAULT : the original nebula category colours.
 *   - PROTAN  : red-deficient (protanopia) safe set.
 *   - DEUTAN  : green-deficient (deuteranopia) safe set.
 *   - TRITAN  : blue-deficient (tritanopia) safe set.
 *   - MONO    : monochrome — every kind shares one neutral fill; the glyphs
 *               carry all category meaning.
 * The active map is chosen from the persisted palette setting; on change the
 * canvas rebuilds a Blockly.Theme and calls setTheme() (see BlockCanvas).
 *
 * Glyph redundancy: each kind also carries an ASCII-safe glyph (owner-
 * approved) prefixed onto BOTH the on-canvas block message and the toolbox
 * category label, so category is readable without relying on colour. Glyphs
 * are palette-independent.
 *
 * Luminance rule: every fill is kept in a mid range so the white on-block
 * text stays legible. Keep these maps in sync with the app.css
 * `[data-palette='…']` `--color-blockly-*` overrides.
 */
export type BlocklyPalette = 'default' | 'protan' | 'deutan' | 'tritan' | 'mono';

const COLOUR_DEFAULT: Record<BlockKind, string> = {
	power: '#5d6b87',
	condition: '#15a89b',
	action: '#7c5cff',
	block_condition: '#c8881f',
	item_condition: '#4f9d3a',
	item_action: '#b5478f'
};

/** Protanopia (red-deficient) safe palette. */
const COLOUR_PROTAN: Record<BlockKind, string> = {
	power: '#0072b2',
	condition: '#5a9bd4',
	action: '#cc79a7',
	block_condition: '#b5651d',
	item_condition: '#117733',
	item_action: '#d55e00'
};

/** Deuteranopia (green-deficient) safe palette. */
const COLOUR_DEUTAN: Record<BlockKind, string> = {
	power: '#1f6f9c',
	condition: '#009e73',
	action: '#cc79a7',
	block_condition: '#c8881f',
	item_condition: '#56789c',
	item_action: '#d55e00'
};

/** Tritanopia (blue-deficient) safe palette. */
const COLOUR_TRITAN: Record<BlockKind, string> = {
	power: '#117733',
	condition: '#cc6677',
	action: '#aa4499',
	block_condition: '#882255',
	item_condition: '#44897a',
	item_action: '#7a4fa0'
};

/** Monochrome — one neutral mid-grey for every kind; glyphs disambiguate. */
const COLOUR_MONO: Record<BlockKind, string> = {
	power: '#5b6470',
	condition: '#5b6470',
	action: '#5b6470',
	block_condition: '#5b6470',
	item_condition: '#5b6470',
	item_action: '#5b6470'
};

export const BLOCKLY_PALETTES: Record<BlocklyPalette, Record<BlockKind, string>> = {
	default: COLOUR_DEFAULT,
	protan: COLOUR_PROTAN,
	deutan: COLOUR_DEUTAN,
	tritan: COLOUR_TRITAN,
	mono: COLOUR_MONO
};

/** Resolve the colour map for a palette (defaults to DEFAULT for unknowns). */
export function paletteColours(palette: BlocklyPalette): Record<BlockKind, string> {
	return BLOCKLY_PALETTES[palette] ?? COLOUR_DEFAULT;
}

/** Owner-approved category glyphs — ASCII-safe, palette-independent. */
export const KIND_GLYPH: Record<BlockKind, string> = {
	power: '◆',
	condition: '?',
	action: '▸',
	block_condition: '▦',
	item_condition: '◈',
	item_action: '▸◈'
};

/** Blockly block-style name for a kind. Blocks reference a style so a theme
 *  swap (setTheme) recolours them live without redefining blocks. */
export function blockStyleName(kind: BlockKind): string {
	return `neo_${kind}_style`;
}

/**
 * Build a `Blockly.Theme` whose `blockStyles` map every kind's style name to
 * the chosen palette's colour. Passing the live `Blockly` module avoids a
 * static import here (Blockly is loaded dynamically in BlockCanvas). Calling
 * `workspace.setTheme(theme)` with this recolours the canvas immediately.
 */
export function buildBlocklyTheme(
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	Blockly: { Theme: { defineTheme: (name: string, opts: any) => any }; Themes: { Classic: any } },
	palette: BlocklyPalette
) {
	const colours = paletteColours(palette);
	const blockStyles: Record<string, { colourPrimary: string }> = {};
	(Object.keys(colours) as BlockKind[]).forEach((kind) => {
		blockStyles[blockStyleName(kind)] = { colourPrimary: colours[kind] };
	});
	// The str-item wrapper keeps its neutral grey (not a category kind).
	blockStyles[STR_ITEM_STYLE] = { colourPrimary: STR_ITEM_COLOUR };
	return Blockly.Theme.defineTheme(`neo_${palette}`, {
		base: Blockly.Themes.Classic,
		blockStyles
	});
}

/** Neutral structural-wrapper colour + style (not a colourable category). */
const STR_ITEM_COLOUR = '#8a8f99';
const STR_ITEM_STYLE = 'neo_str_item_style';

export interface BlockRegistry {
	/** Block definition JSON to feed `defineBlocksWithJsonArray`. */
	defs: object[];
	/** Categorised toolbox JSON for `Blockly.inject`. */
	toolbox: object;
	/** `regKey(kind, typeId)` → generated Blockly block type. Keys carry the kind
	 *  because the same id can exist as several kinds (e.g. `neoorigins:resource`
	 *  is both a power and a condition). */
	blockTypeForId: Map<string, string>;
	/** Generated Blockly block type → its typeId + kind. */
	idForBlockType: Map<string, { typeId: string; kind: BlockKind }>;
	/** `regKey(kind, typeId)` → its parsed field list (for serialization). */
	fieldsByTypeId: Map<string, FormFieldSpec[]>;
}

/** Map key for the per-(kind, typeId) registry maps — bare typeIds collide
 *  across kinds (power vs condition `neoorigins:resource`, etc.). */
export function regKey(kind: BlockKind, typeId: string): string {
	return `${kind}|${typeId}`;
}

/** Sanitise a fully-qualified type id into a Blockly-legal block type. Uses the
 *  full kind name — single-letter prefixes collide (`item_condition` and
 *  `item_action` would both be `i`). */
function blockTypeId(kind: BlockKind, typeId: string): string {
	return `neo_${kind}_${typeId.replace(/[^a-zA-Z0-9]/g, '_')}`;
}

function shortName(typeId: string): string {
	return typeId.includes(':') ? typeId.split(':')[1] : typeId;
}

/** How a single field renders as a Blockly arg / input. */
type FieldRender =
	| { kind: 'inline'; arg: Record<string, unknown> }
	| { kind: 'value'; check: string }
	| { kind: 'statement'; check: string }
	| { kind: 'object'; children: FormFieldSpec[] }
	| { kind: 'array_object'; children: FormFieldSpec[] };

/** The flattened Blockly field name for a leaf `child` of OBJECT field `obj`. */
export function objChildFieldName(objName: string, childName: string): string {
	return `${objName}.${childName}`;
}

/**
 * Block type — and connection check — of the wrapper holding ONE entry of an
 * ARRAY_OBJECT field. Generated per (owner block, field) rather than shared,
 * because each such array has its own element shape: a `tiers` entry must not be
 * pluggable into a `drops` list, and the check string is what enforces that.
 */
export function objItemBlockType(ownerBlockType: string, fieldName: string): string {
	return `${ownerBlockType}__item_${fieldName.replace(/[^a-zA-Z0-9]/g, '_')}`;
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
		case 'ARRAY_OBJECT':
			// A list of fixed-shape objects → stack of generated per-field wrapper
			// blocks (see objItemBlockType), each carrying that element's own
			// fields. The check is the wrapper's own type, so lists with different
			// element shapes cannot be cross-plugged.
			return { kind: 'array_object', children: field.children };
		case 'OBJECT':
			return { kind: 'object', children: field.children };
	}
}

/**
 * Lay a field list out onto `blockType`'s message/args, appending to the arrays
 * the caller supplies (so a power block's leading `id` field keeps slot %1).
 * Returns the finished `message0` plus the deferred statement rows.
 *
 * Element-wrapper defs generated for ARRAY_OBJECT fields are pushed into
 * `extraDefs`; they are ordinary blocks laid out through this same function, so
 * an element's own leaf/statement children render exactly as they would on a
 * top-level block.
 */
function layoutFields(
	blockType: string,
	fields: FormFieldSpec[],
	message: string,
	args: Record<string, unknown>[],
	extraDefs: object[]
): { message: string; statementRows: { label: string; arg: Record<string, unknown> }[] } {
	const statementRows: { label: string; arg: Record<string, unknown> }[] = [];
	for (const f of fields) {
		const r = renderOf(f);
		if (r.kind === 'inline') {
			message += ` ${f.label} %${args.length + 1}`;
			args.push(r.arg);
		} else if (r.kind === 'object') {
			// Flatten the object's leaf children onto this block as inline fields
			// named `<obj>.<child>`. Non-leaf children (REF/ARRAY_REF/nested OBJECT)
			// have no flat-field representation — none exist in current shapes.
			message += ` ${f.label}:`;
			for (const child of r.children) {
				const cr = renderOf(child);
				if (cr.kind !== 'inline') continue;
				message += ` ${child.label} %${args.length + 1}`;
				args.push({ ...cr.arg, name: objChildFieldName(f.name, child.name) });
			}
		} else if (r.kind === 'value') {
			message += ` ${f.label} %${args.length + 1}`;
			args.push({ type: 'input_value', name: f.name, check: r.check });
		} else if (r.kind === 'array_object') {
			const wrapper = objItemBlockType(blockType, f.name);
			extraDefs.push(objItemDef(wrapper, f, r.children, extraDefs));
			statementRows.push({
				label: f.label,
				arg: { type: 'input_statement', name: f.name, check: wrapper }
			});
		} else {
			// Statement (C-mouth) inputs render best on their own line, after the
			// inline header — defer them.
			statementRows.push({
				label: f.label,
				arg: { type: 'input_statement', name: f.name, check: r.check }
			});
		}
	}
	return { message, statementRows };
}

/**
 * The generated wrapper block for ONE entry of an ARRAY_OBJECT field. It takes
 * the neutral structural colour (it is not a category) and its prev/next check
 * is its own type, so only siblings of the same element shape stack together.
 */
function objItemDef(
	wrapperType: string,
	field: FormFieldSpec,
	children: FormFieldSpec[],
	extraDefs: object[]
): object {
	const args: Record<string, unknown>[] = [];
	const laid = layoutFields(wrapperType, children, shortName(field.name), args, extraDefs);
	const def: Record<string, unknown> = {
		type: wrapperType,
		style: STR_ITEM_STYLE,
		inputsInline: true,
		previousStatement: wrapperType,
		nextStatement: wrapperType,
		message0: laid.message,
		args0: args
	};
	laid.statementRows.forEach((row, i) => {
		def[`message${i + 1}`] = `${row.label} %1`;
		def[`args${i + 1}`] = [row.arg];
	});
	return def;
}

/** Build the block definition JSON for one type, plus any element wrappers it needs. */
function buildDefs(kind: BlockKind, typeId: string, fields: FormFieldSpec[]): object[] {
	const args: Record<string, unknown>[] = [];
	const extraDefs: object[] = [];
	// Glyph prefix makes the category legible without colour.
	let message = `${KIND_GLYPH[kind]} ${shortName(typeId)}`;

	if (kind === 'power') {
		message += ' id %1';
		args.push({ type: 'field_input', name: POWER_ID_FIELD, text: '' });
	}

	const laid = layoutFields(blockTypeId(kind, typeId), fields, message, args, extraDefs);
	message = laid.message;
	const statementRows = laid.statementRows;

	const def: Record<string, unknown> = {
		type: blockTypeId(kind, typeId),
		// `style` (not `colour`) drives the block background, so a theme swap
		// (setTheme) recolours live. Blockly forbids setting BOTH `colour` and
		// `style` on one block — the initial colour comes from the theme that
		// BlockCanvas builds for the active palette at inject time.
		style: blockStyleName(kind),
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
	return [def, ...extraDefs];
}

/** The fixed wrapper block for condition-list entries. */
function condItemDef(): object {
	return {
		type: COND_ITEM_TYPE,
		style: blockStyleName('condition'),
		inputsInline: true,
		previousStatement: 'CondItem',
		nextStatement: 'CondItem',
		message0: `${KIND_GLYPH.condition} condition %1`,
		args0: [{ type: 'input_value', name: 'ITEM', check: 'Condition' }]
	};
}

/** The fixed wrapper block for block_condition-list entries (and/or `conditions`). */
function blockCondItemDef(): object {
	return {
		type: BLOCK_COND_ITEM_TYPE,
		style: blockStyleName('block_condition'),
		inputsInline: true,
		previousStatement: 'BlockCondItem',
		nextStatement: 'BlockCondItem',
		message0: `${KIND_GLYPH.block_condition} block condition %1`,
		args0: [{ type: 'input_value', name: 'ITEM', check: 'BlockCondition' }]
	};
}

/** The fixed wrapper block for item_condition-list entries (and/or `conditions`). */
function itemCondItemDef(): object {
	return {
		type: ITEM_COND_ITEM_TYPE,
		style: blockStyleName('item_condition'),
		inputsInline: true,
		previousStatement: 'ItemCondItem',
		nextStatement: 'ItemCondItem',
		message0: `${KIND_GLYPH.item_condition} item condition %1`,
		args0: [{ type: 'input_value', name: 'ITEM', check: 'ItemCondition' }]
	};
}

/** The fixed wrapper block for scalar-string-list entries (e.g. `biomes`). The
 *  value lives in a text FIELD (not an input), since strings have no value block. */
function strItemDef(): object {
	return {
		type: STR_ITEM_TYPE,
		style: STR_ITEM_STYLE,
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
	schemas: RefSchemas,
	palette: BlocklyPalette = 'default'
): BlockRegistry {
	// `colours` drives only the toolbox category bars below — block bodies are
	// coloured by their `style` via the injected theme (see BlockCanvas).
	const colours = paletteColours(palette);
	const defs: object[] = [condItemDef(), blockCondItemDef(), itemCondItemDef(), strItemDef()];
	const blockTypeForId = new Map<string, string>();
	const idForBlockType = new Map<string, { typeId: string; kind: BlockKind }>();
	const fieldsByTypeId = new Map<string, FormFieldSpec[]>();

	// Toolbox category labels carry the same glyph prefix as their blocks so
	// the category is legible without relying on colour.
	const toolboxCats: { kind: string; name: string; colour: string; contents: object[] }[] = [
		{ kind: 'category', name: `${KIND_GLYPH.power} Powers`, colour: colours.power, contents: [] },
		{ kind: 'category', name: `${KIND_GLYPH.condition} Conditions`, colour: colours.condition, contents: [] },
		{ kind: 'category', name: `${KIND_GLYPH.action} Actions`, colour: colours.action, contents: [] },
		{ kind: 'category', name: `${KIND_GLYPH.block_condition} Block Conditions`, colour: colours.block_condition, contents: [] },
		{ kind: 'category', name: `${KIND_GLYPH.item_condition} Item Conditions`, colour: colours.item_condition, contents: [] },
		{ kind: 'category', name: `${KIND_GLYPH.item_action} Item Actions`, colour: colours.item_action, contents: [] }
	];

	const register = (
		kind: BlockKind,
		typeId: string,
		fields: FormFieldSpec[],
		catIndex: number
	) => {
		const bt = blockTypeId(kind, typeId);
		const key = regKey(kind, typeId);
		blockTypeForId.set(key, bt);
		idForBlockType.set(bt, { typeId, kind });
		fieldsByTypeId.set(key, fields);
		defs.push(...buildDefs(kind, typeId, fields));
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
		fieldsByTypeId
	};
}
