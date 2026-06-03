<script lang="ts">
	// A text input with a custom autocomplete dropdown. Suggestions are hints, not
	// constraints — any value can still be typed. The menu filters the list as you
	// type (prefix matches first, then substring), supports full keyboard nav
	// (↓/↑/Enter/Esc), and is rendered with `position: fixed` anchored to the
	// input's box so it escapes any `overflow` scroll container (e.g. the drops
	// table) instead of being clipped. When no suggestions apply the field
	// degrades to a plain text input (no combobox role).

	let {
		value = '',
		suggestions = [],
		id,
		placeholder = '',
		disabled = false,
		invalid = false,
		ariaLabel,
		ariaDescribedby,
		mono = true,
		oninput
	}: {
		value?: string;
		suggestions?: string[];
		id?: string;
		placeholder?: string;
		disabled?: boolean;
		invalid?: boolean;
		ariaLabel?: string;
		ariaDescribedby?: string;
		mono?: boolean;
		oninput: (value: string) => void;
	} = $props();

	const MAX = 50;
	const rnd = `sg-${Math.random().toString(36).slice(2)}`;
	let baseId = $derived(id ?? rnd);
	let listId = $derived(`${baseId}-list`);

	let open = $state(false);
	let highlight = $state(-1);
	let inputEl: HTMLInputElement | undefined = $state();
	let menuEl: HTMLUListElement | undefined = $state();
	let pos = $state({ left: 0, top: 0, width: 0 });

	let hasSuggestions = $derived(suggestions.length > 0);

	// Prefix matches first, then substring matches; the already-typed exact value
	// is dropped (nothing to suggest). Capped so the menu stays light.
	let filtered = $derived.by(() => {
		if (!hasSuggestions) return [] as string[];
		const q = value.trim().toLowerCase();
		if (q === '') return suggestions.slice(0, MAX);
		const starts: string[] = [];
		const contains: string[] = [];
		for (const s of suggestions) {
			const l = s.toLowerCase();
			if (l === q) continue;
			if (l.startsWith(q)) starts.push(s);
			else if (l.includes(q)) contains.push(s);
		}
		return [...starts, ...contains].slice(0, MAX);
	});

	let showMenu = $derived(open && !disabled && filtered.length > 0);

	function reposition() {
		if (!inputEl) return;
		const r = inputEl.getBoundingClientRect();
		pos = { left: r.left, top: r.bottom + 4, width: r.width };
	}

	// Move the menu to <body> so its `position: fixed` coordinates resolve
	// against the viewport, not a transformed ancestor (the editor's `.tab-card`
	// carries a transform, which would otherwise act as the fixed containing
	// block and offset the menu). Also keeps it clear of any `overflow` clipping.
	function portal(node: HTMLElement) {
		document.body.appendChild(node);
		return {
			destroy() {
				node.remove();
			}
		};
	}

	// While the menu is open, keep it pinned to the input through scroll/resize.
	$effect(() => {
		if (!showMenu) return;
		reposition();
		const onMove = () => reposition();
		window.addEventListener('scroll', onMove, true);
		window.addEventListener('resize', onMove);
		return () => {
			window.removeEventListener('scroll', onMove, true);
			window.removeEventListener('resize', onMove);
		};
	});

	// Keep the highlighted option in view during keyboard navigation.
	$effect(() => {
		if (!showMenu || highlight < 0 || !menuEl) return;
		(menuEl.children[highlight] as HTMLElement | undefined)?.scrollIntoView({ block: 'nearest' });
	});

	function choose(s: string) {
		oninput(s);
		open = false;
		highlight = -1;
	}

	function handleInput(e: Event) {
		oninput((e.currentTarget as HTMLInputElement).value);
		open = true;
		highlight = -1;
	}

	function handleKeydown(e: KeyboardEvent) {
		if (e.key === 'ArrowDown') {
			if (!showMenu) {
				open = true;
				return;
			}
			e.preventDefault();
			highlight = (highlight + 1) % filtered.length;
		} else if (e.key === 'ArrowUp') {
			if (!showMenu) return;
			e.preventDefault();
			highlight = (highlight - 1 + filtered.length) % filtered.length;
		} else if (e.key === 'Enter') {
			if (showMenu && highlight >= 0) {
				e.preventDefault();
				choose(filtered[highlight]);
			}
		} else if (e.key === 'Escape') {
			if (showMenu) {
				e.stopPropagation();
				open = false;
				highlight = -1;
			}
		} else if (e.key === 'Tab') {
			open = false;
		}
	}
