using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;

namespace ServiceLib.Services;

public sealed record CiaConfigSyncResult(int Added, int Received, string Message);

public static class CiaConfigFeedService
{
    public const string FeedUrl = "https://c2ray-channel-sync.webserviceamir.workers.dev/api/configs";
    private static readonly TimeSpan RefreshInterval = TimeSpan.FromMinutes(15);
    private static readonly HttpClient Http = new(new HttpClientHandler
    {
        AutomaticDecompression = DecompressionMethods.All
    }) { Timeout = TimeSpan.FromSeconds(30) };

    private static readonly string StateDirectory = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "C2ray", "CiaConfig");
    private static readonly string StateFile = Path.Combine(StateDirectory, "last-sync.txt");
    private static readonly SemaphoreSlim SyncLock = new(1, 1);

    public static async Task<CiaConfigSyncResult> SyncAsync(bool force, CancellationToken cancellationToken = default)
    {
        await SyncLock.WaitAsync(cancellationToken);
        try
        {
            Directory.CreateDirectory(StateDirectory);
            if (!force && File.Exists(StateFile) &&
                DateTimeOffset.TryParse(await File.ReadAllTextAsync(StateFile, cancellationToken), out var last) &&
                DateTimeOffset.UtcNow - last < RefreshInterval)
                return new(0, 0, "CiaConfig was checked recently.");

            using var request = new HttpRequestMessage(HttpMethod.Get, FeedUrl);
            request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
            request.Headers.UserAgent.ParseAdd("C2ray-Windows/2.3");
            using var response = await Http.SendAsync(request, cancellationToken);
            response.EnsureSuccessStatusCode();

            await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
            using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);
            var root = doc.RootElement;
            if (!root.TryGetProperty("ok", out var ok) || ok.ValueKind != JsonValueKind.True ||
                !root.TryGetProperty("configs", out var configs) || configs.ValueKind != JsonValueKind.Array)
                throw new InvalidDataException("Unexpected CiaConfig Worker response.");

            var links = new HashSet<string>(StringComparer.Ordinal);
            foreach (var item in configs.EnumerateArray())
            {
                if (!item.TryGetProperty("uri", out var value) || value.ValueKind != JsonValueKind.String)
                    continue;
                var uri = value.GetString()?.Trim();
                if (string.IsNullOrWhiteSpace(uri) || !Uri.TryCreate(uri, UriKind.Absolute, out var parsed))
                    continue;
                if (new[] { "vless", "vmess", "ss", "trojan", "hysteria2", "hy2", "tuic", "wireguard" }
                    .Contains(parsed.Scheme, StringComparer.OrdinalIgnoreCase))
                    links.Add(uri);
            }

            if (links.Count == 0)
                throw new InvalidDataException("No supported configurations were returned by CiaConfig.");

            var added = await ConfigHandler.AddBatchServers(
                AppManager.Instance.Config, string.Join(Environment.NewLine, links), string.Empty, false);
            await File.WriteAllTextAsync(StateFile, DateTimeOffset.UtcNow.ToString("O"), cancellationToken);
            return new(Math.Max(0, added), links.Count,
                $"CiaConfig: {Math.Max(0, added)} new profiles added ({links.Count} received).");
        }
        finally { SyncLock.Release(); }
    }
}
