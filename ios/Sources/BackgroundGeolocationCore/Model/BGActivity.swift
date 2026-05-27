// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

public final class BGActivity: NSObject {
    public let type: String
    public let confidence: Int

    public init(type: String, confidence: Int) {
        self.type = type
        self.confidence = confidence
    }

    public func toDictionary() -> [String: Any] {
        return ["type": type, "confidence": confidence]
    }

    public override var description: String {
        return "BGActivity type=\(type) confidence=\(confidence)"
    }
}
