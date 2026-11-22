# Changelog

## 0.20.8 - 2026-11-22

- Add Watch in Emby to Requests title details when the movie or show is found in the current profile's connected Emby library.
- Match exact TMDB IDs and Movie/Series types, then open the matched Emby item using the existing Android TV/universal client links.
- Check Emby in the background without blocking details, trailers or requests. Hide the action when no match is found or Emby cannot be reached; discard results after connection changes or closing details.
- Keep Choose seasons available alongside the Emby action for partly available shows.

Validated on Google TV Streamer / Android 14: Iron Man details displayed Watch in Emby and the action opened the correct movie in Emby; a title absent from the library retained Request movie without an Emby action. Build/lint for debug and release, all six JVM checks, hosted-player regressions and showcase checks passed. TV-series and universal Emby client links were not exercised on-device in this update.

## 0.20.7 - 2026-11-22

- Hide the retained Twitch WebView outside Live so its paused video frame cannot show through Requests or Apps backgrounds. Keep the paused player available for reuse.
- Handle keyboard Done, Next, Search and Enter explicitly in search, connection and profile dialogs. Next moves to the next field; Done hides the keyboard and focuses the dialog action; Search hides the keyboard and submits the search.
- Hide the keyboard when text-entry dialogs dismiss, and when a connection submission starts.

Validated on Google TV Streamer / Android 14: Requests search submitted from Gboard's Search key with `mInputShown=false` afterward; profile-name Done hid Gboard and focused Save without saving the draft. Switching from a playing Twitch preview into Requests paused the player and showed a neutral background. Build/lint, all six JVM checks and the hosted-player JavaScript regressions passed.

## 0.20.6 - 2026-11-22

- Prepare a paused, muted Twitch player on Home after followed streams load. Entering Live reuses its initialized page instead of starting the browser and player from scratch.
- Keep that single WebView attached across tab changes and pause the Twitch player outside Live. Pause the WebView when the activity goes into the background; destroy it when previews are disabled or the activity ends.
- Retry a dropped play command briefly after resume, only for the selected idle player. Keep buffering streams and paused Home preparation out of the retry path.
- Shorten the player selection debounce from 180 to 80 ms.

Google TV Streamer / Android 14 validation with the updated hosted page supplied locally through WebView debugging: entering Live after Home warm-up took 3.1 seconds; subsequent returns took 2.0–2.7 seconds; different-channel switches took 3.6–3.9 seconds. Confirmed the same player document survived tab changes, and video was paused on Home and while Settings was foregrounded. These samples are not an instant-start guarantee: opening Live before preparation completes still needs cold initialization, and stream buffering depends on Twitch and the network.

## 0.20.5 - 2026-11-22

- Populate Live's three hero picks with the first three followed channels that are live, clearing Home's movie picks when changing tabs.
- Keep the muted player visible to Chromium behind the artwork while a channel loads, then reveal it only when the selected channel is playing.
- Switch channels through Twitch's `setChannel` without racing it with pause/play commands. Ignore unavailable events for a different channel during a switch.
- Show only the available hero dots and label Twitch picks "Live from your follows".

Validated on Google TV Streamer / Android 14 with the updated hosted player supplied locally through WebView debugging: repeated hero switches and a stream-row selection reached muted playback for summit1g, Ludwig and koil in 3.1–3.9 seconds after player selection. Initial startup still depends on Twitch and network buffering; this is not an instant-start guarantee. Build/lint, all six JVM checks, and JavaScript regressions for asynchronous and rapid channel changes passed.

## 0.20.4 - 2026-11-22

- Add an official muted Twitch player behind the Live shading and launcher UI after stream focus settles.
- Reuse the loaded player for channel changes and shorten the selection delay; cold startup and stream buffering still depend on Twitch.
- Keep the full thumbnail until playback starts; fall back to artwork if the player is blocked, offline, unavailable or fails to start.
- Stop previews on channel changes, leaving Live, activity pause and destruction. Selecting a card still opens S0undTV.
- Add a Live previews toggle in VueSettings. The hosted player receives public channel logins, without Vue TV service credentials.

Device validation on Google TV Streamer / Android 14 confirmed muted live video and reuse of the same Twitch iframe/document across a channel switch. That single warm switch measured 2,965 ms from remote input to muted playback. Cold startup can take longer. Player state, channel validation, reuse during startup, mute enforcement, pause-on-hide and fallback states are covered by the JavaScript check. Build/lint and all six JVM checks passed.

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
