# Design System & UI/UX Document
## Telegram-Backed Photo Vault — Android v1

---

## 1. Design Direction & Rationale
This is a **vault**, not a file manager — the photos are precious, the app
is the protective layer around them. That framing drives every choice below:
a dark, quiet chrome that recedes, glass surfaces that feel protective and
transparent at once, and photos left completely undecorated so they're the
only thing that's actually bright on screen. This is a deliberate direction,
not a default — the goal is something that reads as considered and specific
to a personal media vault, not a reskin of a generic Material app.

Reference points for the *feel* (not to copy visually or reuse any branded
assets from): the restraint of Apple Photos' dark mode, the floating glass
chrome of recent iOS system UI, and the quiet confidence of tools like
Linear or Arc — premium reads as *disciplined*, not decorated.

## 2. Color System
Named tokens — six colors, used deliberately, not decoratively:

| Token | Hex | Role |
|---|---|---|
| **Void** | `#0A0B10` | Base background (dark mode) |
| **Panel** | `#12141C` | Base surface for glass panels, before blur/alpha |
| **Ink** | `#F4F5FA` | Primary text/icons |
| **Mist** | `#8B90A3` | Secondary text, timestamps, captions |
| **Signal** | `#6E5BFF` | Primary accent — CTAs, selection, active states |
| **Aurora** | `#3FE0C5` | Secondary accent — sync/success states only |

One additional functional color, used only for errors: **Ember** `#FF6B6B`.

**Usage rules:**
- Signal appears on at most one primary action per screen. It is not a
  general-purpose "brand color" splashed across icons and text.
- Aurora is reserved *exclusively* for backup/sync state (the Sync Halo,
  progress bars, "backed up" indicators) — this makes it mean something
  specific rather than being a second decorative accent.
- Photos and videos are never tinted, overlaid, or dimmed by the UI chrome
  except during selection mode (see §8).

**Light mode mapping** (same accent tokens, inverted base):

| Token | Hex |
|---|---|
| Paper (bg) | `#F6F7FB` |
| Panel (light) | `#FFFFFF` at 80–90% opacity over Paper |
| Ink (light) | `#14161F` |
| Mist (light) | `#6B7086` |

Dark is the primary/default mode — it's where the glass treatment reads
best — but light mode is a full first-class alternative, not an afterthought.

## 3. Typography
Two roles, deliberately paired rather than defaulted to a single family:

- **Display/Headline — Space Grotesk.** Geometric, slightly technical,
  gives screen titles and numbers (file counts, storage totals) a
  distinct personality without shouting.
- **Body/UI — Inter.** Chosen specifically for its legibility at small
  sizes in dense UI — this is the quiet, functional counterpart to Space
  Grotesk's character, not a default fallback.
- **Data/Mono — JetBrains Mono**, used narrowly for checksums, byte
  counts, and technical values in Settings — a small, considered touch
  that signals "this is a tool that respects your data," not decoration.

**Type scale:**

| Style | Size/Line | Weight | Face |
|---|---|---|---|
| Display | 34/40 | Medium | Space Grotesk |
| Title Large | 22/28 | Medium | Space Grotesk |
| Title Medium | 17/24 | Medium | Space Grotesk |
| Body Large | 16/24 | Regular | Inter |
| Body Medium | 14/20 | Regular | Inter |
| Label/Caption | 12/16 | Medium, +0.2 tracking | Inter |
| Data | 13/18 | Regular | JetBrains Mono |

## 4. Spacing & Layout
- 8dp base grid; 4dp used only for icon-to-label micro-spacing.
- Screen horizontal margin: 20dp.
- Gallery grid: 3 columns on phones, 2dp gutters — chosen tight and
  chromeless so the grid reads as one continuous field of photos, not a
  set of separated cards.

## 5. The Glass System
This is the signature visual language of the app, and it needs to be
scoped carefully or it becomes noise.

**Where glass is used:** floating chrome only — the top app bar (once
content scrolls beneath it), the bottom navigation capsule, the
upload-status capsule, and dialogs/sheets.

**Where glass is *not* used:** gallery grid items. Photos are the hero;
grid tiles are chromeless (just a 2dp corner radius, no border, no glass)
so nothing competes with the actual content. This restraint is the point —
spend the "glass" budget only on the UI floating above the photos, never on
the photos' own containers.

**Technical implementation note (important for the build):**
True frosted background blur on Android requires `RenderEffect`
(API 31 / Android 12+). Below that, live blur isn't reliably available.
- **API 31+**: real backdrop blur via `RenderEffect.createBlurEffect`,
  Panel color at ~40% opacity, 1px top-edge highlight
  (`rgba(255,255,255,0.14)`) instead of a Material-style drop shadow.
- **API 26–30 fallback**: no live blur. Use Panel at ~88% opacity (solid
  enough to be readable, translucent enough to hint depth) with the same
  top-edge highlight and a soft, low-opacity shadow. This should look like
  a slightly simplified sibling of the blurred version, not a broken one —
  test both paths, don't treat the fallback as an afterthought.
