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

The Worker uses Cloudflare Cache API (10 seconds by default) and does not need a Telegram bot token, D1, or a paid server. The channel must be public. Telegram's preview page only exposes recent posts; this does not fetch the entire channel archive.

The Android client deduplicates links and imports new profiles. Choose 1, 5, or 10 minutes for foreground polling while C2ray is open; Android WorkManager still enforces a 15-minute minimum for periodic background checks. The app also provides a manual sync button. The Worker retries transient Telegram fetch failures before returning an error.