</script>

<div class="sg-combo">
	<input
		bind:this={inputEl}
		id={baseId}
		type="text"
		class:mono
		class:invalid
		role={hasSuggestions ? 'combobox' : undefined}
		aria-expanded={hasSuggestions ? showMenu : undefined}
		aria-controls={hasSuggestions ? listId : undefined}
		aria-autocomplete={hasSuggestions ? 'list' : undefined}
		aria-activedescendant={showMenu && highlight >= 0 ? `${listId}-opt-${highlight}` : undefined}
		{value}
		{placeholder}
		{disabled}
		aria-label={ariaLabel}
		aria-invalid={invalid || undefined}
		aria-describedby={ariaDescribedby}
		autocomplete="off"
		spellcheck="false"
		oninput={handleInput}
		onkeydown={handleKeydown}
		onfocus={() => (open = true)}
		onblur={() => (open = false)}
	/>
	{#if showMenu}
		<ul
			use:portal
			bind:this={menuEl}
			id={listId}
			class="sg-menu"
			role="listbox"
			style="left:{pos.left}px;top:{pos.top}px;width:{pos.width}px;"
		>
			{#each filtered as s, i (s)}
				<!-- Keyboard nav is handled on the combobox input (ARIA listbox pattern);
				     options are pointer affordances only. -->
				<!-- svelte-ignore a11y_click_events_have_key_events -->
				<li
					id="{listId}-opt-{i}"
					role="option"
					aria-selected={i === highlight}
					class:active={i === highlight}
					onmousedown={(e) => e.preventDefault()}
					onclick={() => choose(s)}
					onmouseenter={() => (highlight = i)}
				>
					{s}
				</li>
			{/each}
		</ul>
	{/if}
</div>

<style>
	.sg-combo {
		position: relative;
		width: 100%;
	}
	input[type='text'] {
		background: var(--color-bg-subtle);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.5rem 0.65rem;
		font: inherit;
		font-size: 0.88rem;
		width: 100%;
		max-width: 36rem;
		transition: border-color 120ms ease, background 120ms ease;
	}
	input.mono {
		font-family: var(--font-mono);
	}
	input[type='text']:hover:not(:disabled) {
		border-color: var(--color-border-strong);
	}
	input[type='text']:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	input[type='text']:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}
	input[type='text'].invalid {
		border-color: var(--color-danger);
	}

	.sg-menu {
		position: fixed;
		z-index: 60;
		margin: 0;
		padding: 0.25rem;
		list-style: none;
		max-height: 15rem;
		overflow-y: auto;
		background: var(--color-surface);
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-md);
		box-shadow: var(--shadow-lg, 0 8px 24px rgb(0 0 0 / 0.28));
	}
	.sg-menu li {
		display: block;
		padding: 0.35rem 0.55rem 0.35rem 0.5rem;
		border-left: 3px solid transparent;
		border-radius: var(--radius-sm);
		color: var(--color-text);
		font-family: var(--font-mono);
		font-size: 0.82rem;
		line-height: 1.3;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
		cursor: pointer;
	}
	.sg-menu li.active {
		background: var(--color-surface-hover);
		/* Left bar in addition to background — don't rely on colour alone. */
		border-left-color: var(--color-accent);
		color: var(--color-text);
	}
</style>
