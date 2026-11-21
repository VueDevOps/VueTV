# Changelog

## 0.20.0 — 2026-11-21

First public preview release.

- Publish launcher source, setup guide and a signed installable APK.
- Replace personal defaults with `https://emby.example.com` and `https://jellyseerr.example.com`.
- Add a per-profile Jellyseerr URL field. Changing servers clears the previous local session and Requests metadata caches.
- Include the showcase and recordings in the repo, with download/setup links and GitHub Pages deployment.
- Add the Gradle wrapper, build/lint workflow, JVM checks and restore instructions.

Includes the existing 0.19 features: cinematic picks, Emby rows/title links, Twitch follows, Jellyseerr requests, profiles/avatars, app ordering, caching, remote navigation and optional Home-button handling.

Preview limits: tested on Google TV Streamer / Android 14. HTTPS required. Twitch tokens need reconnection after expiry. Requests uses Jellyseerr Emby login and standard-quality requests. YouTube/Netflix are shortcuts without private feed integration.
