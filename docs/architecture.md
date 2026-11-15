# Architecture

```
app/src/main/java/lol/djungelskog/hcbx
├── HcbApp.kt            Application: initialises settings, auth, API
├── MainActivity.kt      Edge-to-edge host, OAuth redirect + deep link + share intents, biometric lock
├── data/
│   ├── J.kt             Null-safe JSON wrapper (the API is loosely typed, so we read it tolerantly)
│   ├── Api.kt           OkHttp client: bearer auth, 401 refresh, JSON + multipart, offline GET cache
│   ├── Auth.kt          OAuth 2 + PKCE, token refresh, revoke
│   ├── SecureStore.kt   AES-GCM string store backed by the Android Keystore
│   └── Settings.kt      Observable preferences (theme, privacy, lock)
└── ui/
    ├── AppRoot.kt       Login / bootstrap / lock gate, bottom navigation, NavHost
    ├── Routes.kt        Secondary routes
    ├── AppState.kt      Session state: user, organizations, selected organization, badges
    ├── Components.kt    Loader / Paged helpers, scaffolds, rows, fields, dialogs
    ├── TxnUi.kt         Transaction row, icons, tag chips
    ├── Pickers.kt       Camera / gallery / file picker and date field
    ├── theme/Theme.kt   MaterialExpressiveTheme, expressive motion, typography, shapes
    └── screens/         One file per feature area
```

## Data flow

Screens ask `rememberLoader { Api.get(...) }` for a single resource or `rememberPaged { after -> Api.get(...) }`
for cursor-paginated lists. Both expose loading / error / refreshing state and a `reload()`.
Mutations run through `rememberAction()`, which handles the busy flag and shows API error messages in a snackbar.

`Api` always returns a `J`. Reading a missing field yields an empty value instead of throwing, which keeps the app working
when HCB adds, renames or removes fields.

## Organization context

`AppState.orgId` is the globally selected organization. Switching it (home screen → switcher, or entering a sub-organization)
re-keys every loader that depends on it. The last selected organization is remembered.

## Offline

Every successful GET response is written to `cacheDir/api` keyed by URL. If a later request fails with an `IOException`
the cached body is returned and `Api.offline` flips to true. The cache is cleared on sign-out.

## Theming

`HcbTheme` wraps `MaterialExpressiveTheme` with `MotionScheme.expressive()`. On Android 12+ the color scheme comes from the wallpaper;
older devices (or users who turn dynamic color off) get a palette built around the HCB red.
Decorative shapes use `MaterialShapes` (cookie and clover polygons).
