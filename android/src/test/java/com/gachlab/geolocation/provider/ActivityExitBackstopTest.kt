// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import com.google.android.gms.location.DetectedActivity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("ActivityExitBackstop — motion classification + resume bridge")
class ActivityExitBackstopTest {

    @AfterEach fun clear() { ActivityExitBackstop.setOnExit(null) }

    @Test
    @DisplayName("moving activities resume; still/tilting/unknown do not")
    fun classifiesMovingActivities() {
        assertTrue(ActivityExitBackstop.isMovingActivity(DetectedActivity.IN_VEHICLE))
        assertTrue(ActivityExitBackstop.isMovingActivity(DetectedActivity.ON_BICYCLE))
        assertTrue(ActivityExitBackstop.isMovingActivity(DetectedActivity.ON_FOOT))
        assertTrue(ActivityExitBackstop.isMovingActivity(DetectedActivity.RUNNING))
        assertTrue(ActivityExitBackstop.isMovingActivity(DetectedActivity.WALKING))
        assertFalse(ActivityExitBackstop.isMovingActivity(DetectedActivity.STILL))
        assertFalse(ActivityExitBackstop.isMovingActivity(DetectedActivity.TILTING))
        assertFalse(ActivityExitBackstop.isMovingActivity(DetectedActivity.UNKNOWN))
    }

    @Test
    @DisplayName("fireExit invokes the armed callback exactly once")
    fun firesOnce() {
        var count = 0
        ActivityExitBackstop.setOnExit { count++ }
        ActivityExitBackstop.fireExit()
        ActivityExitBackstop.fireExit()
        assertEquals(1, count)
    }
}
