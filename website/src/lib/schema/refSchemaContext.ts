// Svelte context plumbing for the recursive RefRow / ArrayRefRow sub-forms.
//
// The power editor loads `power.schema.json` + `field_docs.json` in PowersTab.
// D4's nested action/condition sub-forms additionally need the sibling
// `action.schema.json` / `condition.schema.json` documents to resolve a chosen
// type's fields (`parseRefSchema`). Rather than prop-drill those two documents
// through PowerEditor → FieldRowAdapter → FieldRow → RefRow → … (and across the
// recursive boundary), PowersTab publishes them once via Svelte context and the
// rows read them on demand. Returns `null` when no provider is mounted so a
// stray RefRow degrades gracefully instead of throwing.

import { getContext, setContext } from 'svelte';

export interface RefSchemas {
	/** Parsed `action.schema.json`. */
	action: object;
	/** Parsed `condition.schema.json`. */
	condition: object;
	/** Parsed `block_condition.schema.json` (nested block-condition sub-shapes). */
	blockCondition: object;
	/** Parsed `item_condition.schema.json` (nested item-condition sub-shapes). */
	itemCondition: object;
	/** Parsed `item_action.schema.json` (nested item-action sub-shapes). */
	itemAction: object;
	/** Parsed `field_docs.json` (shared with the power form). */
	fieldDocs: object;
}

const KEY = Symbol('neoorigins.refSchemas');

export function setRefSchemas(schemas: RefSchemas): void {
	setContext(KEY, schemas);
}

export function getRefSchemas(): RefSchemas | null {
	return getContext<RefSchemas | undefined>(KEY) ?? null;
}

/** Pick the document for a given ref target out of the published context. */
export function docFor(
	schemas: RefSchemas,
	refDoc: 'action' | 'condition' | 'block_condition' | 'item_condition' | 'item_action'
): object {
	if (refDoc === 'action') return schemas.action;
	if (refDoc === 'block_condition') return schemas.blockCondition;
	if (refDoc === 'item_condition') return schemas.itemCondition;
	if (refDoc === 'item_action') return schemas.itemAction;
	return schemas.condition;
}
