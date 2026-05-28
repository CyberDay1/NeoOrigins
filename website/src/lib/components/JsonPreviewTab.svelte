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
			<ul class="errors">
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
		gap: 1rem;
	}
	h2 {
		margin: 0 0 0.25rem;
		color: #e6e6e6;
	}
	.status {
		display: inline-block;
		padding: 0.25rem 0.6rem;
		border-radius: 3px;
		font-size: 0.85rem;
		font-weight: 600;
		align-self: flex-start;
	}
	.status-ok {
		background: #1f3d27;
		color: #7fd494;
		border: 1px solid #2d5a39;
	}
	.status-bad {
		background: #3d1f1f;
		color: #e25d4a;
		border: 1px solid #5a2d2d;
	}
	.status-warn {
		background: #3d331f;
		color: #d4b67f;
		border: 1px solid #5a4d2d;
	}
	.status-loading {
		background: #222;
		color: #999;
		border: 1px solid #333;
	}
	.errors {
		list-style: none;
		margin: 0;
		padding: 0.5rem 0.75rem;
		background: #2a1818;
		border: 1px solid #4a2828;
		border-radius: 3px;
		color: #e6e6e6;
		font-size: 0.85rem;
		display: flex;
		flex-direction: column;
		gap: 0.3rem;
	}
	.errors li {
		display: flex;
		flex-wrap: wrap;
		gap: 0.5rem;
		align-items: baseline;
	}
	.err-section {
		color: #d4a07f;
		font-weight: 600;
	}
	.err-ptr {
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
		color: #b8b8b8;
		background: #1a1010;
		padding: 0 0.3rem;
		border-radius: 2px;
	}
	.err-msg {
		color: #e6e6e6;
		flex: 1 1 auto;
	}
	.err-kw {
		color: #888;
		font-size: 0.78rem;
	}
	.block {
		display: flex;
		flex-direction: column;
		border: 1px solid #333;
		border-radius: 3px;
		background: #1a1a1a;
		overflow: hidden;
	}
	.block-head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.5rem;
		padding: 0.4rem 0.6rem;
		background: #222;
		border-bottom: 1px solid #333;
	}
	.path {
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
		font-size: 0.82rem;
		color: #b8b8b8;
		word-break: break-all;
	}
	.copy {
		background: #2a2a2a;
		color: #e6e6e6;
		border: 1px solid #3a3a3a;
		border-radius: 3px;
		padding: 0.2rem 0.6rem;
		font: inherit;
		font-size: 0.78rem;
		cursor: pointer;
		flex-shrink: 0;
	}
	.copy:hover {
		background: #333;
		border-color: #4a4a4a;
	}
	pre {
		margin: 0;
		padding: 0.6rem 0.75rem;
		overflow-x: auto;
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
		font-size: 0.82rem;
		line-height: 1.4;
		color: #e6e6e6;
	}
	pre :global(.jk) {
		color: #7fb5d4;
	}
	pre :global(.js) {
		color: #d4a07f;
	}
	pre :global(.jn) {
		color: #b89cd4;
	}
	pre :global(.jb) {
		color: #d47fb5;
	}
	.empty {
		color: #999;
		font-style: italic;
		margin: 0;
	}
</style>
