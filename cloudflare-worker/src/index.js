const CHANNEL = "CiaConfig";
const DEFAULT_CACHE_SECONDS = 120;
const SCHEMES = new Set(["vless", "vmess", "ss", "trojan", "socks", "socks5", "hysteria2", "hy2", "wireguard"]);

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Cache-Control": "no-store"
  };
}

function decodeHtml(value) {
  return value
    .replace(/&amp;/gi, "&")
    .replace(/&quot;/gi, '"')
    .replace(/&#39;|&apos;/gi, "'")
    .replace(/&lt;/gi, "<")
    .replace(/&gt;/gi, ">")
    .replace(/&#(\d+);/g, (_, n) => String.fromCodePoint(Number(n)))
    .replace(/&#x([\da-f]+);/gi, (_, n) => String.fromCodePoint(parseInt(n, 16)));
}

function cleanUri(value) {
  return decodeHtml(value)
    .replace(/[),.;\]}]+$/g, "")
    .trim();
}

function collectConfigs(html) {
  const found = new Map();
  const expression = /(?:vless|vmess|ss|trojan|socks5?|hysteria2|hy2|wireguard):\/\/[^\s"'<>]+/gi;

  for (const match of html.matchAll(expression)) {
    const uri = cleanUri(match[0]);
    const scheme = uri.slice(0, uri.indexOf(":")).toLowerCase();
    if (!SCHEMES.has(scheme) || found.has(uri)) continue;

    let title = scheme.toUpperCase();
    const hashIndex = uri.indexOf("#");
    if (hashIndex >= 0 && hashIndex < uri.length - 1) {
      try { title = decodeURIComponent(uri.slice(hashIndex + 1)); }
      catch { title = uri.slice(hashIndex + 1); }
    }

    found.set(uri, {
      id: crypto.randomUUID(),
      protocol: scheme,
      title: title.slice(0, 120),
      uri
    });
  }
  return [...found.values()];
}

function json(data, status = 200, extra = {}) {
  return new Response(JSON.stringify(data, null, 2), {
    status,
    headers: {
      ...corsHeaders(),
      "Content-Type": "application/json; charset=utf-8",
      ...extra
    }
  });
}

export default {
  async fetch(request, env, ctx) {
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }
    if (request.method !== "GET") {
      return json({ error: "Method not allowed" }, 405);
    }

    const url = new URL(request.url);
    if (url.pathname === "/" || url.pathname === "/health") {
      return json({
        service: "C2ray Channel Sync",
        status: "ok",
        channel: CHANNEL,
        endpoints: ["/api/configs", "/health"],
        note: "Public Telegram channels only"
      });
    }
    if (url.pathname !== "/api/configs") {
      return json({ error: "Not found" }, 404);
    }

    const cacheKey = new Request(new URL("/api/configs", url.origin).toString(), request);
    const cache = caches.default;
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    const channelUrl = `https://t.me/s/${CHANNEL}`;
    try {
      const upstream = await fetch(channelUrl, {
        headers: {
          "Accept": "text/html,application/xhtml+xml",
          "User-Agent": "Mozilla/5.0 (compatible; C2rayChannelSync/2.0)"
        },
        cf: { cacheTtl: 0, cacheEverything: false }
      });
      if (!upstream.ok) {
        return json({
          error: "Telegram channel page is unavailable",
          upstreamStatus: upstream.status
        }, 502);
      }

      const html = await upstream.text();
      const configs = collectConfigs(html);
      const response = json({
        ok: true,
        channel: CHANNEL,
        fetchedAt: new Date().toISOString(),
        count: configs.length,
        configs
      }, 200, {
        "Cache-Control": `public, max-age=${Math.max(30, Number(env.CACHE_SECONDS || DEFAULT_CACHE_SECONDS))}`
      });

      ctx.waitUntil(cache.put(cacheKey, response.clone()));
      return response;
    } catch (error) {
      return json({
        error: "Could not read Telegram channel",
        message: error instanceof Error ? error.message : "Unknown error"
      }, 502);
    }
  }
};
