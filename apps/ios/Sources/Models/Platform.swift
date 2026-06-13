import Foundation

/// Mirrors `Platform` in packages/engine/src/types.ts. The native iOS client is
/// always `.ios`; the type exists so push registration and `setPush` frames
/// carry the right discriminant.
public enum Platform: String, Codable, Sendable {
    case ios
    case android
}
