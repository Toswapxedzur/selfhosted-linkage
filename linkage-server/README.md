# linkage-server — the home-side agent

Runs next to the Minecraft server and exposes it over iroh so friends' mods can dial it with no
port-forwarding. One-command install (systemd or launchd):

```sh
sudo ./install.sh                                        # PUBLIC mode  → prints your public key
sudo ./install.sh --relay-url https://relay.example.com  # SELF-HOSTED  → prints a ticket to register
```

`run-agent.sh` picks the mode from `LINKAGE_RELAY_URL` (set = self-hosted, unset = public via iroh's free
relays). Below is the manual/under-the-hood detail.

## Files
| File | What |
|---|---|
| `run-agent.sh` | loads the persisted `IROH_SECRET` and execs `linkage-iroh listen-tcp --host 127.0.0.1:25565 --relay-url https://relay.example.com`. Overridable via `$LINKAGE_RELAY_URL` / `$LINKAGE_MC_ADDR`. |
| `com.blopybox.linkage-server.plist` | macOS LaunchDaemon template (KeepAlive, RunAtLoad). Substitute `__USER__` / `__HOME__`. |
| `iroh-secret` | **not committed** — the 32-byte hex iroh secret key (chmod 600). Persisting it keeps the endpoint ticket stable across restarts, so the registry entry never changes. |
| `linkage-iroh` | **not committed** — the built helper binary for the box's platform (`../linkage-iroh`). |

## Install (macOS, per home box)
```sh
mkdir -p ~/linkage-server && cd ~/linkage-server
# put the linkage-iroh binary here (built for this platform) + run-agent.sh
# mint & persist a key ONCE (or reuse an existing one):
IROH_SECRET=$(openssl rand -hex 32); echo "$IROH_SECRET" > iroh-secret; chmod 600 iroh-secret
sudo cp com.blopybox.linkage-server.plist /Library/LaunchDaemons/   # after substituting __USER__/__HOME__
sudo launchctl load -w /Library/LaunchDaemons/com.blopybox.linkage-server.plist
grep -oE 'endpoint[a-z0-9]+' listen.log | head -1     # → publish this ticket in the HK registry (F)
```
Stop: `sudo launchctl bootout system/com.blopybox.linkage-server`.

The ticket printed in `listen.log` is what the HK registry publishes for this server's selector (see PLAN §6/F).
On Linux this is a systemd unit instead; the run script is the same.
