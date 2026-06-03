<script lang="ts">
	import { untrack } from 'svelte';
	import type { RawJsonFieldSpec } from '$lib/schema/FormFieldSpec';

	// The store holds raw-JSON fields as their *parsed* value (object / array /
	// scalar) — that's what the datapack importer produces and the serializer
	// emits atomically. The textarea, though, is text. So we keep a local string
	// buffer: parsed values are pretty-printed in, and valid edits are parsed
	// back out, so the store keeps its parsed-value invariant (and exports stay
	// correct). Without this, an imported object would coerce to "[object
	// Object]" and fail to parse.
	let {
		field,
		value = $bindable('')
	}: { field: RawJsonFieldSpec; value: unknown } = $props();

	const id = $derived(`f-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);
	const descId = $derived(`${id}-desc`);
	const errId = $derived(`${id}-err`);

	// Ghost placeholder showing the expected JSON shape — mirrors the in-game
	// creator's raw-JSON hint so an empty box isn't a blank mystery.
	const shapeHint = $derived(
		{
			REF: '{ "type": … }',
			ARRAY: '[ … ]',
			OBJECT: '{ … }',
			MIXED: 'value or { … }',
			UNKNOWN: 'JSON'
		}[field.reason] ?? 'JSON'
	);

	/** Render a stored value as editable text. Objects/arrays are pretty-printed. */
	function toText(v: unknown): string {
		if (v === '' || v === undefined || v === null) return '';
		if (typeof v === 'string') return v;
		try {
			return JSON.stringify(v, null, 2);
		} catch {
			return String(v);
		}
	}

	function tryParse(t: string): { ok: true; value: unknown } | { ok: false } {
		try {
			return { ok: true, value: JSON.parse(t) };
		} catch {
			return { ok: false };
		}
	}

	function deepEqual(a: unknown, b: unknown): boolean {
		if (a === b) return true;
		if (typeof a !== typeof b || a === null || b === null) return false;
		if (Array.isArray(a) || Array.isArray(b)) {
			if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) return false;
			return a.every((x, i) => deepEqual(x, b[i]));
		}
		if (typeof a === 'object') {
			const ao = a as Record<string, unknown>;
			const bo = b as Record<string, unknown>;
			const ak = Object.keys(ao);
			if (ak.length !== Object.keys(bo).length) return false;
			return ak.every((k) => k in bo && deepEqual(ao[k], bo[k]));
		}
		return false;
	}

	// True when the current text buffer already represents `v` — so an external
	// `value` change that is just the echo of our own edit doesn't reformat the
	// textarea mid-typing.
	function bufferMatches(v: unknown, t: string): boolean {
		if ((v === '' || v === undefined || v === null) && t === '') return true;
		if (v === t) return true;
		const parsed = tryParse(t);
		return parsed.ok && deepEqual(parsed.value, v);
	}

	// Local display buffer for the textarea.
	let text = $state(toText(untrack(() => value)));

	// Re-sync the buffer when `value` changes from outside (import, type switch,
	// template load) — but not when it's merely the parsed echo of our own edit.
	$effect(() => {
		const v = value;
		if (!bufferMatches(v, untrack(() => text))) {
			text = toText(v);
		}
	});

	function onInput(e: Event) {
		const t = (e.currentTarget as HTMLTextAreaElement).value;
		text = t;
		// Store the parsed value when valid (keeps the parsed-value invariant);
		// otherwise stash the raw text so in-progress edits aren't lost and the
		// invalid state is surfaced.
		const parsed = tryParse(t);
		value = t === '' ? '' : parsed.ok ? parsed.value : t;
	}

	// JSON validity tracking — same one-line escape hatch the in-game creator
	// uses for OBJECT / ARRAY / REF / MIXED / UNKNOWN fields. Empty is "unset".
	let parseError = $derived.by<string | null>(() => {
		if (text === '') return null;
		// MIXED fields (e.g. name/description) accept a plain string OR a JSON
		// object/array. A bare string like "Cold Blooded" is a valid value, so
		// only validate as JSON when the text is clearly attempting structured
		// JSON (first non-space char is { or [). Otherwise it's a string — no error.
		if (field.reason === 'MIXED' && !/^\s*[{[]/.test(text)) return null;
		try {
			JSON.parse(text);
			return null;
		} catch (e) {
			return e instanceof Error ? e.message : String(e);
		}
	});

	const describedBy = $derived(
		[field.description ? descId : null, parseError !== null ? errId : null]
			.filter(Boolean)
			.join(' ') || undefined
	);
</script>

<div class="row">
	<label class="lbl" for={id}>
		{field.label}
		{#if field.required}<span class="req" aria-label="required">*</span>{/if}
		<span class="kind">[{field.reason} — raw JSON]</span>
	</label>
	<textarea
		{id}
		rows="3"
		placeholder={shapeHint}
		class:invalid={parseError !== null}
		aria-invalid={parseError !== null || undefined}
		aria-describedby={describedBy}
		value={text}
		oninput={onInput}
	></textarea>
	{#if field.description}
		<small class="desc" id={descId}>{field.description}</small>
	{/if}
	{#if parseError !== null}
		<small class="err" id={errId}>JSON error: {parseError}</small>
	{:else if value !== ''}
		<small class="ok">JSON OK</small>
	{/if}
</div>

<style>
	.row {
		display: grid;
		grid-template-columns: 13rem 1fr;
		align-items: start;
		gap: var(--space-2);
		padding: 0.5rem 0;
		border-bottom: 1px solid var(--color-border);
	}
	.lbl {
		color: var(--color-text);
		font-size: 0.85rem;
		font-weight: 500;
		padding-top: 0.35rem;
	}
	.req {
		color: var(--color-accent);
		margin-left: 0.2rem;
	}
	.kind {
		display: block;
		color: var(--color-text-subtle);
		font-size: 0.7rem;
		font-weight: normal;
		font-family: var(--font-mono);
		margin-top: 0.15rem;
	}
	.desc {
		grid-column: 2;
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	.err {
		grid-column: 2;
		color: var(--color-danger);
		font-size: 0.78rem;
	}
	.ok {
		grid-column: 2;
		color: var(--color-success);
		font-size: 0.78rem;
	}
	textarea {
		background: var(--color-bg);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.5rem 0.6rem;
		font-family: var(--font-mono);
		font-size: 0.82rem;
		line-height: 1.5;
		width: 100%;
		max-width: 40rem;
		resize: vertical;
		transition: border-color 120ms ease;
	}
	textarea:hover {
		border-color: var(--color-border-strong);
	}
	textarea:focus {
		border-color: var(--color-accent);
	}
	textarea.invalid {
		border-color: var(--color-danger);
	}
</style>
