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

## Pass 2 (also done, still uncompiled)
- **Top bars**: sub-pages = 64dp bar, 48dp back button 4dp from the edge, 16sp Medium title (SimpleTopAppBar). Top-level screens = 112dp expanded bar with a 28sp bottom-left title that collapses to 64dp / 16sp and Bg -> Card when `scrolled` (CollapsibleToolbarScaffold). The old 20dp spacer is gone. Note: Outrun animates on a scrolled flag, not M3's continuous scroll-linked collapse.
- **Home**: status overlay gradient = green / white / red at 50% over 500ms; status texts are body1-strong, not 26/22sp headings; 32dp status icon + 2dp spinner; location chip = Card @ 86% with Proton's translucent 8%->2% border, 8x4 padding; connected stats card has the same border; card row min 42dp, 16dp icon gap, 18sp Medium server name; 8dp between buttons.
- **Server list**: rows are heightIn(min 64) with 12dp vertical padding, 4dp subtitle gap, no row highlight; the selected server gets Proton's pulsing `ActiveDot` after its name.
- **Tablet**: non-Home pages are centred and capped at 800dp. Not done: the extra 56/128dp side padding for Medium/Expanded window classes.

## Still open
1. **Compile and run** (`./gradlew assembleDebug`); not possible offline here.
2. Proton `ServerLoadBar` (36x4dp, green <=75%, amber <=90%, red above): Outrun has no load data, so nothing to show.
3. Typography mapping to proton-core `hero/headline/subheadline/body1/body2/caption/overline`; "defaultStrong" is approximated with 16sp Medium; Display 30 ("Protected") still Outrun's own.
4. Bottom sheets (Bg fill, 6D697D drag handle), text-field label/error states, snackbars (ProtonSnackbar: shapes.medium, solid status fills, 12dp margin).
5. Proton icon set (outline/filled nav icons), flag assets vs the 36x24 badge.
6. HeroIcon gradient tiles, speed-gauge blue, world-map colours (tvMapCountry 3F3B4C, tvMapSelected 6D697D, tvMapConnected = accent).
7. Widget / launcher gradients (`res/drawable/widget_*.xml`, OutrunWidget.kt) still use the old red/amber.
8. Light theme (Outrun is dark-only).
9. proton-core theme source, to confirm the "inferred" values flagged above.
