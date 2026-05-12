//
//  MAURSensorFusionDetector.m
//  BackgroundGeolocation
//
//  v4.2 Phase 8 — sensor fusion detector implementation.
//

#import "MAURSensorFusionDetector.h"
#import <CoreMotion/CoreMotion.h>
#import <UIKit/UIKit.h>

static const double kJitterGyroRadS  = 0.7;   // ~40 deg/s
static const double kJitterAccelMps2 = 0.5;

@interface MAURSensorFusionDetector ()
@property (nonatomic, strong) CMMotionManager *motion;
@property (nonatomic, strong) NSOperationQueue *queue;
@property (nonatomic, assign) BOOL started;
@property (nonatomic, assign) NSTimeInterval lastCrashAt;
@property (nonatomic, assign) NSTimeInterval lastPhoneUsageAt;
@property (nonatomic, assign) NSTimeInterval jitterAboveSince;
@end

@implementation MAURSensorFusionDetector

- (instancetype)init {
    if ((self = [super init])) {
        _motion = [[CMMotionManager alloc] init];
        _motion.deviceMotionUpdateInterval = 1.0 / 50.0; // 50 Hz
        _queue = [[NSOperationQueue alloc] init];
        _queue.name = @"MAURSensorFusionQueue";
        _queue.maxConcurrentOperationCount = 1;
        _enabled = NO;
        _crashImpactG = 3.0;
        _crashCooldownMs = 10000;
        _phoneUsageWindowMs = 4000;
        _phoneUsageCooldownMs = 60000;
        _started = NO;
        _lastCrashAt = 0;
        _lastPhoneUsageAt = 0;
        _jitterAboveSince = 0;
    }
    return self;
}

- (BOOL)isAvailable {
    return self.motion.isDeviceMotionAvailable;
}

- (void)start {
    @synchronized (self) {
        if (self.started || !self.enabled) return;
        if (![self.motion isDeviceMotionAvailable]) return;
        __weak typeof(self) weakSelf = self;
        [self.motion startDeviceMotionUpdatesToQueue:self.queue
                                          withHandler:^(CMDeviceMotion * _Nullable motion, NSError * _Nullable error) {
            if (!motion || error) return;
            [weakSelf processMotion:motion];
        }];
        self.started = YES;
    }
}

- (void)stop {
    @synchronized (self) {
        if (!self.started) return;
        [self.motion stopDeviceMotionUpdates];
        self.started = NO;
        self.jitterAboveSince = 0;
    }
}

- (void)processMotion:(CMDeviceMotion *)motion {
    if (!self.enabled) return;
    NSTimeInterval nowMs = [[NSDate date] timeIntervalSince1970] * 1000.0;

    // userAcceleration is in g (gravity removed); convert magnitude to g and to m/s².
    double ax = motion.userAcceleration.x;
    double ay = motion.userAcceleration.y;
    double az = motion.userAcceleration.z;
    double accelMagG  = sqrt(ax*ax + ay*ay + az*az);            // g
    double accelMagMs = accelMagG * 9.80665;                    // m/s²

    double gx = motion.rotationRate.x;
    double gy = motion.rotationRate.y;
    double gz = motion.rotationRate.z;
    double gyroMag = sqrt(gx*gx + gy*gy + gz*gz);               // rad/s

    BOOL tripActiveNow = self.tripActive;
    MAURLocation *loc = self.lastLocation;
    id<MAURSensorFusionListener> l = self.listener;

    // Crash detection
    if (tripActiveNow && self.crashImpactG > 0 && accelMagG >= self.crashImpactG
            && (nowMs - self.lastCrashAt) >= self.crashCooldownMs) {
        self.lastCrashAt = nowMs;
        if ([l respondsToSelector:@selector(onSensorCrashWithImpactG:location:)]) {
            [l onSensorCrashWithImpactG:accelMagG location:loc];
        }
    }

    // phoneUsageWhileDriving
    if (!tripActiveNow) { self.jitterAboveSince = 0; return; }
    BOOL screenOn = [self isScreenOnApprox];
    if (!screenOn) { self.jitterAboveSince = 0; return; }

    BOOL above = (accelMagMs >= kJitterAccelMps2) || (gyroMag >= kJitterGyroRadS);
    if (above) {
        if (self.jitterAboveSince == 0) self.jitterAboveSince = nowMs;
        if ((nowMs - self.jitterAboveSince) >= self.phoneUsageWindowMs
                && (nowMs - self.lastPhoneUsageAt) >= self.phoneUsageCooldownMs) {
            self.lastPhoneUsageAt = nowMs;
            self.jitterAboveSince = 0;
            if ([l respondsToSelector:@selector(onPhoneUsageWhileDriving:)]) {
                [l onPhoneUsageWhileDriving:loc];
            }
        }
    } else {
        self.jitterAboveSince = 0;
    }
}

- (BOOL)isScreenOnApprox {
    // Heuristic: app is foreground active => screen is on. Background sampling does
    // not constitute phone usage while driving (passenger may have screen off too).
    __block UIApplicationState state = UIApplicationStateBackground;
    if ([NSThread isMainThread]) {
        state = [UIApplication sharedApplication].applicationState;
    } else {
        dispatch_sync(dispatch_get_main_queue(), ^{
            state = [UIApplication sharedApplication].applicationState;
        });
    }
    return state == UIApplicationStateActive;
}

@end
