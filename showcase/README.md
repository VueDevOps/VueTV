# Vue TV showcase

A buildless, responsive showcase of the real Vue TV Android launcher. Images and the navigation recording are captured from the app; no generated artwork is used.

Run `npm run dev` and open `http://127.0.0.1:4173`. `npm run check` checks the JavaScript syntax. The deployed static directory is `dist/`.

Features: a navigable HTML recreation of Vue TV 0.20.9 with keyboard and on-page remote controls, the current library snapshot exported from the APK, editable avatars ported from the app's vector drawing code, local app rearranging, Requests search/details/season confirmation, screenshot comparisons and six inline TV recordings. Full-screen demo mode is optional. The published website does not connect to the TV, service accounts or media APIs; launches and requests are demonstrations.

Capture provenance is in `capture-manifest.json`. Do not add account tokens, private API responses or server addresses to the published assets.

Media refreshed on 22 November 2026 from the installed APK on Google TV Streamer / Android 14: Home, library focus, Apps, Live, Requests, profiles, avatar editing and settings. The six recordings use native 1920×1080 captures, H.264 CRF 19 and fast-start metadata. Masters remain outside the website under `tools/`. Videos play in their own page sections and pause other page videos when started.

The demo's 960×540 layout follows the app's density-independent measurements and scales to its frame. Native Android text rendering, system dialogs and streaming-app handoffs are approximated in the browser. Live channels and media availability are a dated snapshot, not a live service feed. The local JSON uses an allowlist of display fields and local artwork paths; it contains no service addresses, account IDs, cookies or tokens.

Validation: `npm ci` then `npm run check` in this directory. GitHub Pages runs the demo interaction, asset and inline-video regression checks before publishing.

## Public project

[Live showcase](https://vuedevops.github.io/VueTV/) ? [Source](https://github.com/VueDevOps/VueTV) ? [Download](https://github.com/VueDevOps/VueTV/releases/latest) ? [Setup](../docs/SETUP.md)

The GitHub Pages workflow publishes `dist/` with relative asset paths. Captures demonstrate earlier development versions; the page links to the current public preview. The hosted Twitch checklist is copied from the repository root's `twitch-setup.html`.
