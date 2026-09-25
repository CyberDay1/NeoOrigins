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

Published at `cyberday1.github.io/NeoOrigins/editor/` by the combined
Pages workflow on `master` (`.github/workflows/pages.yml`), which checks
out this branch (`1.21.1`) and builds the editor alongside the Jekyll
docs site. `.github/workflows/editor-pages.yml` on this branch only
builds the editor as a check and, when the `PAGES_DISPATCH_TOKEN` secret
is set, sends `master` a `repository_dispatch` to redeploy. Without the
secret, run the Pages workflow on `master` by hand.

### Schemas

The source of truth is `docs/schema/*.json`, generated from the parsers
by the Gradle schema tasks. `static/schemas/` holds committed copies:
**do not hand-edit them**. After changing `docs/schema/`, copy the files
across and commit both (`cp ../docs/schema/*.json static/schemas/`).
`./gradlew schemaDriftVerify` and the drift step in `editor-pages.yml`
both fail when the copies differ.

The deployed editor serves this branch's schema to authors on every
Minecraft line, so it can offer fields a 26.x build does not read.
