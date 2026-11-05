using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace ServiceLib.Services;

public sealed record CiaConfigSyncResult(
    bool Skipped,
    string ConfigText,
    int Count,
    string? Message);

/// <summary>
/// Downloads the public R2 snapshot of the CiaConfig Telegram channel.
/// Client devices never call the scheduled Cloudflare Worker.
/// </summary>
public static class CiaConfigFeedService
{
    private static readonly HttpClient Http = new(new HttpClientHandler
    {
        AutomaticDecompression = DecompressionMethods.All,
    })
    {
        Timeout = TimeSpan.FromSeconds(25),
    };

    private static readonly Regex ConfigRegex = new(
        @"(?i)\b(?:vless|vmess|trojan|ss|ssr|hysteria2?|hy2|tuic|wireguard)://[^\s<>""']+",
        RegexOptions.Compiled | RegexOptions.CultureInvariant);

    private static readonly TimeSpan AutomaticRefreshInterval = TimeSpan.FromHours(6);

    private static string FeedUrlFile => Path.Combine(AppContext.BaseDirectory, "CiaConfigFeedUrl.txt");
    private static string StateDirectory => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "C2ray",
        "CiaConfigFeed");
    private static string StateFile => Path.Combine(StateDirectory, "state.json");
    private static string SnapshotFile => Path.Combine(StateDirectory, "configs.json");

    private sealed class FeedState
    {
        public DateTimeOffset? LastSuccessUtc { get; set; }
        public string? ETag { get; set; }
        public DateTimeOffset? LastModifiedUtc { get; set; }
        public string? SnapshotSha256 { get; set; }
    }

    public static async Task<CiaConfigSyncResult> FetchAsync(
        bool force,
        CancellationToken cancellationToken = default)
    {
        var feedUrl = ReadFeedUrl();
        if (feedUrl is null)
        {
            return new CiaConfigSyncResult(
                true,
                string.Empty,
                0,
                "CiaConfig feed URL is not configured. Set CiaConfigFeedUrl.txt to the public R2 URL ending in /configs.json.");
        }

        Directory.CreateDirectory(StateDirectory);
        var state = await ReadStateAsync(cancellationToken);
        var now = DateTimeOffset.UtcNow;

        if (!force &&
            state.LastSuccessUtc.HasValue &&
            now - state.LastSuccessUtc.Value < AutomaticRefreshInterval)
        {
            return new CiaConfigSyncResult(true, string.Empty, 0, "The CiaConfig feed was checked recently.");
        }

        using var request = new HttpRequestMessage(HttpMethod.Get, feedUrl);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
        request.Headers.UserAgent.ParseAdd("C2ray-Windows/2.3");

        if (!string.IsNullOrWhiteSpace(state.ETag) &&
            EntityTagHeaderValue.TryParse(state.ETag, out var entityTag))
        {
            request.Headers.IfNoneMatch.Add(entityTag);
        }

        if (state.LastModifiedUtc.HasValue)
        {
            request.Headers.IfModifiedSince = state.LastModifiedUtc;
        }

        using var response = await Http.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken);

        if (response.StatusCode == HttpStatusCode.NotModified)
        {
            state.LastSuccessUtc = now;
            await WriteStateAsync(state, cancellationToken);
            return new CiaConfigSyncResult(true, string.Empty, 0, "CiaConfig is already up to date.");
        }

        response.EnsureSuccessStatusCode();
        var json = await response.Content.ReadAsStringAsync(cancellationToken);
        using var document = JsonDocument.Parse(json);

        var configsElement = TryGetProperty(document.RootElement, "configs", out var configs)
            ? configs
            : document.RootElement;

        var links = new HashSet<string>(StringComparer.Ordinal);
        CollectConfigLinks(configsElement, links);

        if (links.Count == 0)
        {
            throw new InvalidDataException(
                "The CiaConfig feed returned no supported configuration links. The previous local data was kept.");
        }

        var configText = string.Join(Environment.NewLine, links);
        var hash = Convert.ToHexString(
            System.Security.Cryptography.SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(configText)));

        var tempSnapshot = SnapshotFile + ".tmp";
        await File.WriteAllTextAsync(tempSnapshot, json, cancellationToken);
        File.Move(tempSnapshot, SnapshotFile, true);

        state.LastSuccessUtc = now;
        state.ETag = response.Headers.ETag?.ToString();
        state.LastModifiedUtc = response.Content.Headers.LastModified;
        state.SnapshotSha256 = hash;
        await WriteStateAsync(state, cancellationToken);

        return new CiaConfigSyncResult(false, configText, links.Count, null);
    }

    private static string? ReadFeedUrl()
    {
        try
        {
            if (!File.Exists(FeedUrlFile))
            {
                return null;
            }

            var value = File.ReadAllText(FeedUrlFile).Trim();
            if (!Uri.TryCreate(value, UriKind.Absolute, out var uri) ||
                (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp) ||
                uri.Host.Contains("YOUR-R2", StringComparison.OrdinalIgnoreCase) ||
                uri.Host.Equals("example.com", StringComparison.OrdinalIgnoreCase))
            {
                return null;
            }

            return uri.ToString();
        }
        catch
        {
            return null;
        }
    }

    private static bool TryGetProperty(JsonElement element, string name, out JsonElement value)
    {
        if (element.ValueKind == JsonValueKind.Object)
        {
            foreach (var property in element.EnumerateObject())
            {
                if (string.Equals(property.Name, name, StringComparison.OrdinalIgnoreCase))
                {
                    value = property.Value;
                    return true;
                }
            }
        }

        value = default;
        return false;
    }

    private static void CollectConfigLinks(JsonElement element, HashSet<string> links)
    {
        switch (element.ValueKind)
        {
            case JsonValueKind.String:
                var value = element.GetString();
                if (!string.IsNullOrWhiteSpace(value))
                {
                    foreach (Match match in ConfigRegex.Matches(value))
                    {
                        var link = match.Value.Trim().TrimEnd(',', ';', '.', ')', ']', '}', '،');
                        if (link.Length > 12)
                        {
                            links.Add(link);
                        }
                    }
                }
                break;

            case JsonValueKind.Array:
                foreach (var child in element.EnumerateArray())
                {
                    CollectConfigLinks(child, links);
                }
                break;

            case JsonValueKind.Object:
                foreach (var property in element.EnumerateObject())
                {
                    CollectConfigLinks(property.Value, links);
                }
                break;
        }
    }

    private static async Task<FeedState> ReadStateAsync(CancellationToken cancellationToken)
    {
        try
        {
            if (!File.Exists(StateFile))
            {
                return new FeedState();
            }

            var json = await File.ReadAllTextAsync(StateFile, cancellationToken);
            return JsonSerializer.Deserialize<FeedState>(json) ?? new FeedState();
        }
        catch
        {
            return new FeedState();
        }
    }

    private static async Task WriteStateAsync(FeedState state, CancellationToken cancellationToken)
    {
        var temp = StateFile + ".tmp";
        var json = JsonSerializer.Serialize(state);
        await File.WriteAllTextAsync(temp, json, cancellationToken);
        File.Move(temp, StateFile, true);
    }
}
