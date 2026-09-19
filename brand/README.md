# Brand — Selfhosted Linkage icon

`icon.svg` is the master (rounded square, 200-unit viewBox). PNGs rendered from it with `rsvg-convert`.

- Orange box: `#F58A2B` · shadow (deeper orange): `#C06010` · white `#FFFFFF`
- Layers (bottom→top): white rounded-square bg → deep-orange offset copy of the box (shadow) → orange box →
  arrow's deep-orange offset copy → white double-arrow. The box divider curves match the corner radius.
- Corner radius 23% (46/200). Re-render: `for s in 1024 512 256 128 64 32; do rsvg-convert -w $s -h $s icon.svg -o icon-$s.png; done`

Note: the background is white, so on a white surface the outer square is invisible and only the orange box shows
— intended. A full-bleed / maskable variant (white to the very corners) can be added if a store needs it.
