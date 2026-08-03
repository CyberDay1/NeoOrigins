# sv

Everything you need to build a Svelte project, powered by [`sv`](https://github.com/sveltejs/cli).

## Creating a project

If you're seeing this, you've probably already done this step. Congrats!

```sh
# create a new project
npx sv create my-app
```

To recreate this project with the same configuration:

```sh
# recreate this project
npx sv@0.15.3 create --template minimal --types ts --add sveltekit-adapter="adapter:static" --no-download-check --install npm website
```

## Developing

Once you've created a project and installed dependencies with `npm install` (or `pnpm install` or `yarn`), start a development server:

```sh
npm run dev

# or start the server and open the app in a new browser tab
npm run dev -- --open
```

## Building

To create a production version of your app:

```sh
npm run build
```

You can preview the production build with `npm run preview`.

> To deploy your app, you may need to install an [adapter](https://svelte.dev/docs/kit/adapters) for your target environment.

## NeoOrigins Web Editor

Schema-driven, browser-based datapack generator for NeoOrigins: the same
schemas that drive the in-game creator, ported to a static SvelteKit app.

### Local dev

```sh
npm install
npm run dev
```

### Local build

```sh
npm run build
```

Output goes to `build/` (SvelteKit `adapter-static`).

### Deploy

Deployed to `cyberday1.github.io/NeoOrigins/editor/` by
`.github/workflows/editor-pages.yml` on every push to `1.21.1` that
touches `website/**`, `docs/schema/**`, or the workflow itself.

### Schemas

The source of truth lives at `docs/schema/*.json` (alongside the
mkdocs docs). CI copies those files into `static/schemas/` on every
build. **Do not hand-edit `static/schemas/`**; edit `docs/schema/`
and let the workflow re-sync.
