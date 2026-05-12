//
//  MAURSensorFusionDetector.h
//  BackgroundGeolocation
//
//  v4.2 Phase 8 — Real sensor fusion detector for iOS.
//  Uses CMMotionManager to sample userAcceleration (gravity removed) and rotationRate.
//  Refines possibleCrash via accelerometer impact and detects phoneUsageWhileDriving.
//

#ifndef MAURSensorFusionDetector_h
#define MAURSensorFusionDetector_h

#import <Foundation/Foundation.h>

@class MAURLocation;

@protocol MAURSensorFusionListener <NSObject>
- (void)onSensorCrashWithImpactG:(double)impactG location:(MAURLocation *)location;
- (void)onPhoneUsageWhileDriving:(MAURLocation *)location;
@end

@interface MAURSensorFusionDetector : NSObject

@property (nonatomic, weak)   id<MAURSensorFusionListener> listener;
@property (nonatomic, assign) BOOL enabled;
@property (nonatomic, assign) double crashImpactG;       // default 3.0
@property (nonatomic, assign) NSTimeInterval crashCooldownMs;        // default 10000
@property (nonatomic, assign) NSTimeInterval phoneUsageWindowMs;     // default 4000
@property (nonatomic, assign) NSTimeInterval phoneUsageCooldownMs;   // default 60000

@property (nonatomic, assign) BOOL tripActive;
@property (nonatomic, strong) MAURLocation *lastLocation;

- (instancetype)init;
- (BOOL)isAvailable;
- (void)start;
- (void)stop;

@end

#endif /* MAURSensorFusionDetector_h */
