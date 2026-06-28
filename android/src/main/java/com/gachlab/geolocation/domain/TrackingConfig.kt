// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

/**
 * The resolved location-tracking policy — a pure, immutable value object for the
 * `domain/` layer (ARCHITECTURE.md domain set). Where [TripConfig] is the
 * driving-event detection policy, TrackingConfig is *how location is acquired*:
 * accuracy, distance filter, intervals, and the stationary/exit behaviour.
 *
 * Every field is **resolved** — the platform `BGConfig` carries these as nullable
 * overrides scattered through the providers (each doing `cfg.x ?: DEFAULT_X`); the
 * `BGConfig.toTrackingConfig()` mapper resolves them once into this single value. The
 * providers still read `BGConfig` directly today; rebasing them onto TrackingConfig is
 * Roadmap Fase 3 (hub → ports). [maxAcceptedAccuracy] stays nullable — null means
 * "no accuracy gate", which is a distinct policy from any numeric threshold.
 */
data class TrackingConfig(
    val stationaryRadius: Float,
    val distanceFilter: Int,
    val desiredAccuracy: Int,
    val locationProvider: Int,
    val interval: Int,
    val fastestInterval: Int,
    val activitiesInterval: Int,
    val stationaryTimeout: Int,
    val stationaryPollInterval: Int,
    val stationaryPollFast: Int,
    val stationaryExitMode: String,
    val activityConfidenceThreshold: Int,
    val maxAcceptedAccuracy: Float?,
)
