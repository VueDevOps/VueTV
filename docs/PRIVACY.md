# Privacy and local storage

Vue TV connects directly to the Emby, Jellyseerr and Twitch services you configure. Artwork may come from TMDB or service-provided image URLs. Playback/trailers hand the selected title or channel to the installed app. These services have their own policies.

There is no Vue TV analytics SDK, advertising feed or hosted Vue TV account service.

- Emby and Jellyseerr passwords are used for authentication and are not saved.
- Access tokens, Twitch refresh tokens, Jellyseerr sessions, server URLs and account identifiers remain in the selected profile's Android private preferences. This is Android app storage, not a separately encrypted credential vault.
- Artwork/feed snapshots are cached in private app cache directories, scoped by profile/account. Android may reclaim caches.
- Profile names, avatar choices, app order and cache preferences remain on the device. Android backup and device transfer are disabled.
- The optional Home-button service filters Home keys and observes original-launcher window events. It does not retrieve window contents.

Disconnect services or clear caches through VueSettings. Deleting a profile removes its local settings/cache; uninstalling removes app data. Local disconnection does not revoke server sessions or undo requests. Revoke access at the provider if needed.

The showcase uses fixed captures and does not connect to the visitor's TV or media accounts. The Twitch checklist stores progress in the browser and does not send entered Client IDs to the TV or Twitch.
