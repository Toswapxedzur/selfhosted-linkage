#!/usr/bin/env bash
# Reproducible release build: cross-compile linkage-iroh for the 4 target platforms,
# stage them into the mod's bundled resources, and build both loader jars.
#
# Toolchain (one-time, on macOS):
#   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y --no-modify-path
#   rustup target add aarch64-apple-darwin x86_64-apple-darwin x86_64-unknown-linux-musl x86_64-pc-windows-gnu
#   brew install zig && cargo install cargo-zigbuild
#
# The 4 targets cover ≥99% of Minecraft Java players (Win/Mac-both-arches/Linux); the exotic
# remainder (Win-ARM runs x64 via emulation; Linux-ARM) uses the mod's bin/ override dir.
set -euo pipefail
cd "$(dirname "$0")"
export PATH="$HOME/.cargo/bin:$PATH"
R=common/src/main/resources/helpers

echo "==> macOS (native Apple SDK)"
( cd linkage-iroh && cargo build --release --target aarch64-apple-darwin && cargo build --release --target x86_64-apple-darwin )
echo "==> Linux (static musl) + Windows (gnu) via zig"
( cd linkage-iroh && cargo zigbuild --release --target x86_64-unknown-linux-musl && cargo zigbuild --release --target x86_64-pc-windows-gnu )

echo "==> stage into $R/"
mkdir -p "$R"/{macos-arm64,macos-x64,windows-x64,linux-x64}
cp linkage-iroh/target/aarch64-apple-darwin/release/linkage-iroh    "$R/macos-arm64/linkage-iroh"
cp linkage-iroh/target/x86_64-apple-darwin/release/linkage-iroh     "$R/macos-x64/linkage-iroh"
cp linkage-iroh/target/x86_64-unknown-linux-musl/release/linkage-iroh "$R/linux-x64/linkage-iroh"
cp linkage-iroh/target/x86_64-pc-windows-gnu/release/linkage-iroh.exe "$R/windows-x64/linkage-iroh.exe"
chmod +x "$R"/macos-*/linkage-iroh "$R"/linux-x64/linkage-iroh

echo "==> build loader jars"
./gradlew :fabric:build :neoforge:build -x test
echo
echo "DONE. Jars (helpers bundled at /helpers/<os>-<arch>/):"
ls -la fabric/build/libs/*-fabric-*.jar neoforge/build/libs/*-neoforge-*.jar 2>/dev/null | grep -vE 'sources|dev-shadow'
