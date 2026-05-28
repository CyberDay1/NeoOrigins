<script lang="ts">
	// TODO: schema-driven field row.
	//
	// Reads a FormFieldSpec entry and dispatches to the right concrete widget
	// (BoolRow / NumericRow / EnumRow / TextRow / RefRow / ArrayRefRow).
	// Tier-B kinds fall through to a raw-JSON <textarea>.
	//
	// 1:1 with the Java `FieldWidgetFactory` dispatch table.

	let { label = 'TODO' }: { label?: string } = $props();
</script>

<div class="field-row">
	<label>{label}</label>
	<span class="todo">TODO: schema-driven field row</span>
</div>

<style>
	.field-row {
		display: flex;
		gap: 0.5rem;
		align-items: center;
		padding: 0.25rem 0;
	}
	.todo {
		color: #777;
		font-style: italic;
	}
</style>
