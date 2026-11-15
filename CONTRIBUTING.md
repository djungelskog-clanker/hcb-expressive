# Contributing

Thanks for helping out. A few ground rules:

- Be kind. The [Hack Club Code of Conduct](https://hackclub.com/conduct) applies here too.
- Keep the UI Material 3 Expressive: dynamic color, the shared components in `ui/Components.kt`, and the `MaterialShapes` set for decoration.
- New screens live in `ui/screens/`, new routes in `ui/Routes.kt`.
- API access goes through `data/Api.kt`; read JSON through the tolerant `J` wrapper so unknown or missing fields never crash the app.
- Run `./gradlew lintDebug assembleDebug` before opening a pull request. CI does the same.
- Never commit keystores, tokens or `local.properties`.

See [docs/development.md](docs/development.md) for the local setup.
