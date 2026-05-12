//
//  MAURBackgroundSync.h
//
//  Created by Marian Hello on 07/07/16.
//  Copyright © 2016 mauron85. All rights reserved.
//

#ifndef MAURBackgroundSync_h
#define MAURBackgroundSync_h

#import <Foundation/Foundation.h>

@class MAURBackgroundSync;

// v3.5 Phase 4: notification names for sync events. The plugin layer observes them
// via NSNotificationCenter to forward into JS as syncStart / syncSuccess / syncError / syncProgress.
extern NSString * _Nonnull const MAURBackgroundSyncDidStartNotification;
extern NSString * _Nonnull const MAURBackgroundSyncDidSucceedNotification;
extern NSString * _Nonnull const MAURBackgroundSyncDidFailNotification;
extern NSString * _Nonnull const MAURBackgroundSyncDidProgressNotification;

@protocol MAURBackgroundSyncDelegate <NSObject>

@optional
- (void)backgroundSyncRequestedAbortUpdates:(MAURBackgroundSync * _Nonnull)task;
- (void)backgroundSyncHttpAuthorizationUpdates:(MAURBackgroundSync * _Nonnull)task;
// v3.5 Phase 4
- (void)backgroundSyncStarted:(MAURBackgroundSync * _Nonnull)task;
- (void)backgroundSyncSucceeded:(MAURBackgroundSync * _Nonnull)task locationsSent:(NSInteger)locationsSent;
- (void)backgroundSyncFailed:(MAURBackgroundSync * _Nonnull)task httpStatus:(NSInteger)httpStatus message:(NSString * _Nullable)message;

@end

@interface MAURBackgroundSync : NSObject

@property (nonatomic, weak) id<MAURBackgroundSyncDelegate> _Nullable delegate;

- (instancetype) init;
- (NSString*) status;
- (void) sync:(NSString * _Nonnull)url withTemplate:(id)locationTemplate withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders;
- (void) sync:(NSString * _Nonnull)url withTemplate:(id)locationTemplate withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders withMethod:(NSString * _Nullable)method;
- (void) cancel;

@end

#endif /* MAURBackgroundSync_h */
