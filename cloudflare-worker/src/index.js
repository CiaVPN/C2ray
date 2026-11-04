const CHANNEL = "CiaConfig";
const DEFAULT_CACHE_SECONDS = 10;
const SCHEMES = new Set(["vless", "vmess", "ss", "trojan", "socks", "socks5", "hysteria2", "hy2", "wireguard"]);

function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Cache-Control",
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

async function fetchTelegramPage() {
  const channelUrl = `https://t.me/s/${CHANNEL}`;
  let lastError = null;

  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      const upstream = await fetch(channelUrl, {
        method: "GET",
        redirect: "follow",
        headers: {
          "Accept": "text/html,application/xhtml+xml",
          "Accept-Language": "en-US,en;q=0.9",
          "Cache-Control": "no-cache",
          "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36"
        },
        cf: { cacheTtl: 0, cacheEverything: false }
      });

      if (upstream.ok) return await upstream.text();

      const detail = (await upstream.text()).replace(/\s+/g, " ").slice(0, 240);
      lastError = new Error(`Telegram returned HTTP ${upstream.status}${detail ? `: ${detail}` : ""}`);

      if (upstream.status < 500 && upstream.status !== 429) {
        throw lastError;
      }
    } catch (error) {
      lastError = error instanceof Error ? error : new Error("Unknown Telegram fetch error");
      if (attempt === 3) break;
    }

    if (attempt < 3) {
      await new Promise(resolve => setTimeout(resolve, attempt * 350));
    }
  }

  throw lastError || new Error("Telegram channel connection failed after retries");
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
        cacheSeconds: DEFAULT_CACHE_SECONDS,
        note: "Reads the latest available posts from the public Telegram channel"
      });
    }
    if (url.pathname !== "/api/configs") {
      return json({ error: "Not found" }, 404);
    }

    const cacheKey = new Request(new URL("/api/configs", url.origin).toString(), { method: "GET" });
    const cache = caches.default;
    const cached = await cache.match(cacheKey);
    if (cached) return cached;

    try {
      const html = await fetchTelegramPage();
      const configs = collectConfigs(html);
      const configuredTtl = Number(env.CACHE_SECONDS ?? DEFAULT_CACHE_SECONDS);
      const cacheSeconds = Number.isFinite(configuredTtl)
        ? Math.max(5, Math.min(30, Math.trunc(configuredTtl)))
        : DEFAULT_CACHE_SECONDS;

      const response = json({
        ok: true,
        channel: CHANNEL,
        fetchedAt: new Date().toISOString(),
        count: configs.length,
        configs
      }, 200, {
        // Keep the API fresh; cache the parsed channel result for only a few seconds.
        "Cache-Control": `public, max-age=0, s-maxage=${cacheSeconds}`
      });

      ctx.waitUntil(cache.put(cacheKey, response.clone()));
      return response;
    } catch (error) {
      return json({
        ok: false,
        error: "Could not read Telegram channel",
        message: error instanceof Error ? error.message : "Unknown Telegram fetch error"
      }, 502);
    }
  }
};
