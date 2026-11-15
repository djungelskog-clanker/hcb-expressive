<div align="center">

# HCB Expressive

**An unofficial, Material 3 Expressive Android client for [HCB](https://hackclub.com/bank/), the fiscal sponsorship platform by Hack Club.**

[![build](https://github.com/djungelskog-clanker/hcb-expressive/actions/workflows/build.yml/badge.svg)](https://github.com/djungelskog-clanker/hcb-expressive/actions/workflows/build.yml)
[![lint](https://github.com/djungelskog-clanker/hcb-expressive/actions/workflows/lint.yml/badge.svg)](https://github.com/djungelskog-clanker/hcb-expressive/actions/workflows/lint.yml)

</div>

HCB Expressive is a from-scratch native client (Kotlin + Jetpack Compose) for the public HCB v4 API.
It covers everything the official [hcb-mobile](https://github.com/hackclub/hcb-mobile) app does on Android,
with a Material 3 Expressive interface (dynamic color, cookie and clover shapes, expressive motion, loading indicators)
plus a few extras. It is **not affiliated with or endorsed by Hack Club**.

## Download

Grab the latest APK from the [Releases page](https://github.com/djungelskog-clanker/hcb-expressive/releases/latest)
or from the `hcb-expressive-apk` artifact of the newest [build run](https://github.com/djungelskog-clanker/hcb-expressive/actions/workflows/build.yml).
Enable "install unknown apps" for your browser or file manager, open the APK, and sign in with your HCB account.
Android 8.0 (API 26) or newer.

## Features

| Area | What you can do |
| --- | --- |
| **Home** | Balance hero with a scrubbable balance chart, org switcher, privacy mode, quick actions, banners for invitations / missing receipts / playground mode, recent activity |
| **Activity** | Infinite list grouped by day, instant search, type chips, filters (direction, tag, missing receipts, date range) |
| **Transactions** | Type-aware detail (card charge, donation, check, HCB / ACH / wire / Wise transfer, invoice, check deposit, reimbursement), rename with suggestions, tags, receipts (camera, gallery, PDF), mark "no receipt", comments, share and open on web |
| **Receipts** | "Needs a receipt" queue across all your cards, receipt bin, share a photo or PDF straight into the app |
| **Cards** | Personal and organization cards with card art, freeze / unfreeze, activate physical cards, cancel, per-card transactions, order virtual or physical cards with designs |
| **Card grants** | List, create with merchant / category / keyword locks, expiry, one-time use and pre-authorization, top up, withdraw, cancel, grant spending history |
| **Transfers** | ACH, mailed check, international wire (19 currencies) and HCB-to-HCB transfers, each with attachments |
| **Donations** | Totals, status filters, donation details, copy / share the donation page, QR code |
| **Invoices** | List, create (with inline sponsor creation) |
| **Check deposits** | Photograph front and back, track status |
| **Team** | Members with roles, invite (reader / member / manager), cancel invites, request removal, accept / decline invitations |
| **Tags & sub-orgs** | Create / delete tags, create and switch into sub-organizations |
| **Account numbers** | Copy account, routing and SWIFT numbers |
| **Reimbursements** | Reports and expenses, submit for review, move back to draft |
| **Settings** | Light / dark / system theme, dynamic color toggle, hide balances, biometric app lock, block screenshots, sign out (revokes token) |

### Extras that are not in the official app

- Scrubbable balance chart on the home screen
- Biometric / device-credential app lock and optional screenshot blocking
- Privacy mode that masks every amount
- Offline mode: every GET is cached on disk, so the last data you saw still loads without a connection
- Donation QR code
- Share-to-app receipt upload (share an image or PDF from any app)
- Deep links for `hcb.hackclub.com/hcb/...` transaction links

### Not included (and why)

- **Revealing full card numbers, Add to Google Wallet** need a Stripe "trusted" OAuth client; HCB only allows the official app to use those endpoints.
- **Tap to Pay donations** use the Stripe Terminal SDK with credentials that belong to the official app.
- **Push notifications** need a Firebase project registered to the app.

## Sign-in and security

Authentication is OAuth 2.0 authorization code with PKCE (S256) against `hcb.hackclub.com`, opened in a Custom Tab.
Access and refresh tokens are encrypted with an AES-GCM key held in the Android Keystore. Tokens are never logged.
Out of the box the app reuses the public OAuth client id of the official mobile app together with its `hcb://` redirect,
so no registration is needed. See [docs/auth.md](docs/auth.md) if you want to use your own OAuth application.

## Build from source

```bash
git clone https://github.com/djungelskog-clanker/hcb-expressive
cd hcb-expressive
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease      # signed with your keystore if SIGNING_* env vars are set, else the debug key
```

Requirements: JDK 17 and the Android SDK (platform 36). Android Studio opens the project as is.
More detail in [docs/development.md](docs/development.md) and [docs/architecture.md](docs/architecture.md).

## CI

- `build.yml` builds a release APK on every push and pull request, uploads it as an artifact, and attaches it to a GitHub release when you push a `v*` tag.
- `lint.yml` runs Android lint.

To sign release builds with a stable key (so updates install over older versions) add these repository secrets:
`SIGNING_KEYSTORE_B64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT, see [LICENSE](LICENSE). HCB and Hack Club are trademarks of their respective owners.
