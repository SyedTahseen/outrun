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

## Pass 3: checked against proton-core source (`presentation-compose`), still uncompiled
Verified in `Colors.kt`/`Typography.kt`/`Shape.kt`/`Dimension.kt`/`ProtonButton.kt`/`ProtonOutlinedTextField.kt` of proton-core:
| Area | Before | Now | Source |
|---|---|---|---|
| Card radius | 16dp | 12dp | `ProtonShapes.large` = `ExtraLargeCornerRadius` 12dp (small 4, medium 8) |
| Border / divider | 3A3944 | 3B3747 | dark `separatorNorm` = shade20 |
| Control (secondary button, chip) | 3B3746 | 3B3747 | dark `interactionWeakNorm` = shade20 |
| Land active | 4A4657 | 4A4658 | shade40 |
| Letter spacing | 0 everywhere | body1/caption 0.03em, body2 0.02em, hero 0.45sp | `ProtonTypography` defaults |
| Line heights | body 22 | body 24, body2 20, caption 16 | `ProtonTypography` defaults |
| Text field focus border + cursor | Accent 8A6EFF | Primary 6D4AFF | `focusedBorderColor = brandNorm` |
| Text button | 8dp shape, 12dp pad | 4dp shape (`shapes.small`), 8dp pad | `ProtonTextButton` |
| Outline (accent) button | transparent, Primary text | Bg fill, Accent text, Primary border | `protonOutlinedButtonColors` |
| Icons | Material | Proton `ic_proton_*` set (46 icons, `ProtonIcons.kt`) | proton-core `presentation` res |
New token: `C.Disabled` (5B576B, textDisabled).

