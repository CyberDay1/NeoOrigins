<script lang="ts">
	// Schema-driven field row — TS port of the in-game
	// `FieldWidgetFactory.create()` dispatch (see
	// src/main/java/com/cyberday1/neoorigins/screen/creator/widget/FieldWidgetFactory.java).
	//
	// Reads one FormFieldSpec and dispatches to the matching Tier-A row, the
	// REF / ARRAY_REF / ARRAY_OBJECT / OBJECT sub-form rows, or the RawJsonRow escape hatch
	// for the remaining ARRAY / MIXED / UNKNOWN raw-JSON fallbacks.
	//
	// One MIXED shape is lifted back out of that fallback: a CLOSED boolean-or-string
	// choice, whose options cover the whole value space, gets the ENUM dropdown.

	import {
		asChoiceEnum,
		choiceOption,
		isBooleanStringChoice,
		type FormFieldSpec
	} from '$lib/schema/FormFieldSpec';
	import BoolRow from './BoolRow.svelte';
	import NumericRow from './NumericRow.svelte';
	import EnumRow from './EnumRow.svelte';
	import StringRow from './StringRow.svelte';
	import RefRow from './RefRow.svelte';
	import ArrayRefRow from './ArrayRefRow.svelte';
	import ArrayStringRow from './ArrayStringRow.svelte';
	import ArrayObjectRow from './ArrayObjectRow.svelte';
	import ObjectRow from './ObjectRow.svelte';
	import RawJsonRow from './RawJsonRow.svelte';

	let {
		field,
		value = $bindable()
	}: { field: FormFieldSpec; value: unknown } = $props();
</script>

{#if field.kind === 'BOOLEAN'}
	<BoolRow {field} bind:value={value as boolean} />
{:else if field.kind === 'INTEGER' || field.kind === 'NUMBER'}
	<NumericRow {field} bind:value={value as number | null} />
{:else if field.kind === 'ENUM'}
	<EnumRow {field} bind:value={value as string} />
{:else if field.kind === 'STRING'}
	<StringRow {field} bind:value={value as string} />
{:else if field.kind === 'REF'}
	<RefRow {field} bind:value={value as Record<string, unknown> | null} />
{:else if field.kind === 'ARRAY_REF'}
	<ArrayRefRow {field} bind:value />
{:else if field.kind === 'ARRAY_STRING'}
	<ArrayStringRow {field} bind:value={value as string[] | null} />
{:else if field.kind === 'ARRAY_OBJECT'}
	<ArrayObjectRow {field} bind:value />
{:else if field.kind === 'OBJECT'}
	<ObjectRow {field} bind:value={value as Record<string, unknown> | null} />
{:else if isBooleanStringChoice(field)}
	<!-- Load coerces a legacy boolean onto its option; save writes the string, so
	     editing normalises `true` into its spelling (identical semantics). -->
	<EnumRow
		field={asChoiceEnum(field)}
		bind:value={() => choiceOption(field, value), (v: string) => (value = v)}
	/>
{:else}
	<RawJsonRow {field} bind:value />
{/if}
