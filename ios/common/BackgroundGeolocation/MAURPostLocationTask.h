//
//  MAURPostLocationTask.h
//  BackgroundGeolocation
//
//  Created by Marian Hello on 27/04/2018.
//  Copyright © 2018 mauron85. All rights reserved.
//

#ifndef MAURPostLocationTask_h
#define MAURPostLocationTask_h

#import "MAURConfig.h"
#import "MAURLocation.h"

@class MAURPostLocationTask;

@protocol MAURPostLocationTaskDelegate <NSObject>

@optional
- (void)postLocationTaskRequestedAbortUpdates:(MAURPostLocationTask * _Nonnull)task;
- (void)postLocationTaskHttpAuthorizationUpdates:(MAURPostLocationTask * _Nonnull)task;
// v3.5 Phase 4
- (void)postLocationTaskSyncStarted:(MAURPostLocationTask * _Nonnull)task;
- (void)postLocationTaskSyncSucceeded:(MAURPostLocationTask * _Nonnull)task locationsSent:(NSInteger)locationsSent;
- (void)postLocationTaskSyncFailed:(MAURPostLocationTask * _Nonnull)task httpStatus:(NSInteger)httpStatus message:(NSString * _Nullable)message;

@end

@interface MAURPostLocationTask : NSObject

@property (nonatomic, weak) MAURConfig * _Nullable config;
@property (nonatomic, weak) id<MAURPostLocationTaskDelegate> _Nullable delegate;
/** v4.5.1 — pending driving events buffer owned by the facade; the task drains it onto the
 *  post-transform location so events fired without a simultaneous fix (provider change,
 *  sensor crash, phone usage) survive even if `locationTransform` returns a new instance.
 *  Weak ref: if the facade is gone, no flush — by design. */
@property (nonatomic, weak) NSMutableArray * _Nullable pendingDrivingEventsBuffer;
/** v4.5.1 — same idea for the battery snapshot block. The facade installs a block that the
 *  task invokes AFTER a successful transform, so even when `locationTransform` returns a
 *  fresh instance, battery/charging fields land on what actually gets POSTed. */
@property (nonatomic, copy) void (^ _Nullable attachBatterySnapshot)(MAURLocation * _Nonnull);

- (void) add:(MAURLocation * _Nonnull)location;
- (void) start;
- (void) stop;
- (void) sync;

+ (void) setLocationTransform:(MAURLocationTransform _Nullable)transform;
+ (MAURLocationTransform _Nullable) locationTransform;

@end

#endif /* MAURPostLocationTask_h */
