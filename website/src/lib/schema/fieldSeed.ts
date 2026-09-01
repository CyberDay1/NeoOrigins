// hub: neoorigins/unset-fields.md

import type { FormFieldSpec } from './FormFieldSpec.js';

/**
 * Display stand-in for an unset field. BOOLEAN takes the schema default, since
 * a checkbox cannot render "unset"; other kinds stay unset-shaped so
 * `pruneForWire` drops the key. Never `undefined` — leaf rows reject it.
 */
export function mirrorSeedFor(spec: FormFieldSpec): unknown {
	switch (spec.kind) {
		case 'BOOLEAN':
			return spec.default ?? false;
		case 'INTEGER':
		case 'NUMBER':
		case 'REF':
		case 'ARRAY_REF':
			return null;
		case 'OBJECT':
			return {};
		default:
			return ''; // ENUM, STRING, and the raw-JSON escape hatch
	}
}
