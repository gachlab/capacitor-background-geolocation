// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · disposal-symbol polyfill (Fase 4).
// `using` / `await using` read the well-known Symbol.dispose / Symbol.asyncDispose at
// runtime. Ensure they exist so disposal works on engines predating them (older
// WKWebView / Android WebView) — additive and lax: the handles keep their explicit
// remove()/stop() regardless, so nothing here is load-bearing.

const wk = Symbol as { dispose?: symbol; asyncDispose?: symbol };
wk.dispose ??= Symbol('Symbol.dispose');
wk.asyncDispose ??= Symbol('Symbol.asyncDispose');
