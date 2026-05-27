<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright (c) 2026 gachlab -->

# Background Geolocation Example

Minimal Capacitor 8 host app for `@gachlab/capacitor-background-geolocation`.

## Quick start

```bash
cd example-app
npm install
npx cap add android
npx cap sync
npx cap open android
```

For iOS:

```bash
npx cap add ios
npx cap sync ios
npx cap open ios
```

The web layer (`www/index.html` + `www/main.js`) is the source of truth — it
exposes Configure / Start / Stop / Get Current / Get Valid / Clear / Request
Permission buttons plus a live event log.
