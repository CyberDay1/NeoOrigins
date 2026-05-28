import adapter from '@sveltejs/adapter-static';

const dev = process.env.NODE_ENV !== 'production';

/** @type {import('@sveltejs/kit').Config} */
const config = {
	compilerOptions: {
		// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
		runes: ({ filename }) => (filename.split(/[/\\]/).includes('node_modules') ? undefined : true)
	},
	kit: {
		adapter: adapter({
			// Full static export — no SSR; suitable for GitHub Pages.
			fallback: undefined,
			strict: true
		}),
		paths: {
			// Deployed under cyberday1.github.io/NeoOrigins/editor/
			// Local dev keeps the empty base so http://localhost:5173/ still works.
			base: dev ? '' : '/NeoOrigins/editor'
		}
	}
};

export default config;
