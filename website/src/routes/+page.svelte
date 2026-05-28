<script lang="ts">
	import { base } from '$app/paths';
</script>

<section class="landing">
	<h1>NeoOrigins Datapack Editor</h1>
	<p>
		Build NeoOrigins-compatible datapacks in your browser. Edit, validate,
		download a ready-to-drop <code>.zip</code> — no install required.
	</p>

	<div class="cta">
		<a class="btn primary" href="{base}/editor/origin/">New Origin</a>
		<button class="btn disabled" type="button" disabled title="Coming in Phase 2">
			New Mob Origin (Phase 2)
		</button>
	</div>
</section>

<style>
	.landing {
		text-align: center;
		padding: 2rem 0;
	}
	h1 {
		margin-bottom: 0.5rem;
	}
	.cta {
		margin-top: 2rem;
		display: flex;
		gap: 1rem;
		justify-content: center;
		flex-wrap: wrap;
	}
	.btn {
		display: inline-block;
		padding: 0.75rem 1.5rem;
		font-size: 1rem;
		border-radius: 6px;
		border: 1px solid transparent;
		cursor: pointer;
		text-decoration: none;
	}
	.btn.primary {
		background: #4a90e2;
		color: #fff;
	}
	.btn.disabled {
		background: #2a2a2a;
		color: #777;
		cursor: not-allowed;
	}
</style>
