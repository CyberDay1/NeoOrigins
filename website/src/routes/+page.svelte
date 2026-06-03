<script lang="ts">
	import { base } from '$app/paths';
</script>

<section class="hero">
	<h1>NeoOrigins datapack editor</h1>
	<p class="lede">
		Edit, validate, and download a ready-to-drop <code>.zip</code> — no install,
		no Gradle, no JSON hand-rolling.
	</p>

	<div class="cta">
		<a class="btn primary" href="{base}/editor/origin/">New Origin</a>
		<a class="btn ghost" href="{base}/editor/mob_origin/">New Mob Origin</a>
	</div>
</section>

<section class="features">
	<article class="card">
		<div class="card-top">
			<span class="idx">01</span>
			<span class="card-rule"></span>
		</div>
		<h3>Schema-driven</h3>
		<p>
			Power forms are generated from the same JSON schema the mod loads at
			runtime. If the form lets you set it, the loader accepts it.
		</p>
	</article>
	<article class="card">
		<div class="card-top">
			<span class="idx">02</span>
			<span class="card-rule"></span>
		</div>
		<h3>Live JSON preview</h3>
		<p>
			Every edit re-serialises immediately, with AJV validation against the
			origin and power schemas. Spot errors before you export.
		</p>
	</article>
	<article class="card">
		<div class="card-top">
			<span class="idx">03</span>
			<span class="card-rule"></span>
		</div>
		<h3>Autosaves locally</h3>
		<p>
			Drafts persist in your browser, so a refresh doesn't cost you the
			origin you've been tuning for the last twenty minutes.
		</p>
	</article>
</section>

<style>
	.hero {
		display: flex;
		flex-direction: column;
		align-items: center;
		text-align: center;
		padding: var(--space-6) var(--space-4) var(--space-5);
		gap: var(--space-3);
	}
	h1 {
		margin: 0;
		font-family: var(--font-display);
		font-size: 1.6rem;
		line-height: 1.15;
		letter-spacing: -0.02em;
		font-weight: 700;
		color: var(--color-text);
	}
	.lede {
		margin: 0;
		color: var(--color-text-muted);
		font-size: 1rem;
		line-height: 1.6;
		max-width: 48ch;
	}
	.lede code {
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.05rem 0.35rem;
		font-size: 0.85em;
	}
	.cta {
		margin-top: var(--space-2);
		display: flex;
		gap: var(--space-3);
		justify-content: center;
		flex-wrap: wrap;
	}
	.btn {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.7rem 1.4rem;
		font-size: 0.95rem;
		font-weight: 500;
		font-family: inherit;
		border-radius: var(--radius-md);
		border: 1px solid transparent;
		cursor: pointer;
		text-decoration: none;
		transition: background 120ms ease, border-color 120ms ease, color 120ms ease;
	}
	.btn.primary {
		background: var(--color-accent);
		color: var(--color-accent-contrast);
		font-weight: 600;
	}
	.btn.primary:hover {
		background: var(--color-accent-hover);
	}
	.btn.ghost {
		background: var(--color-surface);
		color: var(--color-text-muted);
		border-color: var(--color-border);
	}
	.btn.ghost:hover {
		border-color: var(--color-border-strong);
		color: var(--color-text);
	}

	.features {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(248px, 1fr));
		gap: var(--space-4);
		margin-top: var(--space-6);
	}
	.card {
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		padding: var(--space-5);
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		transition: border-color 150ms ease;
	}
	.card:hover {
		border-color: var(--color-border-strong);
	}
	.card-top {
		display: flex;
		align-items: center;
		gap: 0.85rem;
		margin-bottom: var(--space-2);
	}
	.idx {
		font-family: var(--font-mono);
		font-size: 0.82rem;
		font-weight: 600;
		letter-spacing: 0.05em;
		color: var(--color-accent);
	}
	.card-rule {
		flex: 1;
		height: 1px;
		background: var(--color-border);
	}
	.card h3 {
		margin: 0;
		font-family: var(--font-display);
		font-size: 1.1rem;
		font-weight: 700;
		letter-spacing: -0.01em;
		color: var(--color-text);
	}
	.card p {
		margin: 0;
		color: var(--color-text-muted);
		font-size: 0.88rem;
		line-height: 1.55;
	}
</style>
