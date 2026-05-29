<script lang="ts">
	import type { RawJsonFieldSpec } from '$lib/schema/FormFieldSpec';

	let {
		field,
		value = $bindable('')
	}: { field: RawJsonFieldSpec; value: string } = $props();

	const id = $derived(`f-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);

	// JSON validity tracking — same one-line escape hatch the in-game
	// creator uses for OBJECT / ARRAY / REF / MIXED / UNKNOWN fields.
	// Empty string is treated as "unset" (valid).
	let parseError = $derived.by<string | null>(() => {
		if (value === '' || value === undefined || value === null) return null;
		try {
			JSON.parse(value);
			return null;
		} catch (e) {
			return e instanceof Error ? e.message : String(e);
		}
	});
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
		class:invalid={parseError !== null}
		bind:value
	></textarea>
	{#if field.description}
		<small class="desc">{field.description}</small>
	{/if}
	{#if parseError !== null}
		<small class="err">JSON error: {parseError}</small>
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
