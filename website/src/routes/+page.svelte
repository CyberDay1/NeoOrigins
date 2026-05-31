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
		padding: var(--space-8) var(--space-4) var(--space-7);
		gap: var(--space-3);
	}
	.kicker {
		font-size: 0.72rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.16em;
		color: var(--color-accent);
		padding: 0.3rem 0.8rem;
		background: var(--color-accent-subtle);
		border: 1px solid color-mix(in srgb, var(--color-accent) 28%, transparent);
		border-radius: var(--radius-pill);
		box-shadow: 0 0 24px -6px color-mix(in srgb, var(--color-accent) 45%, transparent);
		animation: rise 0.6s cubic-bezier(0.2, 0.7, 0.2, 1) both;
	}
	h1 {
		margin: 0;
		font-family: var(--font-display);
		font-size: clamp(2.4rem, 6vw, 4rem);
		line-height: 1.04;
		letter-spacing: -0.03em;
		font-weight: 800;
		color: var(--color-text);
		max-width: 18ch;
		animation: rise 0.6s cubic-bezier(0.2, 0.7, 0.2, 1) both;
		animation-delay: 0.08s;
	}
	.accent {
		background: linear-gradient(
			110deg,
			var(--color-accent) 0%,
			var(--color-accent-2) 50%,
			var(--color-accent) 100%
		);
		background-size: 220% 100%;
		-webkit-background-clip: text;
		background-clip: text;
		-webkit-text-fill-color: transparent;
		color: transparent;
		animation: accent-flow 8s ease-in-out infinite;
	}
	@keyframes accent-flow {
		0%,
		100% {
			background-position: 0% 50%;
		}
		50% {
			background-position: 100% 50%;
		}
	}
	.lede {
		margin: 0;
		color: var(--color-text-muted);
		font-size: 1.1rem;
		line-height: 1.6;
		max-width: 46ch;
		animation: rise 0.6s cubic-bezier(0.2, 0.7, 0.2, 1) both;
		animation-delay: 0.16s;
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
		animation: rise 0.6s cubic-bezier(0.2, 0.7, 0.2, 1) both;
		animation-delay: 0.24s;
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
		font-weight: 600;
		box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-accent) 60%, transparent),
			0 8px 28px -10px color-mix(in srgb, var(--color-accent) 80%, transparent);
	}
	.btn.primary:hover {
		background: var(--color-accent-hover);
		transform: translateY(-1px);
		box-shadow: 0 0 0 1px var(--color-accent),
			0 12px 34px -10px color-mix(in srgb, var(--color-accent) 90%, transparent);
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
		margin-top: var(--space-7);
	}
	.card {
		position: relative;
		background: color-mix(in srgb, var(--color-surface) 72%, transparent);
		backdrop-filter: blur(8px) saturate(130%);
		-webkit-backdrop-filter: blur(8px) saturate(130%);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		padding: var(--space-5);
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		box-shadow: var(--shadow-sm);
		overflow: hidden;
		transition: transform 200ms cubic-bezier(0.2, 0.7, 0.2, 1),
			border-color 200ms ease, box-shadow 200ms ease;
		animation: rise 0.6s cubic-bezier(0.2, 0.7, 0.2, 1) both;
	}
	/* A hairline of accent light along the top edge of each card. */
	.card::before {
		content: '';
		position: absolute;
		inset: 0 0 auto 0;
		height: 1px;
		background: linear-gradient(
			90deg,
			transparent,
			color-mix(in srgb, var(--color-accent) 70%, transparent),
			color-mix(in srgb, var(--color-accent-2) 70%, transparent),
			transparent
		);
		opacity: 0.6;
		transition: opacity 200ms ease;
	}
	.card:nth-child(1) {
		animation-delay: 0.3s;
	}
	.card:nth-child(2) {
		animation-delay: 0.38s;
	}
	.card:nth-child(3) {
		animation-delay: 0.46s;
	}
	.card:hover {
		transform: translateY(-4px);
		border-color: var(--color-border-strong);
		box-shadow: var(--shadow-md),
			0 0 40px -16px color-mix(in srgb, var(--color-accent) 60%, transparent);
	}
	.card:hover::before {
		opacity: 1;
	}
	.card-icon {
		width: 2.25rem;
		height: 2.25rem;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		border-radius: var(--radius-md);
		background: var(--color-accent-subtle);
		border: 1px solid color-mix(in srgb, var(--color-accent) 22%, transparent);
		color: var(--color-accent);
		font-size: 1.05rem;
	}
	.card h3 {
		margin: 0;
		font-family: var(--font-display);
		font-size: 1.05rem;
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
