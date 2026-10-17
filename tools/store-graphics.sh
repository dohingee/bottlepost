#!/usr/bin/env bash
# Renders the Play Store icon (512x512) and feature graphics (1024x500, ko + en) to PNG.
#   ICON_PREVIEW_DIR=/tmp python3 tools/brand-icon.py && bash tools/store-graphics.sh
set -euo pipefail
cd "$(dirname "$0")/../docs/store/graphics"
CHROME="${CHROME:-/opt/pw-browsers/chromium-1194/chrome-linux/chrome}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

shot() { # html w h out — renders taller than needed, then crops (headless reserves space for window chrome)
  "$CHROME" --headless --no-sandbox --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --virtual-time-budget=3000 --window-size="$2,$(($3 + 200))" --screenshot="$TMP/raw.png" "file://$1" >/dev/null 2>&1
  convert "$TMP/raw.png" -crop "${2}x${3}+0+0" +repage -alpha on "PNG32:$PWD/$4"
}

# Only the glyphs we draw, from Google Fonts, embedded locally so rendering does not wait on the network.
font() { # family text -> @font-face css
  local css url
  css=$(curl -sS -A "Mozilla/5.0 Chrome/120" -G "https://fonts.googleapis.com/css2" \
    --data-urlencode "family=$1:wght@600" --data-urlencode "text=$2")
  url=$(grep -o 'https://[^)]*' <<<"$css" | head -1)
  curl -sS -o "$TMP/$1.ttf" "$url"
  echo "@font-face{font-family:'$1';font-weight:600;src:url('file://$TMP/$1.ttf')}"
}

cat > "$TMP/icon.html" <<HTML
<html><body style="margin:0"><img src="file://$PWD/play_icon.svg" width="512" height="512"></body></html>
HTML
shot "$TMP/icon.html" 512 512 play_icon_512.png

feature() { # lang name tagline
  local faces
  faces="$(font "Noto Serif KR" "$2$3")$(font "Noto Serif" "$2$3")"
  cat > "$TMP/feature_$1.html" <<HTML
<html><head><style>
$faces
body{margin:0;width:1024px;height:500px;position:relative;overflow:hidden;font-family:'Noto Serif KR','Noto Serif',serif;color:#FBF7F0}
img{position:absolute;inset:0}
.t{position:absolute;left:72px;top:150px}
h1{margin:0;font-size:84px;font-weight:600;letter-spacing:1px;text-shadow:0 2px 12px rgba(0,0,0,.25)}
p{margin:14px 0 0;font-size:30px;opacity:.85}
</style></head><body><img src="file://$PWD/feature_bg.svg" width="1024" height="500">
<div class="t"><h1>$2</h1><p>$3</p></div></body></html>
HTML
  shot "$TMP/feature_$1.html" 1024 500 "feature_$1.png"
}
feature ko "보틀포스트" "낯선 이에게 띄우는 편지"
feature en "Bottlepost" "Letters in a bottle to a stranger"
ls -l ./*.png
