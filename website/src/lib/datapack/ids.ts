// Id rules the game enforces, checked before anything is exported.
//
// Mirrors ResourceLocation.isValidNamespace / isValidPath. A value that fails
// them is refused rather than trimmed or lower-cased: an exported bad id is
// not a cosmetic problem, the loader skips the file or the whole entry.
// Paths that name a file also refuse empty, "." and ".." segments, which the
// pack loader rejects.

const NAMESPACE_CHARS = /^[a-z0-9_.-]+$/;
const PATH_CHARS = /^[a-z0-9_./-]+$/;

function describeBadChars(value: string, allowed: RegExp): string {
	const bad = [...new Set([...value].filter((c) => !allowed.test(c)))];
	const shown = bad.map((c) => (c === ' ' ? 'a space' : /[A-Z]/.test(c) ? `capital "${c}"` : `"${c}"`));
	return `contains ${shown.join(', ')}`;
}

/** Why `ns` is not a valid namespace, or null. */
export function namespaceError(ns: string): string | null {
	if (ns === '') return 'is blank';
	if (!NAMESPACE_CHARS.test(ns)) return `${describeBadChars(ns, /[a-z0-9_.-]/)} — use lowercase a-z, 0-9, _ . -`;
	return null;
}

/** Why `path` is not a valid id path, or null. `file` adds the segment rules for ids that name a file. */
export function pathError(path: string, file = true): string | null {
	if (path === '') return 'is blank';
	if (!PATH_CHARS.test(path)) return `${describeBadChars(path, /[a-z0-9_./-]/)} — use lowercase a-z, 0-9, _ . - /`;
	if (file && path.split('/').some((seg) => seg === '' || seg === '.' || seg === '..')) {
		return 'has an empty, "." or ".." folder segment';
	}
	return null;
}

/** Why `id` is not a valid `namespace:path` id, or null. A missing namespace means `minecraft:`. */
export function resourceIdError(id: string): string | null {
	const i = id.indexOf(':');
	const ns = i < 0 ? '' : id.slice(0, i);
	const path = i < 0 ? id : id.slice(i + 1);
	// The game reads an empty namespace or path as legal (`minecraft:`), so only
	// the characters are checked here; callers refuse blanks they care about.
	if (ns !== '' && namespaceError(ns)) return namespaceError(ns);
	return path === '' ? null : pathError(path, false);
}