Not changed (proton-core and the VPN app disagree or core is silent): dialog shape (core `ProtonAlertDialog` = 8dp on Bg, VPN's own alert = M3 28dp), bottom-sheet shape (core 8dp top corners), disabled button colours (core uses pale `brandLighten40`), Title 22 / Subtitle 18 sizes (no core equivalent), Material icons without a Proton twin (`ContentPaste`, `WifiOff`).

## Pass 4: checked against the Proton VPN app source (`android-app-master`), still uncompiled
Corrections to earlier "verified" entries (the tv* resources were Android-TV only; the phone app uses `ProtonColors.Dark`):
| Area | Before | Now | Source |
|---|---|---|---|
| Error / destructive colour | DC3251 | F08FA4 | `notificationError` (dark) used by VpnStatusView, ProtonSnackbar |
| Warning colour | FF9900 | FFB84D | `notificationWarning` (dark) |
| Switch unchecked track + border | 6D697D | 5B576B (`C.Disabled`) | ProtonSwitch: shade50 (6D697D is shade60) |
| Outline (accent) button border | 6D4AFF | 8A6EFF | `protonOutlinedBorder` = textAccent |
| Disabled solid buttons | 40% alpha | fill 372580, label white @ 50% | `protonButtonColors` = interactionDisabled |
| Disabled outline buttons | 40% alpha | border + label 372580 (neutral: 5B576B) | `protonOutlinedButtonColors` |
| Button label | 16 Medium | 16 Regular (body1Regular), 24dp horizontal padding in both sizes | ProtonButton `style = defaultUnspecified`, ProtonContentPadding(Compact) |
| "Protected" status | 30 Bold | 20 Bold (headline) | VpnConnectedViewTop `headlineNorm` |
| Status icons | outlined lock / lock-open | filled `lock_filled` / `lock_open_filled_2` | VpnStatusView |
| Dialog title | 18 SemiBold | 20 Bold (headline) | ProtonAlert `headlineNorm` |
Confirmed correct: card radius 12 (`shapes.large` in VpnStatusView / VpnConnectionCard), card border = separatorNorm, dialog shape 28 + `backgroundSecondary` fill, sheet fill `backgroundNorm` + `iconHint` handle, top bar scrolled fill `backgroundSecondary`, text-field focus border `interactionNorm`, section header 14 Medium accent 24/8, settings row 16dp vertical + 4dp gap, snackbar 12dp margin + `shapes.medium`.
Still open: Medium/Expanded window padding (56/128dp), selected/unselected bottom-bar icon variants (Proton `getIcon(isSelected)`), dialog body text (14sp body2Regular) must be set by each caller, widget/launcher colours, light theme.

## Pass 5: motion
Proton VPN has no custom screen transitions: tabs and settings pages use Navigation Compose's default (`NavigationTransition.DefaultFade`, fade in/out 700 ms; only profile creation slides). Outrun's `axisTransition` (300 ms slide + fade) now cross-fades for 700 ms. Press feedback = ripple only and no haptics were already matched. Status gradient 500 ms matched earlier.
Not matched: Outrun's `statusTransition` (slide + scale + fade on the home status) vs Proton's 500 ms alpha only on Connecting -> Connected and snap for everything else; card/recents slide, 200 ms fades with 600/1100/1300 ms delays in VpnConnectionCard, M3 scroll-linked collapsing top bar (Outrun animates a scrolled flag).

## Pass 6: bottom bar icons
Proton `BottomBarView.getIcon(selected)`: outline icon when idle, filled when selected (house, earth, servers, cog wheel). Outrun used filled Material icons for every tab all the time. Now: Home = house, Servers = earth, Settings = cog wheel, each outline/filled by selection. Speed has no Proton counterpart: bolt, same glyph in both states. Proton's tab set (Home, Countries, Profiles, Settings) differs from Outrun's by design.

## Pass 7: buttons and text fields, checked against `android-app-master/base/ui/ProtonButton.kt`, `VpnButton.kt` and proton-core `ProtonOutlinedTextField.kt`
| Area | Before | Now | Source |
|---|---|---|---|
| Large button padding | 24dp horizontal | 16dp (Compact stays 24dp) | `ProtonContentPadding` 16x12 / `ProtonContentPaddingCompact` 24x12 |
| Secondary button, disabled | 372580 fill, white 50% label | Card fill (`interactionWeakDisabled`), `C.Disabled` label | `protonSecondaryButtonColors` |
| Outline buttons, fill | transparent (neutral) | `C.Bg` (`backgroundNorm`), also on cards | `protonOutlined(Neutral)ButtonColors` |
| Text button padding | 8dp horizontal | 12dp (Material 3 `TextButtonContentPadding`; the VPN app uses material3) | `ProtonTextButton` |
| Text field focus border | 1dp | 2dp when focused or in error, 1dp otherwise | Material outlined text field |
| Text field error | none | `isError` param: `C.Red` border | `errorBorderColor = notificationError` |
| Widget gradient / hairline | F0728C, FFB74D, 3A3944 | F08FA4, FFB84D, 3B3747 | synced to `C.Red`, `C.Amber`, `C.Border` |

Confirmed correct, no change: all `ProtonColors.Dark` values in `Colors.kt`, radii 4/8/12, 48dp button min height, outline-accent border = `textAccent` in the VPN app (proton-core's own version uses `brandNorm`, but the VPN app overrides it), dialog button style `body1Medium`.
Corrects an earlier note: the "Secondary" button in the VPN app is `VpnWeakSolidButton` (48dp, fill-width), not `ProtonSecondaryButton` (32dp, caption text).
Not changed: Outrun's own brand art (launcher icon, widget globe, world map shape), which has no Proton counterpart.

## Pass 8: snackbar, tablet padding, map colours, light theme (still uncompiled)
| Area | Before | Now | Source |
|---|---|---|---|
| Messages | system `Toast` everywhere | `Snack.show(msg, SnackType)` + `AppSnackbarHost` above the bottom bar: solid status fill (success 4AB89A / warning FFB84D / error F08FA4 / norm white), `textInverted` 14sp text, 8dp shape, 12dp margin, Short duration | `ProtonSnackbar.kt` |
| Tablet padding | centred, capped at 800dp | plus 56dp (Medium, 600-839dp) / 128dp (Expanded) side padding, `largeScreenContentPadding()` | `base/ui/Dimensions.kt` |
| Map border | 5A5866 | 5B576B (shade50) | `HomeMap.kt` MapConfig (the phone app does not use the `tvMap*` colours) |
| Connecting pin / tone | A6A3B2 | A7A4B5 (`iconWeak`) | `HomeMap.kt` pinColorConfig |
| Light theme | dark only | follows the system: `Palette.dark` drives every `C.*` colour; light values from `ProtonColors.Light` (Carrara cards, white bg, Cinder text, brand 6D4AFF text, Pomegranate / Sunglow / Apple) and `vpnGreen` 1C9C7C | `Colors.kt` (proton-core), `VpnColors.kt` |
| Status overlay (light) | 50% always | connected 20%, disconnected 24%, connecting white 50% | `VpnStatusView.getOverlayGradientTop` |
| Translucent card border | white 8% -> 2% | `shade100` 8% -> 2% (white on dark, black on light) | `VpnStatusView` |
| Ripple | forced white | default (content colour) | invisible on light otherwise |
| System bars, window bg, Material theme | dark only | `SystemBarStyle.auto`, `values` / `values-night` colours and themes, `lightColorScheme` in light | |
| Widget | shared colours | fixed dark colours (`widget_*`), because its art is always dark | |

Derived, not from Proton (no light equivalent exists in either repo): `Outline`, `Track`, `TextDim`, `AccentMid`/`AccentDeep` tile tints and `Divider` in light mode. Review them on a device.

### Genuinely not applicable, so not "fixed"
- **Bottom sheets**: Outrun has none (it uses dialogs and dropdown menus). When one is added: container `C.Bg`, handle `C.Shade50`, top corners `Radius.Control` (proton-core `shapes.bottomSheet` is 8dp top).
- **ServerLoadBar**: needs per-server load data that Outrun's own servers do not have.
- **Text-field labels**: Outrun's fields use placeholders only; error border is done (`isError`).
- **Own design with no Proton counterpart**: speed gauge, HeroIcon tinted tiles, globe/map art, launcher icon, widget art, Speed tab.
- `ScanActivity` still uses a system `Toast` for "No QR code found in image" (separate camera activity).

## Pass 10: Home / Servers / Settings rows / bottom bar, checked against `android-app-master` (still uncompiled)
Verified unchanged: ProtonTypography, ProtonShapes, ProtonDimens, all 55 `ic_proton_*` path data (byte-identical), system sans-serif (Proton ships no font).
| Area | Before | Now | Source |
|---|---|---|---|
| Status overlay | 50% of screen height | fixed 200dp | `Home.kt` Spacer height(200.dp) |
| Status block position | 40dp spacer, no content padding | directly under status bar, 8dp padding (connected row), max width 480dp | `VpnStatusTop`, `VpnStatusBottom` |
| Bottom area | gradient over 40% of screen | 100dp gradient then solid Bg, first item 68dp below top | `Home.kt` RecentsList drawBehind, `ListBgGradientOffsetBasic` |
| Card caption | 16sp, 4dp start inset | 14sp, 24dp vertical, no inset | `VpnConnectionCard` ContainerLabelRow |
| Flag | 36x24 badge | 30x20 flag, 4dp corners, in 30x30 area | `ConnectIntentIcon` Dimensions |
| Card info icon | chevron_right | chevron_up, iconWeak, top aligned | `OpenPanelButton` |
| Sublabel | no gap | 2dp top padding, row top-aligned | `ConnectIntentLabels` |
| Strings | "Connecting to...", "  ·  " | "Connecting to…", " · " | `strings.xml` |
| Connecting text | all textNorm, 900 ms | textWeak + place in textNorm, 50 ms/char, keeps . space - | `AnimateText` |
| Status transition | slide + scale + fade | 500 ms fade for Connecting -> Connected, snap otherwise | `rememberVpnStateAnimationProgress` |
| Servers header | 28sp title in 64dp row | collapsing bar: actions on top row, 28sp title at bottom of 112dp | `CollapsibleToolbarScaffold` |
| Filter chips | 16sp Medium, 20dp pad, unselected text always white | 14sp, 16dp pad, unselected textNorm (was invisible in light theme), row padding 8 | `FiltersRow` |
| Count header | 16sp, pinned, 20dp gap | 14sp weak, top 16 + 8, scrolls with list | `ServerGroupHeader` |
| Server row | 36dp flag + 16dp gap, vertical dots | 30dp flag area + 12dp gap, text column vertical 12, icon 16dp from edge, horizontal dots | `ServerGroupItem`, `ServerGroupItemRow` |
| Settings row | 24dp icon + 16dp gap, 14sp subtitle, 4dp gap, min 64dp | icon in 32dp box + 16dp, 16sp subtitle, 6dp gap, 56dp natural height, 8dp rounded ripple, trailing icon end 8dp | `SettingRow`, `SettingRowWithIcon` |
| Bottom bar | hand-built dock | stock Material 3 `NavigationBar` / `NavigationBarItem` configured as `BottomBarView` | `BottomBarView.kt` |

## Pass 11: dividers and map pin (still uncompiled)
| Area | Before | Now | Source |
|---|---|---|---|
| Dividers (server list, statistics, usage, menus) | `Box.height(Dp.Hairline)` = 0px, so every divider was invisible | `HorizontalDivider(thickness = Dp.Hairline, color = C.Border)` (1px) | `VpnDivider.kt` |
| Map location marker | white disc + vector halo + stroked pulse ring | white 12dp disc, 6dp coloured core, `map_pin_outer_{disconnected,protected}` glow bitmaps (copied from Proton res, all densities + night) pulsing 96dp <-> 64dp over 3s, show-in delays 0.2s / 0.7s, no glow while connecting, respects the system animator-duration scale | `MapView.kt` drawPins |
