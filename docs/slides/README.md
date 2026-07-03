# Slides

Workshop slides built with [Marp](https://marp.app/) (Markdown → slides).

- `slides.md` — the deck
- `catppuccin-mocha.css` — custom Marp theme
- `.nvmrc` — Node version (22)

## Prerequisites

Node 22. With `nvm`:

```sh
nvm use        # reads .nvmrc
npm install    # installs @marp-team/marp-cli locally
```

## Run

Live preview with hot reload in the browser:

```sh
npm start      # marp --serve, open the printed http://localhost:8080
```

Watch + rebuild a single file:

```sh
npm run watch
```

## Export

```sh
npm run build  # slides.html
npm run pdf    # slides.pdf
npm run pptx   # slides.pptx
```

PDF/PPTX export needs a local Chrome/Chromium (Marp downloads one if missing).

## Editing

Slides are Markdown separated by `---`. Front-matter at the top of `slides.md`
sets the theme, pagination, and size. See the
[Marp Markdown syntax](https://marpit.marp.app/markdown).
