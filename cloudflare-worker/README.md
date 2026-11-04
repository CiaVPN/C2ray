# C2ray Channel Sync Worker

A small Cloudflare Worker backend for C2ray. It reads the public Telegram preview page for **@CiaConfig** and extracts supported share links. It does not need a Telegram bot token, a paid server, D1, or a paid Cloudflare feature.

## Endpoints

- `GET /` or `GET /health` — health check and service information.
- `GET /api/configs` — JSON response containing VLESS, VMess, Shadowsocks, Trojan, SOCKS, Hysteria2, and WireGuard links found in recent public channel posts.
- `OPTIONS` — CORS preflight.

The response is cached at Cloudflare's edge for approximately 120 seconds by default. Change `CACHE_SECONDS` in `wrangler.toml` to adjust backend caching. The Android client should still have its own enable/disable switch and polling interval.

## Deploy

1. Install Node.js and Wrangler.
2. From this directory run:

   ```sh
   npx wrangler login
   npx wrangler deploy
   ```

3. Copy the Worker URL, for example `https://c2ray-channel-sync.<account>.workers.dev`.
4. Set that URL as the channel-sync endpoint in C2ray.

## Limitations and privacy

- The channel must be public. Private channels require a different authenticated integration.
- Telegram's public preview page exposes only its currently available recent posts; this is not a complete historical channel archive.
- The Worker only extracts share links. It does not test, guarantee, or endorse any proxy server.
- Requests to `/api/configs` make a request to Telegram when the edge cache is cold.
- No bot token or user data is stored by this Worker.

This Worker is the backend foundation. The Android app integration (drawer settings, local interval preferences, WorkManager scheduling, import/deduplication, app icon and upstream-brand cleanup) must be completed and built in the Android source module.
