#!/usr/bin/env bash
# Build the plugin and install it into local PyCharm 2026.2 config dirs (desktop and
# Gateway/remote-dev backends). Usage: ./install.sh [--no-build] [PLUGINS_DIR...]
set -euo pipefail
cd "$(dirname "$0")"

BUILD=1
if [[ "${1:-}" == "--no-build" ]]; then BUILD=0; shift; fi

if (( BUILD )); then
  args=()
  # Reuse an installed PyCharm (e.g. a Gateway backend) instead of downloading one.
  for d in "${PYCHARM_HOME:-}" "$HOME"/.cache/JetBrains/RemoteDev/dist/*pycharm-2026.2* \
           /opt/pycharm* "$HOME"/.local/share/JetBrains/Toolbox/apps/pycharm*/ ; do
    if [[ -n "$d" && -f "$d/product-info.json" ]]; then args+=("-PplatformLocalPath=$d"); break; fi
  done
  ./gradlew buildPlugin "${args[@]}" --console=plain -q
fi

ZIP=$(ls -t build/distributions/*.zip | head -1)
echo "Plugin: $ZIP"

targets=("$@")
if (( ${#targets[@]} == 0 )); then
  base="${XDG_DATA_HOME:-$HOME/.local/share}/JetBrains"
  for cfg in "${XDG_CONFIG_HOME:-$HOME/.config}"/JetBrains/PyCharm2026.2*; do
    [[ -d "$cfg" ]] && targets+=("$base/$(basename "$cfg")")
  done
  (( ${#targets[@]} )) || targets=("$base/PyCharm2026.2")
fi

for t in "${targets[@]}"; do
  mkdir -p "$t"
  rm -rf "$t/pycharm-doxygen-docstrings"
  unzip -q -o "$ZIP" -d "$t"
  echo "Installed into $t"
done
echo "Restart PyCharm (for Gateway: restart the backend IDE) to load the plugin."
