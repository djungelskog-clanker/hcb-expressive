# Authentication

HCB exposes an OAuth 2 provider (Doorkeeper) under `/api/v4/oauth`.

1. `Auth.start` builds an authorize URL with a random `state` and a PKCE S256 challenge, and opens it in a Custom Tab.
2. After the user approves, HCB redirects to `<scheme>://?code=...&state=...`. `MainActivity` has an intent filter for that scheme.
3. `Auth.handleRedirect` verifies `state`, then POSTs the code and verifier to `/api/v4/oauth/token`.
4. Tokens are encrypted (AES-GCM, Keystore key) and stored. `Auth.validToken` refreshes shortly before expiry and `Api` retries once on a 401.
5. Sign out revokes the token at `/api/v4/oauth/revoke` and wipes local data and the offline cache.

## Using your own OAuth application

The default build reuses the official app's public client id and `hcb://` redirect so it works without registration.
If both apps are installed, Android may ask which one should open the `hcb://` redirect: pick HCB Expressive.

To use your own application, register one with HCB (a public client, redirect URI `yourscheme://`) and build with:

```bash
./gradlew assembleRelease -PhcbClientId=YOUR_CLIENT_ID -PhcbRedirectScheme=yourscheme
```

You can also change the defaults in `gradle.properties`. `-PhcbBaseUrl=https://staging.example` points the app at another HCB instance.

## Scopes

The app requests `read write`. Admin-only scopes are intentionally not requested.