- Decide during v0.4 (Gallery UI phase) whether raising minSdk to 26 with
  this two-tier approach is acceptable, or whether the fallback tier should
  simply be dropped in favor of raising minSdk to 31 for a simpler, single
  code path. Either is defensible — document whichever is chosen.

## 6. Iconography
Avoid default filled Material Symbols — they read as generic Android.
Use a **light/duotone icon set** (e.g. Phosphor Icons, "light" weight,
~1.5px stroke) tinted Ink or Mist, with Signal reserved for the active/
selected state of an icon only. Thin, quiet icons let the glass and the
photos carry the visual weight instead.

## 7. Motion
- **Sync Halo** (the signature element — see §8): the one place the app
  allows itself sustained animation.
- Gallery → full-screen viewer: shared-element transition (the tapped
  thumbnail grows into the full-screen image), not a generic fade/slide.
- Bottom nav selection: subtle scale + fade, no spring/bounce overshoot.
- Everything else: fast, quiet, functional — 150–200ms ease-out is the
  default for anything not explicitly called out above.
- Respect the system's reduced-motion accessibility setting: Sync Halo
  falls back to a static ring + percentage label, shared-element
  transitions fall back to a plain cut.

## 8. Signature Element: the Sync Halo
The one memorable, deliberate visual moment of the app. While a file is
uploading, its thumbnail is ringed by a thin **Aurora**-colored halo that
slowly rotates and softly pulses — visually, the photo itself looks like
it's being "sealed into the vault." On completion, the halo contracts into
a small check mark in the corner and fades. This single motif does the job
that a generic progress bar or spinner would otherwise do, but ties it
directly and legibly to what's actually happening: this specific photo is
moving into your Telegram account right now.

No other element in the app competes for this kind of attention — this is
the one place the design spends its boldness (see design principle of
restraint: everything else stays quiet around it).

## 9. Component Specs
- **Top app bar**: transparent over content at scroll position 0;
  interpolates to a glass panel as content scrolls beneath it (opacity
  tied to scroll offset, not a hard cut).
- **Bottom navigation**: floating glass capsule, inset 12dp from the
  screen's bottom and side edges (not edge-to-edge). Three destinations:
  Vault, Backup, Settings.
- **Gallery grid item**: square, 2dp radius, chromeless. Sync Halo during
  upload; small Aurora dot, bottom-right, once confirmed backed up —
  minimal, not a badge that competes with the image.
- **Primary button**: filled pill, Signal background, Ink text.
- **Secondary button**: glass pill (Panel + border), Ink text.
- **Tertiary/text action**: no container, Mist text, Ink on press.
- **Selection mode**: selected tiles get a thin Signal border + small
  checkmark; unselected tiles dim to ~85% opacity so selected items read
  clearly against the grid.
- **Dialogs/sheets**: glass panel (per §5's tiered approach), rounded
  16dp, reserved for confirmations and the backup-status detail view.

## 10. Screen-by-Screen
1. **First-run (API credentials)** — Void background, centered glass card,
   two fields (`api_id`, `api_hash`), inline helper text + my.telegram.org
   link, primary pill "Continue."
2. **Phone / OTP / 2FA** — same glass-card-on-Void pattern, one field per
   screen, large auto-advancing OTP input.
3. **Vault (home/gallery)** — full-bleed 3-column grid on Void, floating
   glass top bar (title only, minimal), floating glass bottom nav.
4. **Full-screen viewer** — pure black background, image/video fills the
   frame, glass control bar fades in/out on tap, swipe left/right between
   items, swipe down to dismiss.
5. **Selection & backup** — long-press enters selection mode on the
   gallery grid; a glass capsule pinned to the bottom shows selected count
   + total size with a "Back up N items" primary pill.
6. **Backup status** — glass sheet listing queue items with per-item state,
   overall Aurora progress bar, and a clear pause banner if a Telegram
   flood-wait is currently in effect.
7. **Settings** — plain list on Void/Paper background, grouped sections
   (Account, Storage, About), Mist-colored secondary text per row; glass
   reserved only for destructive-action confirmation dialogs (e.g. logout,
   delete from vault).

## 11. Copy & Voice
- Active voice, named by what the user controls: "Back up," not "Sync now"
  or "Initiate transfer." The button, the resulting progress state, and any
  toast all use the same verb — "Back up" → "Backing up…" → "Backed up."
- Errors state what happened and how to fix it, without apologizing or
  being vague: "Upload paused — Telegram is rate-limiting this account.
  Resuming in ~4 min," not "Something went wrong."
- Empty states are an invitation, not a dead end: "Your vault is empty —
  select some photos to back up your first ones," with the action reachable
  from the same screen.

## 12. Accessibility
- Minimum 48dp touch targets throughout, including grid item long-press
  and glass nav icons.
- Verify Mist-on-Void and Mist-on-Paper against WCAG AA contrast
  thresholds specifically for body/caption text sizes before shipping —
  don't assume from the token definition alone, check it with a real
  contrast tool once colors are implemented.
- Visible focus states for keyboard/switch-access navigation on every
  interactive element, including inside glass panels where a focus ring
  can otherwise get visually lost against a translucent background.
- Full support for system font-scaling; the type scale in §3 uses sp, not
  fixed dp, throughout.
