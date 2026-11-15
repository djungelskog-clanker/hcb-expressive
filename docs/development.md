# Development

## Prerequisites

- JDK 17
- Android SDK with platform 36 (Android Studio installs it)

## Run

Open the project in Android Studio and press Run, or:

```bash
./gradlew installDebug
```

The debug build installs as `lol.djungelskog.hcbx.debug` so it lives next to a release build.

## Checks

```bash
./gradlew lintDebug
./gradlew assembleRelease
```

## Signing releases

Release builds use the debug key unless these environment variables are set:

```
SIGNING_KEYSTORE=/path/to/release.jks
SIGNING_STORE_PASSWORD=...
SIGNING_KEY_ALIAS=...
SIGNING_KEY_PASSWORD=...
```

In CI the keystore comes from the `SIGNING_KEYSTORE_B64` secret (base64 of the .jks file) plus the three secrets above.
Generate one with:

```bash
keytool -genkeypair -keystore release.jks -alias hcbx -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks | gh secret set SIGNING_KEYSTORE_B64
```

## Cutting a release

```bash
git tag v1.0.0 && git push origin v1.0.0
```

The `build` workflow attaches `hcb-expressive.apk` to the GitHub release.

## Tips

- The HCB API is documented by the Rails app itself: `app/views/api/v4` in [hackclub/hcb](https://github.com/hackclub/hcb) shows the exact JSON shapes.
- Add `?expand=...` parameters to include nested objects (`organization`, `user`, `balance_cents`, ...).
- List endpoints paginate with `limit` and `after=<last id>`; responses contain `data` and `has_more`.
