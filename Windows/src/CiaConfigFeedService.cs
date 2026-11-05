using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text.Json;

namespace ServiceLib.Services;

public sealed record CiaConfigSyncResult(int Added, int Received, string Message);

public static class CiaConfigFeedService
{
    public const string FeedUrl = "https://c2ray-channel-sync.webserviceamir.workers.dev/api/configs";

    private static readonly string[] SupportedSchemes =
    [
        "vless", "vmess", "ss", "trojan", "hysteria2", "hy2", "tuic", "wireguard"
    ];

    private static readonly TimeSpan RefreshInterval = TimeSpan.FromMinutes(15);
    private static readonly HttpClient Http = new(new HttpClientHandler
    {
        AutomaticDecompression = DecompressionMethods.All
    }) { Timeout = TimeSpan.FromSeconds(30) };

    private static readonly string StateDirectory = Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "C2ray", "CiaConfig");
    private static readonly string StateFile = Path.Combine(StateDirectory, "last-sync.txt");
    private static readonly SemaphoreSlim SyncLock = new(1, 1);

    public static async Task<CiaConfigSyncResult> SyncAsync(
        bool force,
        CancellationToken cancellationToken = default)
    {
        await SyncLock.WaitAsync(cancellationToken);
        try
        {
            Directory.CreateDirectory(StateDirectory);
            if (!force && File.Exists(StateFile) &&
                DateTimeOffset.TryParse(
                    await File.ReadAllTextAsync(StateFile, cancellationToken),
                    out var last) &&
                DateTimeOffset.UtcNow - last < RefreshInterval)
            {
                return new(0, 0, "CiaConfig was checked recently.");
            }

            using var request = new HttpRequestMessage(HttpMethod.Get, FeedUrl);
            request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
            request.Headers.UserAgent.ParseAdd("C2ray-Windows/2.3.18");

            using var response = await Http.SendAsync(
                request,
                HttpCompletionOption.ResponseHeadersRead,
                cancellationToken);
            response.EnsureSuccessStatusCode();

            await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
            using var doc = await JsonDocument.ParseAsync(stream, cancellationToken: cancellationToken);
            var root = doc.RootElement;
            if (!root.TryGetProperty("ok", out var ok) ||
                ok.ValueKind != JsonValueKind.True ||
                !root.TryGetProperty("configs", out var configs) ||
                configs.ValueKind != JsonValueKind.Array)
            {
                throw new InvalidDataException("Unexpected CiaConfig Worker response.");
            }

            var links = new HashSet<string>(StringComparer.Ordinal);
            foreach (var item in configs.EnumerateArray())
            {
                if (!item.TryGetProperty("uri", out var value) ||
                    value.ValueKind != JsonValueKind.String)
                {
                    continue;
                }

                var uri = value.GetString()?.Trim();
                if (string.IsNullOrWhiteSpace(uri) ||
                    !Uri.TryCreate(uri, UriKind.Absolute, out var parsed) ||
                    !SupportedSchemes.Contains(parsed.Scheme, StringComparer.OrdinalIgnoreCase))
                {
                    continue;
                }

                links.Add(uri);
            }

            if (links.Count == 0)
            {
                throw new InvalidDataException(
                    "No supported configurations were returned by CiaConfig.");
            }

            // Compare with the complete local profile table before importing.
            // AddBatchServers itself does not deduplicate ordinary (non-subscription) imports.
            var existingProfiles =
                await AppManager.Instance.ProfileItems(string.Empty) ?? [];
            var seenCandidates = new List<ProfileItem>();
            var newLinks = new List<string>();
            var parseableCount = 0;

            foreach (var link in links)
            {
                var candidate = FmtHandler.ResolveConfig(link, out _);
                if (candidate is null)
                {
                    continue;
                }

                parseableCount++;
                if (existingProfiles.Any(existing => IsSameProfile(existing, candidate)) ||
                    seenCandidates.Any(existing => IsSameProfile(existing, candidate)))
                {
                    continue;
                }

                newLinks.Add(link);
                seenCandidates.Add(candidate);
            }

            if (parseableCount == 0)
            {
                throw new InvalidDataException(
                    "CiaConfig returned links, but C2ray could not parse any of them.");
            }

            if (newLinks.Count == 0)
            {
                await File.WriteAllTextAsync(
                    StateFile,
                    DateTimeOffset.UtcNow.ToString("O"),
                    cancellationToken);
                return new(0, links.Count,
                    $"CiaConfig is up to date ({parseableCount} configurations checked).");
            }

            var added = await ConfigHandler.AddBatchServers(
                AppManager.Instance.Config,
                string.Join(Environment.NewLine, newLinks),
                string.Empty,
                false);

            if (added <= 0)
            {
                throw new InvalidDataException(
                    "C2ray could not import the new configurations returned by CiaConfig.");
            }

            await File.WriteAllTextAsync(
                StateFile,
                DateTimeOffset.UtcNow.ToString("O"),
                cancellationToken);

            return new(
                added,
                links.Count,
                $"CiaConfig: {added} new profiles added ({links.Count} received, " +
                $"{links.Count - newLinks.Count} already present or skipped).");
        }
        finally
        {
            SyncLock.Release();
        }
    }

    private static bool IsSameProfile(ProfileItem left, ProfileItem right)
    {
        var leftProtocol = left.GetProtocolExtra();
        var rightProtocol = right.GetProtocolExtra();
        var leftTransport = left.GetTransportExtra();
        var rightTransport = right.GetTransportExtra();

        return left.ConfigType == right.ConfigType
            && AreEqual(left.Address, right.Address)
            && left.Port == right.Port
            && AreEqual(left.Password, right.Password)
            && AreEqual(left.Username, right.Username)
            && AreEqual(leftProtocol.VlessEncryption, rightProtocol.VlessEncryption)
            && AreEqual(leftProtocol.SsMethod, rightProtocol.SsMethod)
            && AreEqual(leftProtocol.VmessSecurity, rightProtocol.VmessSecurity)
            && AreEqual(left.Network, right.Network)
            && AreEqual(leftTransport.RawHeaderType, rightTransport.RawHeaderType)
            && AreEqual(leftTransport.Host, rightTransport.Host)
            && AreEqual(leftTransport.Path, rightTransport.Path)
            && AreEqual(leftTransport.XhttpMode, rightTransport.XhttpMode)
            && AreEqual(leftTransport.XhttpExtra, rightTransport.XhttpExtra)
            && AreEqual(leftTransport.GrpcAuthority, rightTransport.GrpcAuthority)
            && AreEqual(leftTransport.GrpcServiceName, rightTransport.GrpcServiceName)
            && AreEqual(leftTransport.GrpcMode, rightTransport.GrpcMode)
            && AreEqual(leftTransport.KcpHeaderType, rightTransport.KcpHeaderType)
            && AreEqual(leftTransport.KcpSeed, rightTransport.KcpSeed)
            && (left.ConfigType == EConfigType.Trojan ||
                AreEqual(left.StreamSecurity, right.StreamSecurity))
            && AreEqual(leftProtocol.Flow, rightProtocol.Flow)
            && AreEqual(leftProtocol.SalamanderPass, rightProtocol.SalamanderPass)
            && AreEqual(left.Sni, right.Sni)
            && AreEqual(left.Alpn, right.Alpn)
            && AreEqual(left.Fingerprint, right.Fingerprint)
            && AreEqual(left.PublicKey, right.PublicKey)
            && AreEqual(left.ShortId, right.ShortId)
            && AreEqual(left.Finalmask, right.Finalmask);
    }

    private static bool AreEqual(string? left, string? right)
    {
        return string.Equals(left, right, StringComparison.Ordinal) ||
               (string.IsNullOrEmpty(left) && string.IsNullOrEmpty(right));
    }
}
