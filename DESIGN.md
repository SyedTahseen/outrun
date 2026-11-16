# Outrun VPN design system

All UI lives in `app/src/main/java/app/xvpn/ui`. Screens contain **no** color literals, font sizes, corner radii, durations or ad-hoc dp spacing: they compose the pieces below.

| File | What it owns |
|---|---|
| `Colors.kt` | `C`: the only palette. Surfaces `Bg → Card → Control`, `Border`/`Outline`, `Primary` (fills) vs `Accent` (text/icons), status colors. |
| `Tokens.kt` | `Space` (4dp grid + `Gutter` = 20dp page margin), `Radius`, `Sizes`, `Elevation`, `Type` (type scale). |
| `Motion.kt` | `Motion` durations/easings, `Press` scales, `pressable`, haptics, shared screen transitions. |
| `Theme.kt` | `OutrunApp` / `OutrunTheme` (Material theme from the tokens), edge-to-edge, `ReportScrolled`. |
| `Controls.kt` | `IconAction`, `AppButton` (Primary/Secondary/Outline × Large/Compact), `TextAction`, `CardAction`, `SelectChip`/`Segmented`/`SelectableBox`, `AppSwitch`, `AppRadio`, `AppTextField`. |
| `Components.kt` | `AppCard`, `HairlineDivider`, `ScreenHeader`/`HeaderSurface`/`HeaderBar`, `SectionHeader`, `FieldLabel`, `ListRow`, `ChoiceRow`, `DetailCard`, `SwitchCard`, `HeroIcon`, `PageHeading`, `AppDialog`, `AppDropdownMenu` + items, `AppProgressBar`, `FlagBadge`, `EmptyState`. |

## Rules
- **Spacing**: only `Space.*`. Every screen has a 20dp `Gutter`.
- **Type**: `Display 30 · Headline 26 · Title 22 · Subtitle 18 · Body 16 · Small 14 · Caption/Label 12`. Screen title = Headline, sub-page title = Title, section heading = `SectionHeader` (accent), form label = `FieldLabel` (muted).
- **Shapes**: buttons/chips/inputs 12 · cards 16 · dialogs/sheets 24 · flags 4. Cards are flat with a 1dp `Border`; bars (header strip, dock) are flat `Card`; only menus float with a shadow.
- **Feedback**: buttons, chips, icon buttons and cards ripple and shrink on press (`Press.*`); full-width rows only ripple. Light haptic on selection, strong haptic on primary actions. Color changes always animate.
- **Add a screen**: `ScreenHeader` → content using the components above. If you need a new color/size/style, add a token first.
