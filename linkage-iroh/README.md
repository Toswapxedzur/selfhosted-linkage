# linkage-iroh

The iroh transport helper the mod bundles and runs (`connect-tcp` on the client,
`listen-tcp` on the home agent). It is a **minimal fork of [dumbpipe](https://github.com/n0-computer/dumbpipe)**
(MIT/Apache-2.0, © number 0) with exactly one addition:

- **`--relay-url <URL>` (or `$LINKAGE_RELAY_URL`)** → sets `RelayMode::custom([url])` on the iroh
  endpoint, so both ends use our self-hosted relay (`https://relay.example.com`) instead of n0's public
  relays. n0's relays are unreachable from mainland China; ours is on the HK VPS and reachable there.
  Direct hole-punching still happens whenever the network allows it — the relay is only the fallback.

Everything else (TCP piping, tickets, ALPN, half-close handling) is stock dumbpipe. Keeping it a thin fork
follows the project's open-source-first rule; the change is small enough to upstream as a PR.

## Build
```
cargo build --release      # → target/release/linkage-iroh   (iroh 1.x, matches iroh-relay 1.2.0 on HK)
```

## Use
```
# home agent (persist IROH_SECRET so the ticket is stable across restarts):
IROH_SECRET=<hex> linkage-iroh listen-tcp --host 127.0.0.1:25565 --relay-url https://relay.example.com
# client:
linkage-iroh connect-tcp --addr 127.0.0.1:25578 --relay-url https://relay.example.com <ticket>
```

## Verified
2026-09-18, from a school network (UDP-blocked, so direct impossible): connect side logged
`home is now relay https://relay.example.com` and a full MC 1.21.1 probe (status + login) succeeded
through the tunnel. Since only our relay is in the map, the traffic provably relayed through our HK box —
the China-reachable iroh path this fork exists to enable.
