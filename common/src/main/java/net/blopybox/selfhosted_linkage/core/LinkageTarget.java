package net.blopybox.selfhosted_linkage.core;

/**
 * What a registry selector resolves to: how to reach one server.
 * transport = "iroh"    → {@code ticket} is the agent's endpoint ticket (dial-by-key); {@code relayUrl}
 *                         is the self-hosted iroh relay both ends use (e.g. https://relay.example.com).
 * transport = "netbird" → join {@code managementUrl} with {@code setupKey}, then dial {@code peer}:{@code port}.
 */
public record LinkageTarget(String name, String transport, String ticket, String relayUrl,
                            String managementUrl, String setupKey, String peer, int port) {
    public boolean isIroh() { return "iroh".equalsIgnoreCase(transport); }
    public boolean isNetbird() { return "netbird".equalsIgnoreCase(transport); }
}
