<script lang="ts">
	import { base } from '$app/paths';
</script>

<section class="hero">
	<div class="kicker">Datapack toolkit</div>
	<h1>
		Build NeoOrigins datapacks <span class="accent">in your browser.</span>
	</h1>
	<p class="lede">
		Edit. Validate. Download a ready-to-drop <code>.zip</code> — no install,
		no Gradle, no JSON hand-rolling.
	</p>

	<div class="cta">
		<a class="btn primary" href="{base}/editor/origin/">New Origin</a>
		<button class="btn ghost" type="button" disabled title="Coming in Phase 2">
			New Mob Origin
			<span class="badge">Phase 2</span>
		</button>
	</div>
</section>

<section class="features">
	<article class="card">
		<div class="card-icon" aria-hidden="true">◆</div>
		<h3>Schema-driven</h3>
		<p>
			Power forms are generated from the same JSON schema the mod loads at
			runtime. If the form lets you set it, the loader accepts it.
		</p>
	</article>
	<article class="card">
		<div class="card-icon" aria-hidden="true">◇</div>
		<h3>Live JSON preview</h3>
		<p>
			Every edit re-serialises immediately, with AJV validation against the
			origin and power schemas. Spot errors before you export.
		</p>
	</article>
	<article class="card">
		<div class="card-icon" aria-hidden="true">◈</div>
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
		padding: var(--space-7) var(--space-4) var(--space-6);
		gap: var(--space-3);
	}
	.kicker {
		font-size: 0.78rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.12em;
		color: var(--color-accent);
		padding: 0.25rem 0.65rem;
		background: var(--color-accent-subtle);
		border-radius: var(--radius-pill);
	}
	h1 {
		margin: 0;
		font-size: clamp(2rem, 4.5vw, 3rem);
		line-height: 1.1;
		letter-spacing: -0.02em;
		font-weight: 700;
		color: var(--color-text);
		max-width: 36ch;
	}
	.accent {
		background: linear-gradient(
			120deg,
			var(--color-accent) 0%,
			color-mix(in srgb, var(--color-accent) 55%, #ec4899) 100%
		);
		-webkit-background-clip: text;
		background-clip: text;
		-webkit-text-fill-color: transparent;
		color: transparent;
	}
	.lede {
		margin: 0;
		color: var(--color-text-muted);
		font-size: 1.05rem;
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
		margin-top: var(--space-4);
		display: flex;
		gap: var(--space-3);
		justify-content: center;
		flex-wrap: wrap;
	}
	.btn {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.7rem 1.25rem;
		font-size: 0.95rem;
		font-weight: 500;
		font-family: inherit;
		border-radius: var(--radius-md);
		border: 1px solid transparent;
		cursor: pointer;
		text-decoration: none;
		transition: background 120ms ease, border-color 120ms ease,
			color 120ms ease, transform 80ms ease;
	}
	.btn:active {
		transform: translateY(1px);
	}
	.btn.primary {
		background: var(--color-accent);
		color: var(--color-accent-contrast);
	}
	.btn.primary:hover {
		background: var(--color-accent-hover);
	}
	.btn.ghost {
		background: var(--color-surface);
		color: var(--color-text-muted);
		border-color: var(--color-border);
	}
	.btn.ghost:disabled {
		cursor: not-allowed;
		opacity: 0.7;
	}
	.badge {
		font-size: 0.7rem;
		font-weight: 600;
		padding: 0.1rem 0.45rem;
		border-radius: var(--radius-pill);
		background: var(--color-bg-subtle);
		color: var(--color-text-subtle);
		border: 1px solid var(--color-border);
	}

	.features {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
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
		box-shadow: var(--shadow-sm);
	}
	.card-icon {
		width: 2rem;
		height: 2rem;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		border-radius: var(--radius-md);
		background: var(--color-accent-subtle);
		color: var(--color-accent);
		font-size: 1rem;
	}
	.card h3 {
		margin: 0;
		font-size: 0.98rem;
		font-weight: 600;
		color: var(--color-text);
	}
	.card p {
		margin: 0;
		color: var(--color-text-muted);
		font-size: 0.88rem;
		line-height: 1.55;
	}
</style>
