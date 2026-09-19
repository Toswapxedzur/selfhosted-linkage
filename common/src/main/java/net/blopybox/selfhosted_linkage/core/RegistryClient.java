package net.blopybox.selfhosted_linkage.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The directory lookup: relay address + selector ("port") → {@link LinkageTarget}.
 * The relay publishes a small JSON registry at /.well-known/selfhosted-linkage.json:
 * <pre>{ "version": 1, "servers": { "25565": { "transport": "iroh", "ticket": "endpoint…" },
 *                                  "25566": { "transport": "netbird", "management_url": "https://…",
 *                                             "setup_key": "…", "peer": "100.x.y.z", "port": 25565 } } }</pre>
 * A server may offer several transports, tried in order (iroh-primary, NetBird fallback):
 * <pre>"25565": { "transports": [ { "transport": "iroh", "ticket": "endpoint…" },
 *                             { "transport": "netbird", "management_url": "…", "setup_key": "…",
 *                               "peer": "100.x.y.z", "port": 25565 } ] }</pre>
 * A single legacy object that carries BOTH an iroh ticket and netbird fields also expands to
 * [iroh, netbird] automatically. It is a DIRECTORY, not a proxy: it never carries game traffic.
 */
public final class RegistryClient {
    public static final String WELL_KNOWN = "/.well-known/selfhosted-linkage.json";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

    private RegistryClient() {}

    /** @param relay host[:port], optionally prefixed with http:// or https:// (default: try https, then http). */
    public static LinkageTarget resolve(String relay, String selector) throws IOException {
        return resolveAll(relay, selector).get(0);
    }

    /** All transports for a selector, in the order they should be tried (iroh-primary, NetBird fallback). */
    public static List<LinkageTarget> resolveAll(String relay, String selector) throws IOException {
        IOException last = null;
        for (String base : candidates(relay.trim())) {
            try {
                return parse(fetch(base + WELL_KNOWN), selector, base);
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no registry URL candidates for " + relay);
    }

    static List<String> candidates(String relay) {
        List<String> out = new ArrayList<>();
        String r = relay.endsWith("/") ? relay.substring(0, relay.length() - 1) : relay;
        if (r.startsWith("http://") || r.startsWith("https://")) {
            out.add(r);
        } else {
            out.add("https://" + r);
            out.add("http://" + r);
        }
        return out;
    }

    private static String fetch(String url) throws IOException {
        try {
            HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IOException("registry " + url + " answered HTTP " + res.statusCode());
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while fetching registry", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("bad relay address: " + url, e);
        }
    }

    static List<LinkageTarget> parse(String json, String selector, String source) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject servers = root.getAsJsonObject("servers");
            if (servers == null || !servers.has(selector))
                throw new IOException("relay " + source + " has no server on port " + selector);
            JsonObject s = servers.getAsJsonObject(selector);
            String name = str(s, "name");
            List<LinkageTarget> out = new ArrayList<>();
            if (s.has("transports") && s.get("transports").isJsonArray()) {
                for (com.google.gson.JsonElement e : s.getAsJsonArray("transports"))
                    out.add(target(name, e.getAsJsonObject()));
            } else {
                // A single object. If it carries both an iroh ticket and netbird fields, expand
                // iroh-first (iroh punches direct where NetBird relays); else it's one transport.
                boolean iroh = str(s, "ticket") != null, netbird = str(s, "peer") != null || str(s, "setup_key") != null;
                if (iroh && netbird && str(s, "transport") == null) {
                    out.add(new LinkageTarget(name, "iroh", str(s, "ticket"), str(s, "relay_url"), null, null, null, 0));
                    out.add(new LinkageTarget(name, "netbird", null, null, str(s, "management_url"),
                            str(s, "setup_key"), str(s, "peer"), s.has("port") ? s.get("port").getAsInt() : 25565));
                } else {
                    out.add(target(name, s));
                }
            }
            if (out.isEmpty()) throw new IOException("relay " + source + " lists no transport for port " + selector);
            return out;
        } catch (IllegalStateException | ClassCastException | com.google.gson.JsonParseException e) {
            throw new IOException("relay " + source + " returned a malformed registry", e);
        }
    }

    private static LinkageTarget target(String name, JsonObject s) {
        return new LinkageTarget(name != null ? name : str(s, "name"), str(s, "transport"), str(s, "ticket"),
                str(s, "relay_url"), str(s, "management_url"), str(s, "setup_key"), str(s, "peer"),
                s.has("port") ? s.get("port").getAsInt() : 25565);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
}
