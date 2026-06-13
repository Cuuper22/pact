# Assets notes

This catalog ships only what's needed to compile and run:

- **AppIcon** — a single 1024×1024 universal slot is declared but no image
  file is committed (binary assets aren't generated here). Drop a
  `1024x1024.png` into `AppIcon.appiconset/` and reference it in
  `Contents.json` before submitting; the icon should be the single ember on the
  near-black indigo night field (see IDEA.md "Name and look"). Keep it quiet —
  no gradients, no glassmorphism.
- **AccentColor** — the primary indigo `#3B2F8F` (oklch 0.40 0.15 270). Used by
  system controls; the app otherwise draws from `Theme.swift`.

All in-app color and the flame are drawn in code (`Theme.swift`, `Flame.swift`)
so the night surface stays exact across light/dark system settings. There are
no raster image assets in the UI by design — the look is type, color, and one
vector flame.

The custom shield's artwork is provided by the `ShieldConfiguration` extension
at runtime (an SF Symbol `flame.fill` plus the night background), not from this
catalog, because shield configurations take UIKit values directly.
