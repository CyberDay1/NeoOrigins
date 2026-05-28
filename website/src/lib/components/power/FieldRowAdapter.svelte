<script lang="ts">
	// Bridge between `FieldRow`'s `bind:value` contract and the
	// store-update callback idiom used by the rest of the editor.
	//
	// FieldRow exposes a `$bindable` `value` prop. To keep the store as the
	// single source of truth we mirror the incoming prop into a local
	// `$state` variable, then `$effect` on changes to that mirror and
	// forward to `onUpdate`. When the incoming prop changes (e.g. type
	// switch resets `power.fields[name]` to undefined), we re-sync the
	// mirror without re-firing `onUpdate`.

	import { untrack } from 'svelte';
	import type { FormFieldSpec } from '$lib/schema/FormFieldSpec';
	import FieldRow from '$lib/widgets/FieldRow.svelte';

	let {
		field,
		value,
		onUpdate
	}: {
		field: FormFieldSpec;
		value: unknown;
		onUpdate: (v: unknown) => void;
	} = $props();

	// Local mirror that FieldRow can `bind:value` against. Initialised
	// inside `untrack` so Svelte doesn't warn about capturing the snapshot
	// of `value` at module init time — the $effect below keeps it in sync.
	let mirror = $state<unknown>(untrack(() => value));
	let lastIncoming = $state<unknown>(undefined);

	// External → local sync. Only re-write the mirror when the prop
	// genuinely differs from the last incoming snapshot; otherwise we'd
	// thrash and clobber in-progress keystrokes.
	$effect(() => {
		if (value !== untrack(() => lastIncoming)) {
			lastIncoming = value;
			mirror = value;
		}
	});

	// Local → external sync. Skip the initial run by tracking a
	// `dispatched` flag — first $effect call is the mount snapshot.
	let dispatched = false;
	$effect(() => {
		const current = mirror;
		if (!dispatched) {
			dispatched = true;
			lastIncoming = current;
			return;
		}
		if (current !== untrack(() => lastIncoming)) {
			lastIncoming = current;
			onUpdate(current);
		}
	});
</script>

<FieldRow {field} bind:value={mirror} />
