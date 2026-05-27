# NOTICE

`@gachlab/capacitor-background-geolocation`

## License

This package is distributed under the MIT License.
See [LICENSE](./LICENSE) for the full text.

## Third-party components (Apache-2.0)

The Android native core under `android/src/main/java/com/marianhello/`
is derived from prior art that remains under the Apache License 2.0:

- `@josuelmm/cordova-background-geolocation`
  https://github.com/josuelmm/cordova-background-geolocation
- Original upstream: `mauron85/cordova-plugin-background-geolocation`
  https://github.com/mauron85/cordova-plugin-background-geolocation

Those files retain their original `SPDX-License-Identifier: Apache-2.0`
headers. The Apache-2.0 license text is available at:
https://www.apache.org/licenses/LICENSE-2.0.txt

The Android core is scheduled for a full Kotlin rewrite under MIT as part
of the v1.1 milestone; at that point this notice will be removed.

## Structural inspiration

Repository layout, build scripts and example-app structure take
non-substantial inspiration from the Cap-go Capacitor plugin scaffold
(MPL-2.0). No source files are copied; only directory shape and tooling
conventions.

- https://github.com/Cap-go/capacitor-background-geolocation
