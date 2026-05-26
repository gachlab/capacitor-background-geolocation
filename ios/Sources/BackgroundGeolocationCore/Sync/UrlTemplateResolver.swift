// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

enum UrlTemplateResolver {

    private static let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.timeZone = TimeZone(identifier: "UTC")
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    static func resolve(
        _ template: String,
        location: BGLocation,
        queryParams: [String: String]? = nil
    ) -> String {
        var context = [String: String]()

        if let lat = location.latitude {
            let s = String(lat)
            context["latitude"] = s
            context["lat"] = s
        }
        if let lon = location.longitude {
            let s = String(lon)
            context["longitude"] = s
            context["lon"] = s
        }
        if let t = location.time {
            let ms = Int64(t.timeIntervalSince1970 * 1000)
            context["time"] = String(ms)
            context["timestamp"] = String(ms)
            context["timestamp_iso"] = isoFormatter.string(from: t)
        }
        if let v = location.speed    { context["speed"]    = String(v) }
        if let v = location.altitude { context["altitude"] = String(v) }
        if let v = location.heading  { context["bearing"]  = String(v) }
        if let v = location.accuracy { context["accuracy"] = String(v) }
        if let v = location.provider { context["provider"] = v }

        if let qp = queryParams {
            for (k, v) in qp { context[k] = v }
        }

        let pattern = try! NSRegularExpression(pattern: #"\{([a-zA-Z0-9_]+)\}"#)
        let nsTemplate = template as NSString
        let range = NSRange(location: 0, length: nsTemplate.length)
        let matches = pattern.matches(in: template, range: range)

        var result = template
        for match in matches.reversed() {
            let fullRange  = Range(match.range, in: result)!
            let keyRange   = Range(match.range(at: 1), in: result)!
            let key        = String(result[keyRange])
            guard let raw  = context[key] else { continue }
            let encoded    = encode(raw)
            result.replaceSubrange(fullRange, with: encoded)
        }
        return result
    }

    private static func encode(_ value: String) -> String {
        var s = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
        s = s.replacingOccurrences(of: "&", with: "%26")
        s = s.replacingOccurrences(of: "+", with: "%2B")
        return s
    }
}
