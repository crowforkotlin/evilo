# Evilo Design System

## Scope and direction

Evilo uses one restrained Material Design 3 design system for Android, desktop,
web, and iOS. Page implementations stay in `evilo-app`; platform launchers do not
duplicate them. Teal identifies selection and primary actions, neutral surfaces
separate groups, and content supplies most of the visual detail. Do not mix
glass blur, neumorphic shadows, promotional gradients, and Material cards.

The references inform structure, not copied implementation:

- [Material Design 3](https://m3.material.io/): color roles, navigation, shapes,
  interaction states, and accessible controls.
- [Mihon](https://github.com/mihonapp/mihon): content-led pages, compact app bars,
  bottom navigation on phones, and a rail on wider windows.
- [RikkaHub](https://github.com/rikkahub/rikkahub): rounded settings groups and
  readable separation between titles, descriptions, and actions.
- [UI Resource Hub](https://siinxu.github.io/ui-resource-hub/mobile/): mobile
  layout references. It does not introduce a second component library.

Implementation owners:

| File | Responsibility |
| --- | --- |
| `evilo-app/src/commonMain/kotlin/crow/evilo/ui/EviloTheme.kt` | Light/dark colors, typography, Material shapes |
| `evilo-app/src/commonMain/kotlin/crow/evilo/ui/EviloLayout.kt` | Spacing, radii, elevation, component dimensions, breakpoints |
| `evilo-app/src/commonMain/kotlin/crow/evilo/ui/EviloIcons.kt` | Shared outlined icons |
| `evilo-app/src/commonMain/kotlin/crow/evilo/ui/EviloMotion.kt` | Motion durations/easing, Material motion scheme, page/content transitions |
| `evilo-app/src/commonMain/kotlin/crow/evilo/ui/EviloToast.kt` | Single floating notification, timeout, dismissal, optional action |
| `evilo-app/src/commonMain/kotlin/crow/evilo/ui/EviloComponents.kt` | Search fields, section headings, empty states, settings rows |
| `evilo-app/src/commonMain/kotlin/crow/evilo/ui/EviloAsyncImage.kt` | Shared Coil loading, retry, errors, and natural-ratio rendering |
| `evilo-app/src/commonMain/kotlin/crow/evilo/ui/EviloImageError.kt` | Localized, redacted image diagnostics and copy/retry actions |
| `evilo-app/src/commonMain/kotlin/crow/evilo/feature/browse/PluginNavigation.kt` | Bottom navigation, navigation rail, content-type selector |
| `evilo-web/src/webMain/resources/styles.css` | The HTML loading screen before shared Compose content is ready |

## Colors

Use `MaterialTheme.colorScheme` roles in pages, not literal colors. Both schemes
are explicit and stable; device wallpaper does not replace the palette.

| Role | Light | Dark |
| --- | --- | --- |
| `primary` | `#006A6B` | `#78D6CF` |
| `onPrimary` | `#FFFFFF` | `#003735` |
| `primaryContainer` | `#B5F0EC` | `#00514E` |
| `onPrimaryContainer` | `#003E3F` | `#A4F0E9` |
| `secondary` | `#4B6462` | `#B4CDC4` |
| `onSecondary` | `#FFFFFF` | `#20352E` |
| `secondaryContainer` | `#D6E7E3` | `#374B43` |
| `onSecondaryContainer` | `#233B37` | `#D6E7E3` |
| `tertiary` | `#795A30` | `#E8C188` |
| `onTertiary` | `#FFFFFF` | `#432C0B` |
| `tertiaryContainer` | `#F7DEB2` | `#5D431F` |
| `onTertiaryContainer` | `#48330F` | `#F7DEB2` |
| `background` | `#F7FAF9` | `#101716` |
| `surface` | `#FFFFFF` | `#131B19` |
| `onSurface` / `onBackground` | `#192522` | `#E1ECE7` |
| `onSurfaceVariant` | `#51625C` | `#B6C8BF` |
| `outline` | `#74847D` | `#7F9288` |
| `outlineVariant` | `#D6DFD9` | `#344A40` |
| `error` | `#BA1A1A` | `#FFB4AB` |
| `errorContainer` | `#FFDAD6` | `#93000A` |
| `onErrorContainer` | `#410002` | `#FFDAD6` |

Surface levels provide hierarchy without stacking shadows:

| Role | Light | Dark | Use |
| --- | --- | --- | --- |
| `surfaceContainerLowest` | `#FFFFFF` | `#0C1210` | Lowest neutral level |
| `surfaceContainerLow` | `#F0F5F3` | `#19231F` | Search fields, settings details, rail |
| `surfaceContainer` | `#E9F0EE` | `#1E2924` | Bottom navigation, neutral badges |
| `surfaceContainerHigh` | `#E2EAE7` | `#28342E` | Higher neutral level |
| `surfaceContainerHighest` | `#DCE4E1` | `#334039` | Image placeholders |

Use primary roles for selection and enabled primary actions. Use
`onSurfaceVariant` for authors, dates, counts, and descriptions. Use error roles
for failures, HTTP warnings, NSFW warnings, and destructive actions. Installed
version badges use neutral surfaces. Tertiary is reserved for
occasional emphasis, not a different accent for every media type.

`App()` draws a subtle vertical gradient from `surfaceContainerLow` to
`background`. Text-bearing cards use opaque surfaces. Dark mode uses the same
structure and shapes, with its own text, outline, container, and error colors.
`EviloTheme()` follows the system preference. Settings -> Appearance describes this behavior; it
does not offer a manual theme selector or persist an appearance preference.

## Typography

The supplied `ChillRoundM-SemiBold.ttf` is bundled, without modifying or subsetting
its bytes, as
`evilo-app/src/commonMain/composeResources/font/ChillRoundMSemiBold.ttf`.
It replaces the previous Noto Sans SC resource.

Every standard and emphasized Material typography role uses this one face,
`FontWeight.SemiBold`, zero letter spacing, and `FontSynthesis.None`. There are no
local serif or monospace overrides, including revision hashes and chapter
numbers. Hierarchy comes from size, line height, spacing, and color, not fake
bold or italic. Font sizes and line heights are in `sp` and follow font scaling.

| Role | Size | Line height |
| --- | --- | --- |
| `displayLarge` | 40 | 48 |
| `displayMedium` | 34 | 42 |
| `displaySmall` | 30 | 38 |
| `headlineLarge` | 28 | 36 |
| `headlineMedium` | 24 | 32 |
| `headlineSmall` | 22 | 30 |
| `titleLarge` | 20 | 28 |
| `titleMedium` | 16 | 24 |
| `titleSmall` | 14 | 20 |
| `bodyLarge` | 16 | 26 |
| `bodyMedium` | 14 | 22 |
| `bodySmall` | 12 | 18 |
| `labelLarge` | 14 | 20 |
| `labelMedium` | 12 | 16 |
| `labelSmall` | 11 | 16 |

Emphasized variants retain the corresponding size and line height. App-bar
titles use `titleLarge`, section/card titles use `titleMedium` or `titleSmall`,
descriptions use `bodyMedium`, and long-form reading uses `bodyLarge`. Do not use
display typography as an oversized catalog banner. Permission URLs and source
revisions remain readable and can wrap; card titles and navigation labels may
ellipsize where space is limited.

The font name table reports family `ChillRoundM`, version `1.805`, and internal
style `Regular`. The file name and logical Compose weight do not change the
actual outlines. Only this supplied face is included. Missing characters and
emoji may still use the platform fallback font. Web composition preloads the
resource and font resolver, showing a themed progress indicator until they are
ready. Its initial HTML loading screen may briefly use a fallback while the font
downloads.

Font provenance: [ChillRound](https://github.com/Warren2060/ChillRound), based on
[Zen Maru Gothic](https://github.com/googlefonts/zen-marugothic), licensed under
SIL OFL 1.1. The copyright notices and full license accompany the font in
`evilo-app/src/commonMain/composeResources/files/ChillRound-OFL.txt`.

## Spacing, shapes, and elevation

All dimensions below are in `dp`, except the HTML loading screen's equivalent
CSS pixel values. Use the shared constants instead of introducing page-specific
spacing scales.

| Rule | Values / use |
| --- | --- |
| Spacing scale | 4, 8, 12, 16, 24, 32 |
| Page padding | 16; 24 when the content region is at least 840 wide |
| Grid/card gaps | 12 between plugin rows, 16 between columns; section gaps 24 or 32 |
| Plugin row padding | 16 |
| Settings row padding | 16 |
| Radius scale | 8, 12, 16, 24, 28 |
| Medium shape, 16 | Media cards, chapter rows, permission groups, source input |
| Large shape, 24 | Plugin cards, grouped settings, empty states, selector choices |
| Extra-large shape, 28 | Material dialogs and sheet top corners |
| Pill/circle | Search fields, content-selector FAB, standard Material buttons |
| Outline | 1, using `outlineVariant`; selected choices use `primary` |
| Elevation | Flat cards at 0; content-selector FAB at 6 |
| Icons | 24 viewport/action size, 18 inline size, 12 selector chevron; 2.2 stroke, rounded caps/joins, curved arrow and angular-icon corners |
| Plugin avatar | 48 with medium rounded shape |
| Plugin row action | One 48-touch-target Info action in a 16-radius container; no separate trailing chevron |
| Minimum touch target | 48; use Material controls and adequately padded rows |

Do not give every card a shadow. Borders and surface levels separate content;
floating controls and Material modal scrims communicate elevation. Never encode
selection or a warning only through color: retain labels, checks, or state
semantics.

## Navigation and responsive layout

- Below 600 wide, use a Material `NavigationBar` with Browse and More. Reserve an
  88-wide center slot for the 64-diameter circular content-selector FAB, raised
  24 above the bar. The current media type has a bounded label below the FAB.
- At 600 or wider, use a 96-wide `NavigationRail` with the same destinations and
  selector. The rail can scroll in short windows.
- The selector opens one fully expanded bottom sheet, at most 640 wide, with a
  scrollable two-by-two set of manga, novel, anime, and film/TV choices. Each has
  an icon, visible installed-plugin count after source/NSFW filtering, and an explicit selected state.
- Main content is centered and capped at 1200. Reading and work details are
  capped at 900. Page padding is based on the remaining content width, not the
  platform name.
- Adaptive grids use minimum row widths of 300 for source-catalog and installed
  plugins, 152 for books, and 192 for video catalogs. Settings groups
  keep a 340 minimum and use one or two equal-width columns independently of the
  installed-plugin grid. Work details use a 104-wide cover. Narrow windows keep
  one plugin/settings column.
- Long metadata and button groups wrap. Do not assign fixed heights to titles
  or descriptions. Selector choice cards have a minimum, not fixed, height of 128.

The selector filters installed Browse entries by their installed release's kinds;
source catalogs use the same selected kind and offer their own selector action.
It is not a destination. Phone secondary pages hide the primary bottom bar and FAB;
wide secondary pages may retain the rail, while works/reading pages hide it.
Browse and More are the implemented tabs. Do not add disabled or inert Library,
History, Updates, or Downloads items merely to fill four navigation slots.
`LibraryScreen` remains an unconnected foundation, not an implemented bookshelf.

## Component and interaction rules

- Keep app bars compact. Browse has one Plugin Sources action. Source management
  contains Add and an enabled-source check menu; each source catalog has refresh,
  media selection, and source settings. A connected plugin uses the reader app bar
  with back and close actions.
- Browse starts with a pill local-search field and a collapsed source-filter
  action, followed by source headings and installed rows. It has no All/Installed
  switch, introductory statistics banner, or promotional hero card. Source headings
  span the grid and show their actual visible installed count.
- Compact `PluginRow` uses a 48 avatar, name, up to three compact labels, and one
  independent Info action. The rest of the row opens installed works directly or
  explains its blocking condition. Names can use two lines with enlarged fonts;
  row height is not fixed. Source catalogs and raw installation management reuse
  the row at higher density, with a short description and installation label.
- More lists Settings, Plugin Sources, Safety, and About; it does not repeat the
  installed-plugin list. Settings use rounded category cards and readable groups.
  Simple content switches share a page; source catalogs, source settings, plugin
  details, and raw installation management have their own returnable pages.
- Use grouped settings with a heading, icon, title, description, and one trailing
  control. A switch row includes its text in the clickable/toggleable area.
- Use the shared empty-state card with a real next action. Distinguish no
  installations, sources added without installation, disabled sources, unsupported
  media kind, search/source misses, and NSFW-hidden entries without exposing names.
- Plugin details contain scrollable identity, installation, source-copy, version,
  approved/requested permissions, and actual-source sections. Bottom actions stay
  separate from scrolling content. Browse is the action for an unchanged usable
  installation; updates/downgrades/reinstalls have explicit review labels. Uninstall
  sits in a separate destructive group and names the exact source installation.
- `PluginHomeStore` separates its `HomePage` return stack from one `HomeOverlay`.
  Only temporary selectors and permission/uninstall/source-removal confirmations
  are overlays; closing them retains the initiating page. Approval rechecks the
  exact reviewed release. Keep dialog body text scrollable and bottom actions separate.
- Permission/source panels use the same type, spacing, shapes, and semantic
  colors as normal pages. Keep signing and HTTP warnings explicit.
- Icon-only actions have localized content descriptions. The content-selector
  FAB exposes its current type; selector cards expose their selected state.
- App Bar Back, system Back, and Escape follow the same order: close a confirmation,
  Reader -> work details -> works catalog -> previous home page. Local query/filter
  state and saveable grid/list positions survive returning to their owning page.
- Missing icons use initials; missing covers use a neutral image outline and label.
  Loading covers retain their fixed 2:3 box without a large spinner. Cover errors
  show a compact category and individual actions, with Details retaining retry
  when two 48 targets do not fit. Reader errors retain page numbers and separate
  permission/reopen actions. Original exception text never appears on covers.
- Image details are collapsed by default and copy only redacted category/status,
  origin, limits, and advice. Normal reader images use Fit without cropping text;
  extreme aspect ratios retain the existing 1:32..32:1 layout bound to avoid
  unbounded Compose constraints, while Fit still displays the entire image.
- Transient operation failures and installation notices use one non-modal popup,
  not a card or list item inserted into page content. Phone notifications sit at
  the bottom center of the content region, above primary navigation; wide layouts
  use its bottom end. Width is capped at 480. Notifications use a rounded MD3
  surface, floating elevation, a localized Close action, and optional Browse/Retry.
  Showing or dismissing a notification must not change page padding or list position.
- A notification starts closing after 5 seconds. New notifications replace the old
  one and receive a fresh timeout, including repeated identical messages. Dismissal
  is keyed to the displayed notification so an old timeout cannot close a new one.
  `DismissTransientError` clears only the matching action/connection error; it
  does not clear source-check diagnostics or a plugin-browser connection failure.
  Popup focus and Back/Escape interception stay disabled. Source-check errors,
  image failures, permission blocks, and empty-content Retry states remain persistent;
  hiding a notification must not turn a failed request into a successful empty result.
  Apply IME padding only to the notification anchor, not to the page behind it.
- `EviloMotion` defines 280 ms page/spatial transitions, 180 ms component/effect
  transitions, 120 ms exit fades, and 360 ms slow Material spatial transitions. Use the
  same cubic easing `(0.2, 0, 0, 1)` instead of per-page springs and durations.
  `EviloTheme` supplies this scheme to Material controls and sheets.
- Forward/back navigation uses a 24-distance horizontal slide and fade, reversing
  direction on Back and respecting layout direction. Same-depth destination changes
  fade. Keep full state snapshots and saveable page keys while outgoing content
  finishes its transition; it must not receive pointer, keyboard, or Back actions.
  Clip page slides to the content bounds rather than drawing over navigation.
- Predictive Back seeks the same page transition toward a render-only previous
  snapshot. `EviloBackTransitionState` owns seeking, not the MVI return stack.
  Gesture cancellation restores the current page; completion settles the transition
  and then dispatches the existing Back action. Disable page input during seeking,
  retain saveable page keys and active sessions, and reject a stale gesture after
  another navigation changes its destination. Nested Reader/Details handlers take
  precedence; the catalog delegates to the home stack. The root home destination
  leaves Back unhandled for Android's system back-to-home animation. Overlays
  retain their Material dismissal behavior. Older devices and instant Back/Escape
  keep the same completed-return action without requiring gesture progress.
  Animated app bars and primary navigation retain their system-inset padding
  when hidden, so status and gesture-navigation areas remain reserved.
- Media-type/source-filter content, app-bar titles, selector icons, search-clear
  actions, progress visibility, selection colors, and changing permission/footer
  sizes use shared component specs. Progress indicators overlay the content edge,
  rather than inserting a row that moves the page. Do not add decorative entrance
  motion to every row or badge. Compose specs respect a supplied motion-duration
  scale; the HTML loading spinner stops when the browser requests reduced motion.

These are implementation rules, not a claim that every platform has been built
or visually inspected. Changes to the design system do not implement library,
history, downloads, saved progress, or an in-app video player.
