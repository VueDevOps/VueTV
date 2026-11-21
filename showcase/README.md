# Vue TV showcase

A buildless, responsive showcase of the real Vue TV Android launcher. Images and the navigation recording are captured from the app; no generated artwork is used.

Run `npm run dev` and open `http://127.0.0.1:4173`. `npm run check` checks the JavaScript syntax. The deployed static directory is `dist/`.

Features: clickable screenshot tour with keyboard/remote controls, source-screen hotspots, full-screen gallery, recorded native navigation video, avatar-editor comparison slider, expandable feature explanations, responsive layout and reduced-motion support. The website does not connect to the TV, service accounts or media APIs.

Capture provenance is in `capture-manifest.json`. Do not add account tokens, private API responses or server addresses to the published assets.

Video captures: two native 1920?1080 recordings, encoded with H.264 CRF 17 and fast-start metadata. Original high-bitrate masters are retained outside the website under `tools/`. Hero playback shows cinematic picks; the motion section shows row navigation.

## Public project

[Live showcase](https://vuedevops.github.io/VueTV/) ? [Source](https://github.com/VueDevOps/VueTV) ? [Download](https://github.com/VueDevOps/VueTV/releases/latest) ? [Setup](../docs/SETUP.md)

The GitHub Pages workflow publishes `dist/` with relative asset paths. Captures demonstrate earlier development versions; the page links to the current public preview. The hosted Twitch checklist is copied from the repository root's `twitch-setup.html`.
