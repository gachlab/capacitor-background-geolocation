//
//  MAURActivityLocationProvider.m
//  BackgroundGeolocation
//
//  Created by Marian Hello on 14/09/2016.
//  Copyright © 2016 mauron85. All rights reserved.
//
//  v4.5.2 — refactored to use CoreMotion's CMMotionActivityManager directly.
//  The SOMotionDetector dependency (sources + plugin.xml entries) was removed
//  in this version.
//

#import <Foundation/Foundation.h>
#import <CoreMotion/CoreMotion.h>
#import "MAURActivityLocationProvider.h"
#import "MAURActivity.h"
#import "MAURLocationManager.h"
#import "MAURLogging.h"

static NSString * const TAG = @"ActivityLocationProvider";
static NSString * const Domain = @"com.marianhello";

// Local motion-type enum (replaces the legacy SOMotionType used previously).
typedef NS_ENUM(NSUInteger, MAURMotionType) {
    MAURMotionTypeUnknown = 0,
    MAURMotionTypeNotMoving,
    MAURMotionTypeWalking,
    MAURMotionTypeRunning,
    MAURMotionTypeAutomotive,
    MAURMotionTypeCycling
};

@implementation MAURActivityLocationProvider {
    BOOL isStarted;
    BOOL isTracking;
    BOOL motionAvailable;
    BOOL motionPermissionErrorEmitted;
    MAURMotionType lastMotionType;

    MAURLocationManager *locationManager;
    CMMotionActivityManager *activityManager;
    NSOperationQueue *activityQueue;

    // v4.5.2: cache of active config so motion callbacks can read
    // activityConfidenceThreshold without re-fetching.
    MAURConfig *currentConfig;
}

- (instancetype) init
{
    self = [super init];

    if (self) {
        isStarted = NO;
        isTracking = NO;
        motionAvailable = NO;
        motionPermissionErrorEmitted = NO;
        lastMotionType = MAURMotionTypeUnknown;
    }

    return self;
}

- (void) onCreate {
    locationManager = [MAURLocationManager sharedInstance];
    locationManager.delegate = self;

    // v4.5.2 — CoreMotion direct. Without CMMotionActivityManager support the
    // provider cannot drive STILL/ACTIVE transitions; emit a clear error so the
    // host app knows to fall back to DISTANCE_FILTER or RAW.
    motionAvailable = [CMMotionActivityManager isActivityAvailable];
    if (!motionAvailable) {
        DDLogError(@"%@ CMMotionActivityManager unavailable on this device; ACTIVITY_PROVIDER will be inert.", TAG);
        NSError *err = [NSError errorWithDomain:Domain
                                           code:MAURBGServiceError
                                       userInfo:@{ NSLocalizedDescriptionKey: @"CMMotionActivityManager unavailable on this device." }];
        if (self.delegate && [self.delegate respondsToSelector:@selector(onError:)]) {
            [self.delegate onError:err];
        }
        return;
    }

    activityManager = [[CMMotionActivityManager alloc] init];
    activityQueue = [[NSOperationQueue alloc] init];
    activityQueue.name = @"MAURActivityRecognitionQueue";
    activityQueue.maxConcurrentOperationCount = 1;
}

- (BOOL) onConfigure:(MAURConfig*)config error:(NSError * __autoreleasing *)outError
{
    DDLogVerbose(@"%@ configure", TAG);

    currentConfig = config;

    locationManager.pausesLocationUpdatesAutomatically = [config pauseLocationUpdates];
    locationManager.activityType = [config decodeActivityType];
    locationManager.distanceFilter = config.distanceFilter.integerValue; // meters
    locationManager.desiredAccuracy = [config decodeDesiredAccuracy];

    return YES;
}

