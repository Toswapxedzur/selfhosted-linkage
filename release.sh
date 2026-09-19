#!/usr/bin/env bash
# Build a versioned release: named jars (<ver>-<channel>+mc<mc>), host bundles, relay installer,
# checksums, and an upserted releases.json manifest — all staged into dist/ for deploy.
# Version + MC targets come from gradle.properties (mod_version, mc_versions). Run build-release.sh
# first if the bundled helper binaries aren't staged yet.
set -euo pipefail
cd "$(dirname "$0")"
VER=$(sed -n 's/^mod_version=//p' gradle.properties | tr -d '[:space:]')
MCS=$(sed -n 's/^mc_versions=//p' gradle.properties | tr -d '[:space:]')
DATE=$(date +%Y-%m-%d)
case "$VER" in *-beta*) CH=beta;; *-alpha*) CH=alpha;; *) CH=release;; esac
DIST=dist; DL=$DIST/dl; mkdir -p "$DL"

echo "==> build jars ($VER)"
./gradlew :fabric:build :neoforge:build -x test >/dev/null

echo "==> version + stage mod jars for MC: $MCS"
for MC in ${MCS//,/ }; do
  FAB="selfhosted-linkage-fabric-$VER+mc$MC.jar"
  NEO="selfhosted-linkage-neoforge-$VER+mc$MC.jar"
  cp "fabric/build/libs/selfhosted-linkage-fabric-$VER.jar"     "$DL/$FAB"
  cp "neoforge/build/libs/selfhosted-linkage-neoforge-$VER.jar" "$DL/$NEO"
  FSHA=$(shasum -a 256 "$DL/$FAB" | cut -d' ' -f1)
  NSHA=$(shasum -a 256 "$DL/$NEO" | cut -d' ' -f1)
  python3 - "$DIST/releases.json" "$VER" "$CH" "$DATE" "$MC" "dl/$FAB" "$FSHA" "dl/$NEO" "$NSHA" <<'PY'
import json,sys,os
p,ver,ch,date,mc,ffile,fsha,nfile,nsha=sys.argv[1:]
m={"mod":"selfhosted-linkage","releases":[]}
if os.path.exists(p): m=json.load(open(p))
m["releases"]=[r for r in m["releases"] if not (r["version"]==ver and r["mc"]==mc)]
m["releases"].append({"version":ver,"channel":ch,"date":date,"mc":mc,
  "fabric":{"file":ffile,"sha256":fsha},"neoforge":{"file":nfile,"sha256":nsha}})
def key(r):  # newest first: date, then version string
    return (r["date"], r["version"])
m["releases"].sort(key=key,reverse=True)
json.dump(m,open(p,"w"),indent=2); print("  manifest:",len(m["releases"]),"release rows")
PY
done

echo "==> host agents (per OS) + relay installer"
mk_host(){ os=$1; bin=$2; d=$(mktemp -d)/linkage-server; mkdir -p "$d"
  cp linkage-server/install.sh linkage-server/run-agent.sh linkage-server/README.md "$d/"
  cp "$bin" "$d/linkage-iroh"; chmod +x "$d/linkage-iroh" "$d/install.sh" "$d/run-agent.sh"
  tar -czf "$DL/linkage-server-$os.tar.gz" -C "$(dirname "$d")" linkage-server; }
mk_host macos-arm64 linkage-iroh/target/aarch64-apple-darwin/release/linkage-iroh
mk_host macos-x64   linkage-iroh/target/x86_64-apple-darwin/release/linkage-iroh
mk_host linux-x64   linkage-iroh/target/x86_64-unknown-linux-musl/release/linkage-iroh
cp linkage-relay/install.sh "$DL/linkage-relay-install.sh"

echo "==> checksums"
( cd "$DL" && shasum -a 256 *.jar *.tar.gz *.sh > checksums.txt )
echo "DONE. dist/ ready ($(ls "$DL" | wc -l | tr -d ' ') files). Manifest: dist/releases.json"
