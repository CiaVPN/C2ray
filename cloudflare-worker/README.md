# C2ray Channel Sync Worker

This free Cloudflare Worker reads recent public posts from **@CiaConfig** and extracts supported proxy share links for C2ray.

## Deploy

From this directory:

```sh
npx wrangler login
npx wrangler deploy
```

Deploy it with the worker name `c2ray-channel-sync` in the `webserviceamir` Cloudflare account. C2ray uses `https://c2ray-channel-sync.webserviceamir.workers.dev/api/configs` internally; the address is not shown in the app or editable by users.

## Endpoints

- `GET /` and `GET /health`: health/status.
- `GET /api/configs`: JSON array of supported links extracted from recent public channel posts.

The Worker uses Cloudflare edge cache (120 seconds by default) and does not need a Telegram bot token, D1, or a paid server. The channel must be public. Telegram's preview page only exposes recent posts; this does not fetch the entire channel archive.

The Android client runs a background WorkManager job, deduplicates links, and imports new profiles. Android's periodic WorkManager interval has a 15-minute minimum; the app also provides a manual sync button.