- (BOOL) onStart:(NSError * __autoreleasing *)outError
{
    DDLogInfo(@"%@ will start", TAG);

    if (isStarted) {
        return YES;
    }

    if (!motionAvailable || activityManager == nil) {
        // No motion service: degrade gracefully — keep emitting raw location
        // changes via the manager, but the STILL/ACTIVE state machine is off.
        [self startTracking];
        isStarted = YES;
        return YES;
    }

    // iOS 11+: surface motion-permission denial up-front so the host app can
    // re-prompt. Older iOS versions deliver permission errors via the handler.
    if (@available(iOS 11.0, *)) {
        CMAuthorizationStatus authStatus = [CMMotionActivityManager authorizationStatus];
        if (authStatus == CMAuthorizationStatusDenied || authStatus == CMAuthorizationStatusRestricted) {
            if (!motionPermissionErrorEmitted) {
                NSError *err = [NSError errorWithDomain:Domain
                                                   code:MAURBGServiceError
                                               userInfo:@{ NSLocalizedDescriptionKey: @"Motion & Fitness permission denied; ACTIVITY_PROVIDER cannot detect STILL/ACTIVE." }];
                if (self.delegate && [self.delegate respondsToSelector:@selector(onError:)]) {
                    [self.delegate onError:err];
                }
                motionPermissionErrorEmitted = YES;
            }
            // Still start raw tracking so locations flow.
            [self startTracking];
            isStarted = YES;
            return YES;
        }
    }

    __weak typeof(self) weakSelf = self;
    [activityManager startActivityUpdatesToQueue:activityQueue
                                     withHandler:^(CMMotionActivity * _Nullable activity) {
        typeof(self) strongSelf = weakSelf;
        if (strongSelf == nil || activity == nil) return;
        [strongSelf handleActivityUpdate:activity];
    }];

    // v4.5.2 — start tracking immediately. Without this, if the user opens the
    // app while already still, CoreMotion fires STILL first and `handleActivityUpdate`
    // never calls startTracking (its rule is "ACTIVE → start"), so no fix is ever
    // produced and the initial stationary is never emitted. Mirrors the legacy
    // SOMotionDetector behavior. If CoreMotion subsequently confirms STILL, the
    // first incoming fix will trigger `onStationaryChanged` + `stopTracking`, so
    // battery cost stays bounded.
    [self startTracking];

    isStarted = YES;
    return YES;
}

- (BOOL) onStop:(NSError * __autoreleasing *)outError
{
    DDLogInfo(@"%@ will stop", TAG);

    if (!isStarted) {
        return YES;
    }

    if (activityManager != nil) {
        [activityManager stopActivityUpdates];
    }
    [self stopTracking];
    isStarted = NO;

    return YES;
}

#pragma mark - CMMotionActivity → MAURActivity bridge

