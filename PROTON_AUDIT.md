# Outrun vs Proton VPN: UI audit and hand-off

Reference: Proton VPN Android (`android-app-master`). Proton's base palette and type scale (`ProtonTheme.colors/typography/shapes`)
live in the external `me.proton.core:compose` library, which is NOT in that repo. Everything below is therefore split into:
- **Verified**: read directly from the Proton repo (tv* colors, VpnColors.kt, VpnStatusView.kt, BottomBarView.kt, SettingsItem.kt,
  ProtonAlert.kt, ProtonDropdownMenu.kt, ProtonTextField.kt, ProtonSwitch/Radio.kt, VpnDivider.kt, CollapsibleToolbarScaffold.kt,
  ConnectIntentRow.kt, VpnConnectionCard.kt, dimens.xml).
- **Inferred**: standard values of proton-core / Material 3 (flagged "inferred"); confirm against a proton-core checkout if exactness matters.

## Done in this pass (shared tokens + components, so every screen inherits it)

| Area | Outrun before | Now (Proton) | Basis |
|---|---|---|---|
| Bg / Card | 1C1B23 / 292734 | 1C1B24 / 292733 | verified (tvBackground, tvCardBackground) |
| Map overlay card | Card @ 92% | Card @ 86% | verified (VpnStatusView) |
| Accent text/icons | 6D4AFF (same as fill) | 8A6EFF; fills stay 6D4AFF | verified (tvAccentLighten / tvAccent) |
| Nav indicator | solid 4A3F8A | accent @ 32% over bar | verified (BottomBarView) |
| Text / muted | F2F2F7 / 9A98A8 | FFFFFF / A7A4B5; hint/placeholder 6D697D | muted, hint verified (tvInMaintenance, tvButtonBg); white inferred |
| Green / red / amber | 2DF5C0 / F0728C / FFB74D | 2CFFCC / DC3251 / FF9900 | verified (vpnGreen, tvAlert, tvServerLoadMedium) |
| Page gutter | 20dp | 16dp | verified (screen_padding_horizontal) |
| Radii | 9dp everywhere | controls 8, cards 16, dialogs/sheets 28, flags 4 | shapes.medium/large used throughout repo; 28 = M3 AlertDialog default (ProtonAlert uses it) |
| Bottom bar | 72dp, 56x32 pill, icon bounce 1.1x | 80dp, 64x32 pill, no bounce | verified (M3 NavigationBar, no overrides) |
| Section heading | 14 SemiBold, first=12dp top | 14 Medium, always 24 top / 8 bottom | verified (SettingsSectionHeading) |
| Settings row | 12dp vertical, no subtitle gap | 16dp vertical, 4dp subtitle gap, min 64dp | verified (SettingsItemScaffold) |
| Screen title | 26sp | 28sp | verified (CollapsibleToolbarTitle expanded size) |
| Dividers | 1dp, 4% white | hairline, Border colour | verified (VpnDivider) |
| Dialog | pad 20, gaps 12 | pad 24, title gap 16, button gap 24 | M3 AlertDialog defaults (inferred) |
| Menu | 9dp, 8dp shadow | 8dp radius, 3dp shadow | verified radius; shadow = M3 default |
| Switch | track Accent / Border grey | checked Primary; unchecked track+border 6D697D, thumb A7A4B5 | verified (ProtonSwitch) |
| Radio | custom spring dot, accent/muted | stock M3 RadioButton, selected 6D4AFF, unselected 6D697D | verified (ProtonRadio) |
| Outline button | grey 4F4D5C border; accent 1.5dp | 1dp white / 1dp primary | inferred from ProtonOutlinedNeutral/Outlined |
| Button / control / field / row heights | 56 / 44 / 52 / 56 | 48 / 48 / 56 / 64 | inferred (Proton min height 48) + verified row 64 |
| Button label | 18 Medium | 16 Medium | inferred |
| Press feedback | scale 0.88-0.985 + ripple | ripple only | verified (no scale effects in Proton) |
| Haptics | tick on every select/primary | none | verified (Proton only ticks in custom-DNS reorder) |
| Density | forced 360dp-wide scaling, fontScale=1 | real dp, system font scale | Proton uses native dp |
| res/values/colors.xml | old palette | synced | |

## NOT done (hand-off to the next pass)
1. **Compile and run.** No Gradle dependencies / network here: nothing was built. First step: `./gradlew assembleDebug`, then walk every screen.
2. **Top app bar**: Proton uses M3 `MediumTopAppBar` (collapsing 28sp -> 16sp title, bg Bg -> Card on scroll). Outrun has a fixed 20dp spacer + 48dp row (`HeaderBar` in Components.kt). Rebuild `ScreenHeader` to match the 112dp/64dp heights and collapse behaviour.
3. **Home screen** (HomeScreen.kt, WorldMap.kt): compare against `VpnConnectionCard.kt` / `VpnStatusView.kt` (card padding 16, border 1dp separatorNorm, shapes.large, status icon 32dp, status text `headline` in vpnGreen, 16dp vertical padding between map and card). Display 30sp status text and the connect-button layout are unverified.
4. **Server list** (ServersScreen.kt): Proton `ConnectIntentBlankRow` = min 64dp, vertical 12, horizontal 16, text gap 16, title body1Regular, subtitle body2Regular with 4dp top, "…" overflow button 8dp padded 24dp icon, `ActiveDot` for connected. Flag badge 36x24 vs Proton flag assets; server-load bar (`ServerLoadBar.kt`).
5. **Typography**: Display 30 / Title 22 / Subtitle 18 are Outrun's own sizes; map them to proton-core `hero / headline / subheadline / body1 / body2 / caption / overline` once proton-core is available. Dialog title = `headlineNorm`, dialog buttons `headlineSmall`.
6. **Bottom sheets**: Proton `SimpleModalBottomSheet` = containerColor Bg, M3 drag handle in 6D697D, expanded only. Check Outrun sheets in MenuScreen/ServersScreen.
7. **Text field**: Proton uses `ProtonOutlineDecorationBox` (label above, assistive/error text with `notificationError` border, 16dp padding). Outrun field has no label/error state.
8. **Snackbars/toasts**: Proton `ProtonSnackbar` (shapes.medium, solid success/warning/error fills, 12dp margin, textInverted).
9. **Icons**: Proton uses its own `ic_proton_*` set (outline vs filled for selected nav); Outrun uses Material icons. Needs asset import (check licence: Proton icons are GPL with the app).
10. **HeroIcon tiles / AccentMid / AccentDeep gradient, speed gauge Blue, world-map colours** (tvMapCountry 3F3B4C, tvMapSelected 6D697D, tvMapConnected = accent): no Proton equivalent for the tiles; map colours can be aligned.
11. **Widget + launcher** (`res/drawable/widget_*.xml`, OutrunWidget.kt): still use F0728C/FFB74D gradient stops; Proton widget gradients are in its colors.xml (`protonWidget*`).
12. **Tablet/landscape**: Proton caps content at 800dp (`MaxContentWidth`) with extra padding on Medium/Expanded window classes (Dimensions.kt). Not implemented.
13. **Light theme**: Proton supports light/dark; Outrun is dark-only (kept).
