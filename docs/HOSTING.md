# Hosting a Minecraft server with Selfhosted Linkage

Host a server **at home behind CGNAT / no port-forwarding**, and let friends join by installing only the
modpack — one checkbox, and either a key or a hostname. No VPN app, no admin rights, no account on your side.

## Two modes — pick one

| | **Public mode** | **Self-hosted mode** |
|---|---|---|
| You run | just the home agent | home agent **+ a small VPS** |
| Friends type | your server's **public key** + port | your **relay hostname** + port |
| Relays used | iroh's free public relays + discovery | your own relay |
| Best for | anywhere direct works (most home play) | China / strict networks / lowest latency |
| Cost | **$0, nothing to host but the agent** | a cheap VPS (1 GB is plenty) |

Both punch a **direct** peer-to-peer connection when the network allows (≈9/10 of the time); the relay is
only the fallback. Public mode's fallback is n0's free relays (reachable but far from China); self-hosted
mode's fallback is your own relay (fast everywhere, incl. China).

---

## Public mode (no VPS)

On the home machine, next to the bundled `linkage-iroh` binary:
```sh
sudo ./linkage-server/install.sh --mc-addr 127.0.0.1:25565
```
It installs the agent as a service and prints your server's **public key**. Give friends that key + your MC
port. Done — nothing else to host.

## Self-hosted mode (VPS + home)

**1. VPS** (Ubuntu + Caddy already installed), one command:
```sh
sudo ./linkage-relay/install.sh relay.example.com
```
Sets up iroh-relay + TLS + the registry. Then, as it tells you, **open inbound UDP 7842** in your cloud
firewall (for QUIC direct-connection discovery).

**2. Home machine:**
```sh
sudo ./linkage-server/install.sh --relay-url https://relay.example.com --mc-addr 127.0.0.1:25565
```
It prints a **ticket**. Paste it into the VPS registry once (the key is persisted, so the ticket is stable):
`/etc/iroh-relay/webroot/.well-known/selfhosted-linkage.json` →
```json
{ "version":1, "servers": { "25565": { "name":"My Server", "transport":"iroh",
    "relay_url":"https://relay.example.com", "ticket":"endpoint…" } } }
```
The `"25565"` key is the *selector* friends type as the port (any number; needn't equal the real MC port).

---

## What your friends do (either mode)
1. Install the modpack (Selfhosted Linkage is one jar, no extra dependencies).
2. **Add Server** / **Direct Connection** → tick **"Selfhosted Linkage"**.
3. Address = your **public key** (public mode) or **relay hostname** (self-hosted); Port = your port.
4. Join. First connect may show "Connecting…" for a few seconds while the link comes up.

The mod auto-detects the mode from the Address (a bare key vs a hostname). No VPN, no accounts.

---

## Notes
- **How it works:** the home agent exposes your MC port over iroh (dial-by-key); the mod starts iroh on the
  friend's side and points the game at a local port. Direct P2P when possible, relay only as fallback.
- **Security:** a key/ticket grants a peer the ability to reach *that one TCP port* — nothing else on the
  machine. Publishing it (in a registry, or handing out the key) is fine. Run any online-mode you like.
- **Identity is keyless & stateless:** no accounts anywhere. The only state is the agent's local keypair and
  (self-hosted) a static directory file. Persisting the key keeps your key/ticket stable across restarts.
- **Manual / under-the-hood** setup (what the installers do) is documented in `relay/README.md`.
- **Resilience:** the single point of failure in self-hosted mode is the VPS; for redundancy run a second
  relay on another host rather than a second transport.
