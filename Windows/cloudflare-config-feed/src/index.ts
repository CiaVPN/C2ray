export interface Env {
  CONFIGS: R2Bucket;
}

type ConfigRecord = {
  id: string;
  protocol: string;
  title: string;
  config: string;
};

const CHANNEL = "CiaConfig";
const CHANNEL_PAGE = "https://t.me/s/CiaConfig";
const MAX_CONFIGS = 500;

const CONFIG_RE =
  /(?:^|[^A-Za-z0-9])((?:vless|vmess|trojan|ssr|ss|hysteria2|hysteria|hy2|tuic|wireguard):\/\/[^\s<>"']+)/gim;

function decodeHtml(value: string): string {
  const named: Record<string, string> = {
    amp: "&",
    quot: '"',
    apos: "'",
    "#39": "'",
    lt: "<",
    gt: ">",
    nbsp: " ",
  };

  return value.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (whole, key: string) => {
    const normalized = key.toLowerCase();
    if (normalized.startsWith("#x")) {
      const cp = Number.parseInt(normalized.slice(2), 16);
      return Number.isFinite(cp) ? String.fromCodePoint(cp) : whole;
    }
    if (normalized.startsWith("#")) {
      const cp = Number.parseInt(normalized.slice(1), 10);
      return Number.isFinite(cp) ? String.fromCodePoint(cp) : whole;
    }
    return named[normalized] ?? whole;
  });
}

function plainText(markup: string): string {
  return decodeHtml(
    markup
      .replace(/<br\s*\/?>/gi, "\n")
      .replace(/<\/(?:p|div|blockquote)>/gi, "\n")
      .replace(/<[^>]+>/g, " "),
  )
    .replace(/\u00a0/g, " ")
    .replace(/[ \t]+\n/g, "\n")
    .replace(/\n{3,}/g, "\n\n");
}

function decodeTitle(config: string, messageText: string): string {
  const fragment = config.split("#", 2)[1];
  if (fragment) {
    try {
      const title = decodeURIComponent(fragment).trim();
      if (title) return title.slice(0, 160);
    } catch {
      return fragment.slice(0, 160);
    }
  }

  const beforeLink = messageText.slice(0, Math.max(0, messageText.indexOf(config))).trim();
  return (beforeLink || "CiaConfig").slice(-160);
}

async function stableId(value: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value),
  );
  return Array.from(new Uint8Array(digest), (b) =>
    b.toString(16).padStart(2, "0"),
  ).join("");
}

async function fetchChannelConfigs(): Promise<ConfigRecord[]> {
  const response = await fetch(CHANNEL_PAGE, {
    headers: {
      "user-agent": "Mozilla/5.0 (compatible; C2rayConfigFeed/1.0)",
      accept: "text/html,application/xhtml+xml",
      "accept-language": "fa,en;q=0.8",
    },
    redirect: "follow",
  });

  if (!response.ok) {
    throw new Error(\`Telegram channel returned HTTP \${response.status}\`);
  }

  const html = await response.text();
  if (!html.includes("tgme_widget_message")) {
    throw new Error("Telegram page layout was not recognized; existing R2 snapshot was preserved.");
  }

  const messageBlocks = [
    ...html.matchAll(
      /<div[^>]*class=["'][^"']*tgme_widget_message_text[^"']*["'][^>]*>([\s\S]*?)<\/div>/gi,
    ),
  ];

  if (messageBlocks.length === 0) {
    throw new Error("No public channel messages were found; existing R2 snapshot was preserved.");
  }

  const byId = new Map<string, ConfigRecord>();
  for (const match of messageBlocks) {
    const text = plainText(match[1]);
    CONFIG_RE.lastIndex = 0;

    for (const found of text.matchAll(CONFIG_RE)) {
      const config = found[1].trim().replace(/[.,;،)\]}]+$/g, "");
      const protocol = config.slice(0, config.indexOf("://")).toLowerCase();
      if (!config.includes("@") && !["vmess", "wireguard"].includes(protocol)) continue;

      const id = await stableId(config);
      if (!byId.has(id)) {
        byId.set(id, {
          id,
          protocol,
          title: decodeTitle(config, text),
          config,
        });
      }
    }
  }

  if (byId.size === 0) {
    throw new Error("No supported V2Ray/Xray links were found; previous R2 snapshot was preserved.");
  }

  return [...byId.values()].slice(0, MAX_CONFIGS);
}

async function refreshSnapshot(env: Env): Promise<void> {
  const configs = await fetchChannelConfigs();
  const fetchedAt = new Date().toISOString();
  const payload = {
    ok: true,
    channel: CHANNEL,
    fetchedAt,
    count: configs.length,
    configs,
  };

  await env.CONFIGS.put("configs.json", JSON.stringify(payload), {
    httpMetadata: {
      contentType: "application/json; charset=utf-8",
      cacheControl: "public, max-age=300, s-maxage=900",
    },
    customMetadata: {
      channel: CHANNEL,
      fetchedAt,
      count: String(configs.length),
    },
  });
}

export default {
  async scheduled(
    _controller: ScheduledController,
    env: Env,
    ctx: ExecutionContext,
  ): Promise<void> {
    ctx.waitUntil(refreshSnapshot(env));
  },

  async fetch(): Promise<Response> {
    return new Response(
      "C2ray CiaConfig feed is refreshed on schedule. Download configs.json from the public R2 custom domain.",
      {
        status: 404,
        headers: { "content-type": "text/plain; charset=utf-8" },
      },
    );
  },
} satisfies ExportedHandler<Env>;
