# Set up Vue TV

## Before you start

- An Android TV or Google TV device with a remote. Tested on Google TV Streamer / Android 14; other firmware may behave differently. The build minimum is Android 8.0 (API 26).
- The APK from [the latest release](https://github.com/VueDevOps/VueTV/releases/latest).
- Optional: an Emby server/account, a Jellyseerr server configured for Emby authentication, or a Twitch account and public developer application. Each integration is optional. Install your playback apps separately on the TV.
- Your own HTTPS server addresses with trusted TLS certificates. `https://emby.example.com` and `https://jellyseerr.example.com` are placeholders. Plain HTTP and self-signed certificates are not supported by this build.

## Install

Transfer the APK to the TV and open it with a trusted file manager. If prompted, allow **Install unknown apps** for that installer, install Vue TV, then turn that permission off again. Launch **Vue TV Preview** from Apps. Installing alone does not replace the system launcher.

Alternatively, install Android SDK Platform Tools on your computer and use ADB:

1. On the TV, open **Settings / System / About** and select **Android TV OS build** seven times to enable Developer options. Labels vary by firmware.
2. Enable the debugging option your TV offers. Wireless debugging uses a pairing address/code and a separate connection address/port. Older network ADB may use port 5555. Keep the computer and TV on the same trusted network and authorize the computer on the TV.
3. Substitute the addresses displayed by your device below:

```sh
# Only when the TV presents a wireless pairing screen:
adb pair TV_IP:PAIRING_PORT
# The connection port may differ from the pairing port:
adb connect TV_IP:ADB_PORT
adb -s TV_IP:ADB_PORT install -r VueTV-0.20.5.apk
adb -s TV_IP:ADB_PORT shell am start -n je.vu.tvhome/.MainActivity
```

Check the connection with `adb devices`. Disable debugging when finished.

## Emby

1. Open **avatar / VueSettings / Connect Emby**.
2. Replace the example URL with your server root, such as `https://emby.example.com`. Do not enter a movie page or `/web` address. Include a reverse-proxy base path if needed; Vue TV appends `/emby/…` API paths.
3. Enter your Emby username and password and choose **Sign in**.
4. Home loads Continue watching, Recently added and library picks. Install and sign into the official Emby Android TV app separately to play titles. Cards open title details in that client; the universal Android client is a fallback.

The password is used for sign-in and is not saved. The token remains in private app storage. **Disconnect Emby** removes the local token. Use Connect Emby again to sign into another server.

## Twitch

1. Register an application in the [Twitch developer console](https://dev.twitch.tv/console/apps). Twitch may require verified email and two-factor authentication.
2. Choose the **Public** client type. If the form requires a redirect URL, add `http://localhost:3000`; device-code sign-in does not use it or require a local server. Keep the Client ID. Do not create or enter a client secret.
3. Open **avatar / VueSettings / Connect Twitch** on the TV, enter the Client ID and request a sign-in code.
4. Open [Twitch activation](https://www.twitch.tv/activate?public=true) on your phone, sign into the desired account and enter the code. Keep the TV dialog open while authorization completes.
5. Open **Live** to see followed channels that are live. Focused stream cards show their full thumbnail, then start a muted Live preview when playback is available. Leave focus on a card briefly to start its preview. Artwork remains if playback is blocked, unavailable or offline. Toggle **avatar / VueSettings / Live previews** to use artwork only. Previews stop when leaving Live or opening another app. Selecting a channel still opens that stream in **S0undTV**, which must be installed separately.

Vue TV requests `user:read:follows`. See [Twitch device-flow refresh rules](https://dev.twitch.tv/docs/authentication/getting-tokens-oauth/#device-code-grant-flow). Vue TV automatically renews expired access tokens and saves each replacement refresh token. **After upgrading from 0.20.1 or earlier, reconnect Twitch once** because those versions did not save a refresh token. Normal use can keep the session going beyond 30 days, but Twitch can require sign-in again after 30 days without a refresh or when authorization is revoked. This is not a guaranteed permanent or fixed 30-day sign-in. **Disconnect Twitch** clears both local tokens. Revoke access at the provider through [Twitch Connections](https://www.twitch.tv/settings/connections) if needed.

The showcase includes an [interactive Twitch checklist](https://vuedevops.github.io/VueTV/twitch-setup.html).

## Requests

1. Jellyseerr must already have Emby authentication, media services, permissions and request defaults configured. Vue TV does not install or configure these services.
2. Open **Requests / Sign in**.
3. Enter your HTTPS Jellyseerr root URL, for example `https://jellyseerr.example.com`, and your Emby username/password. Vue TV appends `/api/v1` itself.
4. Browse or search, then select a title for details, available ratings and a trailer. Movies require confirmation; shows offer season selection followed by confirmation. Existing availability, permissions, quotas and approval rules apply.

The preview supports Jellyseerr's Emby login flow and standard-quality requests. Local Jellyseerr login, other authentication providers and every Seerr/Jellyseerr version have not been validated. To change servers, choose **Requests / Sign out**, then sign in with the new address. Expired sessions require signing in again.

## Profiles and controls

- The avatar opens **Who's watching?**. Choose **Create user**, then connect that person's services. Server URLs and Twitch Client ID may be copied as setup hints; account tokens are not copied.
- Hold a profile or choose **Edit profile** to rename it, customize the avatar or delete it. At least one profile is retained. Deleting a profile removes local settings/cache, not server accounts/media.
- Use the directional pad to browse, OK to select, and Back to return. Hold OK on an app icon and choose **Move**; Left/Right moves, OK saves, Back cancels.
- The gear opens native Google TV quick settings where available. **VueSettings / Cache settings** changes artwork retention or clears cached content. **Refresh content** bypasses feed freshness windows.

## Optional Home-button setup

First verify you can launch Vue TV from Apps and reach Android Settings. Keep the original Google TV launcher enabled.

If firmware offers a default Home-app picker, select Vue TV. An ADB alternative is:

```sh
adb -s TV_IP:ADB_PORT shell cmd package set-home-activity --user 0 je.vu.tvhome/.MainActivity
```

Some Google TV firmware still prioritizes its own launcher. On the tested Streamer, enable **Vue TV home button** under **Settings / System / Accessibility**. This service observes original-launcher window events and filters Home keys; it cannot retrieve window contents. Press Home for Vue TV and hold Home for quick settings. Behavior varies by device and firmware.

## Restore Google TV

Disable **Vue TV home button** in Accessibility settings, then select the original launcher as the default Home app if firmware offers that option. **VueSettings / Open original Google TV** opens it temporarily; disable the service first to avoid redirection.

For Google TV with the standard Google launcher, the included PowerShell helper removes only Vue TV's accessibility-service entry and restores Google's Home activity:

```powershell
./restore-google-home.ps1 -Device 'TV_IP:ADB_PORT'
```

The helper targets `com.google.android.apps.tv.launcherx/.home.HomeActivity`; other vendors need their own launcher component. Restore Home and disable the service before uninstalling Vue TV. No system apps need to be removed or disabled.

## Upgrade or uninstall

Install a newer official APK with the same installer or `adb install -r`. Same-key upgrades preserve app data. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the installed copy uses a different signing key, for example an older development build. Uninstalling fixes that conflict but deletes local profiles and sign-ins; reconnect afterward. A same-key update does not require uninstalling.

To remove Vue TV, restore the original Home app first, then uninstall through Android Settings. Server accounts, media and submitted requests remain on their servers.

## Troubleshooting

- **Server connection fails:** check DNS/network access, HTTPS and a trusted certificate. Do not include `/api/v1` in the Jellyseerr URL. VPNs, proxy rules or additional proxy login screens can block API calls.
- **Emby login fails:** check credentials and that the proxy exposes `/emby` API endpoints. If browsing works but playback fails, check the playback client is installed and signed in.
- **Requests returns 401/403:** reconnect for 401; check server permissions and quotas for 403. The server must support Emby login.
- **Live is empty:** no followed channels may be live. Allow automatic retries after a temporary network outage. Reconnect once after upgrading an older sign-in, after prolonged inactivity, or if Twitch has revoked authorization. Check the application's Client ID matches the authorization.
- **Home still opens Google TV:** check the Home-app setting and accessibility service. Some firmware does not support reliable overrides.
- **Settings/playback behaves differently:** these depend on installed apps and firmware APIs. Confirm the relevant app is installed and current.

Report a [GitHub issue](https://github.com/VueDevOps/VueTV/issues) with device model, Android/app versions and reproduction steps. Never include passwords, access tokens or cookies.
