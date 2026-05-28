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
		grid-template-columns: 12rem 1fr;
		align-items: start;
		gap: 0.5rem;
		padding: 0.4rem 0;
		border-bottom: 1px solid #2a2a2a;
	}
	.lbl {
		color: #e6e6e6;
		font-size: 0.9rem;
		padding-top: 0.3rem;
	}
	.req {
		color: #4a90e2;
		margin-left: 0.25rem;
	}
	.kind {
		display: block;
		color: #777;
		font-size: 0.7rem;
		font-weight: normal;
	}
	.desc {
		grid-column: 2;
		color: #999;
		font-size: 0.78rem;
	}
	.err {
		grid-column: 2;
		color: #e25d4a;
		font-size: 0.78rem;
	}
	.ok {
		grid-column: 2;
		color: #4ae278;
		font-size: 0.78rem;
	}
	textarea {
		background: #1a1a1a;
		color: #e6e6e6;
		border: 1px solid #333;
		border-radius: 3px;
		padding: 0.4rem 0.5rem;
		font: 0.85rem ui-monospace, 'Cascadia Code', Consolas, monospace;
		width: 100%;
		max-width: 36rem;
		resize: vertical;
	}
	textarea:focus {
		outline: none;
		border-color: #4a90e2;
	}
	textarea.invalid {
		border-color: #e25d4a;
	}
</style>
