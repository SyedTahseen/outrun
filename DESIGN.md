# Outrun VPN design system

All UI lives in `app/src/main/java/app/xvpn/ui`. Screens contain **no** color literals, font sizes, corner radii, durations or ad-hoc dp spacing: they compose the pieces below.

| File | What it owns |
|---|---|
| `Colors.kt` | `C`: the only palette. Surfaces `Bg → Card → Control`, `Border`/`Outline`, `Primary` (fills) vs `Accent` (text/icons), status colors. |
| `Tokens.kt` | `Space` (4dp grid + `Gutter` = 16dp page margin, Proton's screen_padding_horizontal), `Radius`, `Sizes`, `Elevation`, `Type` (type scale). |
| `Motion.kt` | `Motion` durations/easings, `Press` scales, `pressable`, haptics, shared screen transitions. |
| `Theme.kt` | `OutrunApp` / `OutrunTheme` (Material theme from the tokens), edge-to-edge, `ReportScrolled`. |
| `Controls.kt` | `IconAction`, `AppButton` (Primary/Secondary/Outline × Large/Compact), `TextAction`, `CardAction`, `SelectChip`/`Segmented`/`SelectableBox`, `AppSwitch`, `AppRadio`, `AppTextField`. |
| `Components.kt` | `AppCard`, `HairlineDivider`, `ScreenHeader`/`HeaderSurface`/`HeaderBar`, `SectionHeader`, `FieldLabel`, `ListRow`, `ChoiceRow`, `DetailCard`, `SwitchCard`, `HeroIcon`, `PageHeading`, `AppDialog`, `AppDropdownMenu` + items, `AppProgressBar`, `FlagBadge`, `EmptyState`. |

## Rules
- **Spacing**: only `Space.*`. Every screen has a 16dp `Gutter`.
- **Type**: `Display 30 · Headline 28 · Title 22 · Subtitle 18 · Body 16 · Small 14 · Caption/Label 12`. Screen title = Headline, sub-page title = Title, section heading = `SectionHeader` (accent), form label = `FieldLabel` (muted).
- **Shapes** (Proton `ProtonTheme.shapes`): buttons/chips/inputs/menus 8 · cards 16 · dialogs/sheets 28 (Material default) · flags 4. Cards are flat `Card` fills (the selected-server card on Home adds a 1dp `Border`, like Proton's connection card); bars (header strip, dock) are flat `Card`; menus use the Material default 3dp shadow.
- **Feedback**: Material ripple only, like Proton. `Press.*` scale tokens and `rememberTick` haptics are kept but neutralised (1f / no-op). Color changes animate.
- **Reference**: values tagged `[PV]` in `Colors.kt` come from the Proton VPN source. See `PROTON_AUDIT.md` for the full audit and what is still open.
- **Add a screen**: `ScreenHeader` → content using the components above. If you need a new color/size/style, add a token first.

## Spark theme
A fourth theme (`AppTheme.SPARK`, Settings → Theme → Spark) always renders dark and monochrome, matching the reference screenshot (neutral `#18191B` page, `#2A2B2D` cards, white pill buttons with dark text, 24dp cards, 16dp controls, 32dp sheets).
- `Palette.spark` switches every token in `Colors.kt`/`Tokens.kt`; the other themes are unchanged.
- `Glass.kt` owns the frosted glass: `AmbientBackdrop` (warm/cool colour glows) and, on Home, the map are recorded into `GraphicsLayer`s via `backdropSource`; `Modifier.glass(shape)` draws a 24dp-blurred crop of those layers (Android 12+), a white 11%→7% gradient fill and a 1dp gradient hairline. Below Android 12 it falls back to fill + hairline.
- `AppCard`, the dock, Home's status chip and settings cards use glass in Spark. Dialogs use an opaque card with the glass edge (they live in their own window).