- (void) handleActivityUpdate:(CMMotionActivity *)activity
{
    // CMMotionActivityConfidence: Low=0, Medium=1, High=2 → normalize to 0-100
    // so activityConfidenceThreshold means the same thing as on Android.
    int confidence;
    switch (activity.confidence) {
        case CMMotionActivityConfidenceLow:    confidence = 20; break;
        case CMMotionActivityConfidenceMedium: confidence = 40; break;
        case CMMotionActivityConfidenceHigh:   confidence = 80; break;
        default:                               confidence = 0;  break;
    }

    NSNumber *thresholdN = currentConfig.activityConfidenceThreshold;
    if (thresholdN != nil && confidence < thresholdN.intValue) {
        DDLogDebug(@"%@ ignoring low-confidence activity confidence=%d threshold=%@", TAG, confidence, thresholdN);
        return;
    }

    MAURMotionType motionType;
    NSString *typeStr;
    if (activity.automotive) {
        motionType = MAURMotionTypeAutomotive;
        typeStr = @"IN_VEHICLE";
    } else if (activity.cycling) {
        motionType = MAURMotionTypeCycling;
        typeStr = @"ON_BICYCLE";
    } else if (activity.running) {
        motionType = MAURMotionTypeRunning;
        typeStr = @"RUNNING";
    } else if (activity.walking) {
        motionType = MAURMotionTypeWalking;
        typeStr = @"WALKING";
    } else if (activity.stationary) {
        motionType = MAURMotionTypeNotMoving;
        typeStr = @"STILL";
    } else {
        // CoreMotion fired "unknown" or no specific motion flag set. Do NOT
        // collapse this to NotMoving — under uncertainty we keep the current
        // tracking state (legacy behavior was to stop on UNKNOWN, which paused
        // GPS unexpectedly during low-confidence motion gaps).
        motionType = MAURMotionTypeUnknown;
        typeStr = @"UNKNOWN";
    }

    // Hop to main queue: location manager + delegate are main-thread-affine.
    dispatch_async(dispatch_get_main_queue(), ^{
        // UNKNOWN must not perturb the state machine: no emit, no lastMotionType
        // mutation, no tracking change. Otherwise a sequence STILL → UNKNOWN
        // would lose the STILL state and the next fix would be delivered as a
        // regular location instead of stationary.
        if (motionType == MAURMotionTypeUnknown) {
            DDLogDebug(@"%@ ignoring UNKNOWN activity (state preserved, confidence=%d)", TAG, confidence);
            return;
        }

        BOOL changed = (motionType != self->lastMotionType);
        self->lastMotionType = motionType;

        if (changed) {
            DDLogDebug(@"%@ activityTypeChanged: %@ confidence=%d", TAG, typeStr, confidence);
            MAURActivity *act = [[MAURActivity alloc] init];
            act.type = typeStr;
            act.confidence = [NSNumber numberWithInt:confidence];
            if (super.delegate && [super.delegate respondsToSelector:@selector(onActivityChanged:)]) {
                [super.delegate onActivityChanged:act];
            }
        }

        // Tracking control:
        // - ACTIVE motion (walking, running, automotive, cycling) → ensure tracking is on.
        // - STILL → leave tracking running; it will be stopped after the next fix (legacy).
        if (motionType != MAURMotionTypeNotMoving) {
            [self startTracking];
        }
    });
}

#pragma mark - Location plumbing

- (void) startTracking
{
    if (isTracking) {
        return;
    }

    NSError *error = nil;
    if ([locationManager start:&error]) {
        isTracking = YES;
    } else {
        if (self.delegate && [self.delegate respondsToSelector:@selector(onError:)]) {
            [self.delegate onError:error];
        }
    }
}

- (void) stopTracking
{
    if (isTracking) {
        [locationManager stop:nil];
        isTracking = NO;
    }
}

- (void) onSwitchMode:(MAUROperationalMode)mode
{
    /* do nothing */
}

- (void) onAuthorizationChanged:(MAURLocationAuthorizationStatus)authStatus
{
    [self.delegate onAuthorizationChanged:authStatus];
}

- (void) onLocationsChanged:(NSArray*)locations
{
    // v4.5.2: while NotMoving we only emit the stationary fix; the previous
    // code fell through and also delivered each location as onLocationChanged,
    // which produced phantom "moving" rows during a STILL window.
    if (lastMotionType == MAURMotionTypeNotMoving) {
        [self stopTracking];
        [self.delegate onStationaryChanged:[MAURLocation fromCLLocation:[locations lastObject]]];
        return;
    }

    for (CLLocation *location in locations) {
        MAURLocation *bgloc = [MAURLocation fromCLLocation:location];
        [self.delegate onLocationChanged:bgloc];
    }
}

- (void) onError:(NSError*)error
{
    [self.delegate onError:error];
}

- (void) onPause:(CLLocationManager*)manager
{
    [self.delegate onLocationPause];
}

- (void) onResume:(CLLocationManager*)manager
{
    [self.delegate onLocationResume];
}

- (void) onDestroy {
    DDLogInfo(@"Destroying %@ ", TAG);
    [self onStop:nil];

    // v4.5.2: MAURLocationManager is a singleton shared with the other providers
    // (RAW, DISTANCE). Release the delegate slot so a subsequent provider swap
    // does not leave this destroyed instance as the active delegate.
    if (locationManager != nil && locationManager.delegate == self) {
        locationManager.delegate = nil;
    }

    activityManager = nil;
    activityQueue = nil;
}

@end
