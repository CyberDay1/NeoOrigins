<script lang="ts">
	// JSON Preview tab — live origin + per-power JSON with AJV validation.
	// See planning/web_editor_scope.md §3.
	//
	// Flow:
	//   draft (store) → serializeOrigin() → SerializedDatapackBundle
	//                                     → AJV-validate origin + each power
	//                                     → render path-headed code blocks
	//                                     → list errors with section prefix
	//
	// Syntax highlighting: a small regex-only pass over the JSON.stringify
	// output. ~25 lines, no extra deps. Colors string keys, string values,
	// numbers, booleans, and `null`. If anything in the rendered JSON looks
	// off, fall back to plain text by removing the `{@html ...}` and just
	// emitting `{json}`.

	import { base } from '$app/paths';
	import { draft } from '$lib/stores/originDraft';
	import { serializeOrigin } from '$lib/schema/originSerializer';
	import { getValidator, type ErrorObject } from '$lib/schema/ajv';

	// ── reactive serialization ────────────────────────────────────────────
	const bundle = $derived(serializeOrigin($draft));

	// ── async validation ──────────────────────────────────────────────────
	// AJV setup is async (it fetches schemas). We model the result as a
	// promise the template renders via `{#await}`. Keyed off a stable
	// JSON.stringify so unrelated reactivity doesn't refire.
	const bundleKey = $derived(
		JSON.stringify({ o: bundle.origin, p: bundle.powers.map((p) => p.json) })
	);

	interface ValidationIssue {
		section: string; // e.g. "origin" or "power: mypack:flight"
		pointer: string;
		message: string;
		keyword: string;
	}

	interface ValidationResult {
		issues: ValidationIssue[];
		failed: boolean;
		errored: boolean;
		errorText?: string;
	}

	async function validateBundle(): Promise<ValidationResult> {
		const issues: ValidationIssue[] = [];
		try {
			const originValidator = await getValidator(`${base}/schemas/origin.schema.json`);
			const powerValidator = await getValidator(`${base}/schemas/power.schema.json`);

			if (!originValidator(bundle.origin)) {
				for (const e of originValidator.errors ?? []) {
					issues.push(toIssue('origin', e));
				}
			}
			for (const p of bundle.powers) {
				if (!powerValidator(p.json)) {
					for (const e of powerValidator.errors ?? []) {
						issues.push(toIssue(`power: ${p.fullId || p.id || '(unset)'}`, e));
					}
				}
			}
			return { issues, failed: issues.length > 0, errored: false };
		} catch (err) {
			return {
				issues,
				failed: false,
				errored: true,
				errorText: err instanceof Error ? err.message : String(err)
			};
		}
	}

	function toIssue(section: string, e: ErrorObject): ValidationIssue {
		return {
			section,
			pointer: e.instancePath || '(root)',
			message: e.message ?? '(no message)',
			keyword: e.keyword
		};
	}

	// Re-validation runs whenever the bundle JSON shape changes.
	let validationPromise = $derived.by(() => {
		// Touch the key so the derivation re-runs.
		void bundleKey;
		return validateBundle();
	});

	// ── syntax highlight (regex-only, ~25 lines) ──────────────────────────
	function escapeHtml(s: string): string {
		return s
			.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;');
	}

	function highlight(jsonText: string): string {
		// Order matters: match strings first (with optional trailing colon
		// to distinguish keys from values), then numbers, then keywords.
		return escapeHtml(jsonText).replace(
			/("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b/g,
			(match, str, colon, kw) => {
				if (str !== undefined) {
					if (colon) return `<span class="jk">${str}</span>${colon}`;
					return `<span class="js">${str}</span>`;
				}
				if (kw !== undefined) return `<span class="jb">${kw}</span>`;
				return `<span class="jn">${match}</span>`;
			}
		);
	}

	function formatJson(value: unknown): string {
		return JSON.stringify(value, null, 2);
	}

	// ── copy-to-clipboard ─────────────────────────────────────────────────
	let copiedKey = $state<string | null>(null);
	let copyTimer: ReturnType<typeof setTimeout> | null = null;

	async function copy(text: string, key: string): Promise<void> {
		try {
			await navigator.clipboard.writeText(text);
			copiedKey = key;
			if (copyTimer) clearTimeout(copyTimer);
			copyTimer = setTimeout(() => {
				copiedKey = null;
			}, 1200);
		} catch {
			// Clipboard API can fail in non-secure contexts; surface nothing
			// flashy, the user can still select-and-copy from the <pre>.
		}
	}
</script>

<section aria-labelledby="json-heading" class="tab">
	<h2 id="json-heading">JSON Preview</h2>

	{#await validationPromise}
		<div class="status status-loading">Validating...</div>
	{:then result}
		{#if result.errored}
			<div class="status status-warn">
				Schemas unavailable: {result.errorText}
			</div>
		{:else if result.failed}
			<div class="status status-bad">
				{result.issues.length}
				{result.issues.length === 1 ? 'error' : 'errors'}
			</div>
		{:else}
			<div class="status status-ok">Valid</div>
		{/if}

		{#if result.issues.length > 0}
			<ul class="errors" role="alert" aria-label="Validation errors">
				{#each result.issues as issue, i (i)}
					<li>
						<span class="err-section">{issue.section}</span>
						<code class="err-ptr">{issue.pointer}</code>
						<span class="err-msg">{issue.message}</span>
						<span class="err-kw">[{issue.keyword}]</span>
					</li>
				{/each}
			</ul>
		{/if}
	{/await}

	<article class="block">
		<header class="block-head">
			<code class="path">{bundle.originPath}</code>
			<button
				type="button"
				class="copy"
				aria-label={`Copy ${bundle.originPath}`}
				onclick={() => copy(formatJson(bundle.origin), 'origin')}
			>
				{copiedKey === 'origin' ? 'Copied' : 'Copy'}
			</button>
		</header>
		<pre><code>{@html highlight(formatJson(bundle.origin))}</code></pre>
	</article>

	{#if bundle.powers.length === 0}
		<p class="empty">No powers yet — add some in the Powers tab.</p>
	{:else}
		{#each bundle.powers as p (p.id || p.fullId)}
			<article class="block">
				<header class="block-head">
					<code class="path">{p.path}</code>
					<button
						type="button"
						class="copy"
						aria-label={`Copy ${p.path}`}
						onclick={() => copy(formatJson(p.json), `power-${p.id}`)}
					>
						{copiedKey === `power-${p.id}` ? 'Copied' : 'Copy'}
					</button>
				</header>
				<pre><code>{@html highlight(formatJson(p.json))}</code></pre>
			</article>
		{/each}
	{/if}
</section>

<style>
	.tab {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}
	h2 {
		margin: 0;
		color: var(--color-text);
		font-size: 1.05rem;
		font-weight: 600;
		letter-spacing: -0.01em;
	}
	.status {
		display: inline-block;
		padding: 0.3rem 0.65rem;
		border-radius: var(--radius-pill);
		font-size: 0.8rem;
		font-weight: 600;
		align-self: flex-start;
	}
	.status-ok {
		background: var(--color-success-subtle);
		color: var(--color-success);
		border: 1px solid color-mix(in srgb, var(--color-success) 40%, transparent);
	}
	.status-bad {
		background: var(--color-danger-subtle);
		color: var(--color-danger);
		border: 1px solid color-mix(in srgb, var(--color-danger) 40%, transparent);
	}
	.status-warn {
		background: var(--color-warning-subtle);
		color: var(--color-warning);
		border: 1px solid color-mix(in srgb, var(--color-warning) 40%, transparent);
	}
	.status-loading {
		background: var(--color-bg-subtle);
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
	}
	.errors {
		list-style: none;
		margin: 0;
		padding: var(--space-2) var(--space-3);
		background: var(--color-danger-subtle);
		border: 1px solid color-mix(in srgb, var(--color-danger) 35%, var(--color-border));
		border-radius: var(--radius-md);
		color: var(--color-text);
		font-size: 0.85rem;
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}
	.errors li {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem;
		align-items: baseline;
	}
	.err-section {
		color: var(--color-warning);
		font-weight: 600;
	}
	.err-ptr {
		font-family: var(--font-mono);
		color: var(--color-text);
		background: var(--color-bg);
		padding: 0.05rem 0.35rem;
		border-radius: var(--radius-sm);
		border: 1px solid var(--color-border);
		font-size: 0.78rem;
	}
	.err-msg {
		color: var(--color-text);
		flex: 1 1 auto;
	}
	.err-kw {
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	.block {
		display: flex;
		flex-direction: column;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: var(--color-bg-subtle);
		overflow: hidden;
	}
	.block-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-2);
		padding: 0.5rem 0.7rem;
		background: var(--color-surface);
		border-bottom: 1px solid var(--color-border);
	}
	.path {
		font-family: var(--font-mono);
		font-size: 0.8rem;
		color: var(--color-text-muted);
		word-break: break-all;
	}
	.copy {
		background: var(--color-bg-subtle);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.25rem 0.65rem;
		font: inherit;
		font-size: 0.76rem;
		font-weight: 500;
		cursor: pointer;
		flex-shrink: 0;
		transition: background 120ms ease, border-color 120ms ease,
			color 120ms ease;
	}
	.copy:hover {
		background: var(--color-surface-hover);
		border-color: var(--color-border-strong);
		color: var(--color-accent);
	}
	pre {
		margin: 0;
		padding: var(--space-3);
		overflow-x: auto;
		font-family: var(--font-mono);
		font-size: 0.8rem;
		line-height: 1.55;
		color: var(--color-text);
		background: var(--color-bg);
	}
	pre :global(.jk) {
		color: var(--color-syntax-key);
	}
	pre :global(.js) {
		color: var(--color-syntax-string);
	}
	pre :global(.jn) {
		color: var(--color-syntax-number);
	}
	pre :global(.jb) {
		color: var(--color-syntax-bool);
		font-weight: 500;
	}
	.empty {
		color: var(--color-text-muted);
		font-style: italic;
		margin: 0;
	}
</style>
