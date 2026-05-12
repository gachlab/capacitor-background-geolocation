# @josuelmm/capacitor-background-geolocation

Capacitor 8+ plugin for accurate background geolocation tracking on iOS and Android.

Derived from the `@josuelmm/cordova-background-geolocation` native core
(fork of `mauron85/cordova-plugin-background-geolocation`). The Cordova
fork is maintained separately; this package is a Capacitor-native
rewrite of the JavaScript bridge, reusing the proven Android and iOS
core for GPS tracking, sync queue, providers, payload, diagnostics
and battery handling.

## Status

`0.1.0` — scaffold (Phase 1). Not functional yet.

Phased roadmap:

- Phase 1: scaffold + build green
- Phase 2: TypeScript API contract
- Phase 3: Android bridge over `com.marianhello.bgloc` core
- Phase 4: iOS bridge over `MAUR*` core
- Phase 5: Web fallback via `navigator.geolocation`
- Phase 6: example app
- Phase 7: CI + publish to npm

## Install

```bash
npm install @josuelmm/capacitor-background-geolocation
npx cap sync
```

## Platforms

- iOS 14+
- Android API 23+
- Web (foreground fallback)

## License

Apache License 2.0. See `LICENSE` and `NOTICE.md`.
