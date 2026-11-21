# Changelog

## 0.20.3 - 2026-11-22

- Show the focused stream thumbnail as full-screen artwork on Live, instead of an artwork-derived color wash.
- Keep shading for readable text and restore the focused stream artwork after returning from playback.
- Include the automatic Twitch sign-in renewal introduced in 0.20.2.

Checked on Google TV Streamer / Android 14: full artwork followed focus between Ludwig and Xaryu, the selected channel opened in S0undTV, and its artwork/focus returned when switching back to Vue TV. Build and lint passed.


## 0.20.2 - 2026-11-22

- Keep Twitch sign-in active by renewing expired access tokens automatically with public-client device-flow refresh tokens.
- Save both tokens in profile-private preferences and persist every rotated replacement. Serialize renewals across activity recreations to avoid reusing a single-use refresh token.
- Validate on startup and at least hourly; retry authorization failures once. Temporary network failures keep the saved sign-in for automatic retry.
- Clear both tokens on disconnect/client changes and prevent in-flight refreshes from restoring a deleted or disconnected profile.
- Older sign-ins require one reconnect to obtain the refresh token discarded by previous versions. Twitch may still require authorization after 30 days without a refresh or after revocation.

Validation: mocked renewal tests cover 35 days of regular use, restart, expiry/retry, revocation, offline recovery, scope/client mismatch, token rotation and concurrent/disconnect/deletion races. On Google TV Streamer / Android 14, completed device-code authorization, replaced only the development access token with a test-invalid value, verified automatic renewal against Twitch and rotation of both tokens, then verified persistence across a full app restart. A real 30-day session has not been time-tested.

## 0.20.1 - 2026-11-21

- Open selected live channels in S0undTV instead of the official Twitch TV app.
- Retain Twitch broadcaster IDs and use channel links supported by the installed S0undTV beta on cold and repeat launches.
- Show an installation/update message if S0undTV cannot handle the channel.
- Update the website and setup guide to describe S0undTV playback.

Checked on Google TV Streamer / Android 14: a channel link started live LIRIK playback in S0undTV. The launcher still uses Twitch sign-in to retrieve followed live channels; S0undTV is the playback app.

## 0.20.0 — 2026-11-21

First public preview release.

- Publish launcher source, setup guide and a signed installable APK.
- Replace personal defaults with `https://emby.example.com` and `https://jellyseerr.example.com`.
- Add a per-profile Jellyseerr URL field. Changing servers clears the previous local session and Requests metadata caches.
- Include the showcase and recordings in the repo, with download/setup links and GitHub Pages deployment.
- Add the Gradle wrapper, build/lint workflow, JVM checks and restore instructions.

Includes the existing 0.19 features: cinematic picks, Emby rows/title links, Twitch follows, Jellyseerr requests, profiles/avatars, app ordering, caching, remote navigation and optional Home-button handling.

Preview limits: tested on Google TV Streamer / Android 14. HTTPS required. Twitch tokens need reconnection after expiry. Requests uses Jellyseerr Emby login and standard-quality requests. YouTube/Netflix are shortcuts without private feed integration.
