# Selfhosted Linkage

Let friends join your home Minecraft server — even behind CGNAT — by installing **one modpack**. No VPN app,
no account, no port forwarding, no router settings.

**Website & downloads: https://linkage.blopybox.net**

It's a Fabric + NeoForge client mod (MC 1.21.1) that carries peer-to-peer networking ([iroh](https://iroh.computer))
**inside the pack**. Your server runs a tiny agent that exposes it by a public key; friends tick a checkbox,
paste the key, and connect — directly when the network allows, via a relay only as a fallback. No accounts
anywhere; identity is a self-generated key.

## For players
Install the mod for your loader (from the website), then in the multiplayer menu: **Add Server** → tick
**“Selfhosted Linkage”** → paste the server's public key as the address + the port → Join. The single jar
carries the right helper for Windows, macOS (Intel & Apple Silicon), and Linux.

## For hosts
- **Public mode (no VPS):** run the `linkage-server` agent next to your server; it prints your public key.
  Share it with friends. Done.
- **Self-hosted mode (optional):** if a friend is consistently relayed and laggy, run your own relay on a
  cheap VPS with `linkage-relay`. Full walkthrough in [`docs/HOSTING.md`](docs/HOSTING.md).

## Build from source
```sh
# helper binaries (Rust) — one-time toolchain:
#   rustup + targets (aarch64/x86_64-apple-darwin, x86_64-unknown-linux-musl, x86_64-pc-windows-gnu)
#   brew install zig && cargo install cargo-zigbuild
./build-release.sh     # cross-compile linkage-iroh for all platforms + bundle into the jars
./release.sh           # produce versioned jars + checksums + releases.json in dist/
# or just the jars for this platform:
./gradlew :fabric:build :neoforge:build
```

## Layout
- `common/`, `fabric/`, `neoforge/` — the mod (Architectury; zero API deps → one jar per loader)
- `linkage-iroh/` — the bundled transport helper (a small fork of [dumbpipe](https://github.com/n0-computer/dumbpipe) adding a custom relay flag)
- `linkage-server/` — the home-side agent installer (systemd / launchd)
- `linkage-relay/` — the VPS relay installer (self-hosted mode)
- `relay/` — reference relay config + registry example
- `website/`, `brand/`, `docs/`

## License & credits
MIT / Apache-2.0. Built on [iroh](https://iroh.computer) and [dumbpipe](https://github.com/n0-computer/dumbpipe).

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
