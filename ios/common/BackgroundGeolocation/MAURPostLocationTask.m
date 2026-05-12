//
//  MAURPostLocationTask.m
//  BackgroundGeolocation
//
//  Created by Marian Hello on 27/04/2018.
//  Copyright © 2018 mauron85. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "Reachability.h"
#import "MAURSQLiteLocationDAO.h"
#import "MAURBackgroundSync.h"
#import "MAURConfig.h"
#import "MAURLogging.h"
#import "MAURPostLocationTask.h"
#import "MAURSQLiteLocationDAO.h"
#import "MAURSessionLocationDAO.h"
#import "MAURUrlTemplateResolver.h"

static NSString * const TAG = @"MAURPostLocationTask";

@interface MAURPostLocationTask() <MAURBackgroundSyncDelegate>
{
    
}
@end

@implementation MAURPostLocationTask
{
    Reachability *reach;
    MAURBackgroundSync *uploader;
    BOOL hasConnectivity;
}

static MAURLocationTransform s_locationTransform = nil;

- (instancetype) init
{
    self = [super init];

    if (self == nil) {
        return self;
    }

    hasConnectivity = YES;

    uploader = [[MAURBackgroundSync alloc] init];
    uploader.delegate = self;
    
    reach = [Reachability reachabilityWithHostname:@"www.google.com"];
    reach.reachableBlock = ^(Reachability *_reach) {
        // keep in mind this is called on a background thread
        // and if you are updating the UI it needs to happen
        // on the main thread:
        hasConnectivity = YES;
        [_reach stopNotifier];
    };
    
    reach.unreachableBlock = ^(Reachability *reach) {
        hasConnectivity = NO;
    };

    return self;
}

- (void) start
{
    hasConnectivity = YES;
    [reach startNotifier];
}

- (void) stop
{
    [reach stopNotifier];
}

- (void) add:(MAURLocation * _Nonnull)inLocation
{
    // Take this variable on the main thread to be safe
    MAURLocationTransform locationTransform = s_locationTransform;
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_BACKGROUND, 0), ^{
        
        MAURLocation *location = inLocation;

        if (locationTransform != nil) {
            // v4.5.1 — snapshot v4.3+ fields BEFORE transform so they survive a transform
            // that returns a brand new MAURLocation instance (otherwise events/battery would
            // be lost en route to SQLite / backend).
            NSMutableArray *rawEvents = inLocation.drivingEvents;
            NSNumber *rawBattery = inLocation.batteryLevel;
            NSNumber *rawCharging = inLocation.isCharging;

            location = locationTransform(location);

            if (location == nil) {
                return;
            }

            // v4.5.1 — re-attach fields the transform may have dropped. When the transform
            // produced a NEW instance (`location != inLocation`), MERGE rawEvents into the new
            // instance's array instead of overwriting — same semantics as Android
            // `LocationServiceImpl.onLocation` re-attach. If the transform returned the same
            // instance (mutated in place) the rawEvents are already there.
            if (location != inLocation) {
                if (rawEvents != nil && [rawEvents count] > 0) {
                    if (location.drivingEvents == nil) {
                        location.drivingEvents = [rawEvents mutableCopy];
                    } else {
                        [location.drivingEvents addObjectsFromArray:rawEvents];
                    }
                }
                if (location.batteryLevel == nil) location.batteryLevel = rawBattery;
                if (location.isCharging == nil)   location.isCharging   = rawCharging;
            }
        }

        // v4.5.1 — drain pending driving events ONTO the post-transform location. Previously
        // the facade drained them BEFORE [postLocationTask add:], so a transform that returned
        // nil silently lost every buffered event. Now: if transform succeeded we're guaranteed
        // `location != nil` here and the buffer is drained safely.
        NSMutableArray *pendingBuffer = self.pendingDrivingEventsBuffer;
        if (pendingBuffer != nil) {
            @synchronized (pendingBuffer) {
                if ([pendingBuffer count] > 0) {
                    NSTimeInterval nowMs = [[NSDate date] timeIntervalSince1970] * 1000.0;
                    if (location.drivingEvents == nil) location.drivingEvents = [NSMutableArray array];
                    for (NSDictionary *ev in pendingBuffer) {
                        NSNumber *t = ev[@"time"];
                        NSTimeInterval evMs = t != nil ? [t doubleValue] : nowMs;
                        if (nowMs - evMs <= 60000.0) {
                            [location.drivingEvents addObject:ev];
                        }
                    }
                    [pendingBuffer removeAllObjects];
                }
            }
        }
        // v4.5.1 — stamp battery snapshot AFTER transform so it lands on the POSTed instance
        // even if the transform created a new one.
        void (^attachBattery)(MAURLocation *) = self.attachBatterySnapshot;
        if (attachBattery != nil) {
            attachBattery(location);
        }

        // v3.5 Phase 4: mock location policy. Detection already exists in MAURLocation.simulated.
        if (location.simulated != nil && [location.simulated boolValue]) {
            NSString *policy = self.config.mockLocationPolicy ?: @"allow";
            if ([@"drop" isEqualToString:policy]) {
                DDLogInfo(@"%@ Simulated/mock location dropped (mockLocationPolicy=drop)", TAG);
                return;
            }
            // "flag": leave it. The simulated NSNumber is already on the model and propagates via toResultFromTemplate.
            // "allow": no-op.
        }

        MAURSQLiteLocationDAO *locationDAO = [MAURSQLiteLocationDAO sharedInstance];
        // TODO: investigate location id always 0
        NSNumber *locationId = [locationDAO persistLocation:location limitRows:self.config.maxLocations.integerValue];
        
        if ([[MAURSessionLocationDAO sharedInstance] isSessionActive]) {
            [[MAURSessionLocationDAO sharedInstance] persistSessionLocation:location];
        }
        
        if (hasConnectivity && [self.config hasValidUrl]) {
            NSError *error = nil;
            if ([self post:location toUrl:self.config.url withTemplate:self.config._template withHttpHeaders:self.config.httpHeaders error:&error]) {
                if (locationId != nil) {
                    [locationDAO deleteLocation:locationId error:nil];
                }
            }
        }

        if ([self.config hasValidSyncUrl] && [self.config syncEnabled]) {
            NSNumber *locationsCount = [locationDAO getLocationsForSyncCount];
            NSInteger threshold = self.config.syncThreshold != nil ? self.config.syncThreshold.integerValue : 100;
            if (locationsCount && [locationsCount integerValue] >= threshold) {
                DDLogDebug(@"%@ Attempt to sync locations: %@ threshold: %ld", TAG, locationsCount, (long)threshold);
                [self sync];
            }
        }
    });
}

- (BOOL) post:(MAURLocation*)location
         toUrl:(NSString*)url
    withTemplate:(id)locationTemplate
 withHttpHeaders:(NSMutableDictionary*)httpHeaders
         error:(NSError * __autoreleasing *)outError
{
    // v3.3 Phase 2: backend-agnostic transport.
    // Resolve URL template using current location + queryParams (for both single and batch modes).
    NSString *resolvedUrl = [MAURUrlTemplateResolver resolve:url location:location queryParams:self.config.queryParams];

    NSString *method = self.config.httpMethod ?: @"POST";
    NSString *mode = self.config.httpMode ?: @"batch";
    BOOL isBodyless = [@"GET" isEqualToString:method];
    BOOL singleMode = isBodyless || [@"single" isEqualToString:mode];

    NSData *data = nil;
    if (!isBodyless) {
        // For single mode (or body methods that prefer one location per request) send a JSONObject;
        // for batch send the array (current behaviour).
        if (singleMode) {
            data = [NSJSONSerialization dataWithJSONObject:[location toResultFromTemplate:locationTemplate] options:0 error:outError];
        } else {
            NSArray *locations = [[NSArray alloc] initWithObjects:[location toResultFromTemplate:locationTemplate], nil];
            data = [NSJSONSerialization dataWithJSONObject:locations options:0 error:outError];
        }
        if (!data) {
            return NO;
        }
    }

    NSString *jsonStr = data ? [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding] : nil;
    NSString *contentType = [httpHeaders objectForKey:@"Content-Type"];
    if (!contentType) {
        contentType = @"application/json";
    }

    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:[NSURL URLWithString:resolvedUrl]];
    [request setHTTPMethod:method];
    if (!isBodyless) {
        [request setValue:contentType forHTTPHeaderField:@"Content-Type"];
    }
    if (httpHeaders != nil) {
        for (id key in httpHeaders) {
            if (![key isEqualToString:@"Content-Type"]) {
                NSString *value = [httpHeaders objectForKey:key];
                [request addValue:value forHTTPHeaderField:key];
            }
        }
    }

    if (!isBodyless) {
        if ([contentType isEqualToString:@"application/x-www-form-urlencoded"]) {
            id jsonObject = [NSJSONSerialization JSONObjectWithData:data options:0 error:outError];
            NSDictionary *dict = nil;
            if ([jsonObject isKindOfClass:[NSArray class]] && [jsonObject count] == 1) {
                dict = [jsonObject firstObject];
            } else if ([jsonObject isKindOfClass:[NSDictionary class]]) {
                dict = jsonObject;
            }
            if (dict) {
                NSMutableArray *parts = [NSMutableArray array];
                for (NSString *key in dict) {
                    NSString *value = [NSString stringWithFormat:@"%@", dict[key]];
                    NSString *encodedKey = [key stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLQueryAllowedCharacterSet]];
                    NSString *encodedValue = [value stringByAddingPercentEncodingWithAllowedCharacters:[NSCharacterSet URLQueryAllowedCharacterSet]];
                    NSString *part = [NSString stringWithFormat:@"%@=%@", encodedKey, encodedValue];
                    [parts addObject:part];
                }
                NSString *encodedString = [parts componentsJoinedByString:@"&"];
                [request setHTTPBody:[encodedString dataUsingEncoding:NSUTF8StringEncoding]];
            } else {
                [request setHTTPBody:[jsonStr dataUsingEncoding:NSUTF8StringEncoding]];
            }
        } else {
            [request setHTTPBody:[jsonStr dataUsingEncoding:NSUTF8StringEncoding]];
        }
    }

    // v3.4: NSURLSession (iOS 7+) replaces deprecated [NSURLConnection sendSynchronousRequest:].
    // We run on a background queue (see -add: dispatch_async) so a semaphore-based wait is safe.
    __block NSHTTPURLResponse *urlResponse = nil;
    __block NSError *taskError = nil;
    dispatch_semaphore_t sema = dispatch_semaphore_create(0);
    NSURLSessionDataTask *dataTask = [[NSURLSession sharedSession]
        dataTaskWithRequest:request
          completionHandler:^(NSData * _Nullable d, NSURLResponse * _Nullable response, NSError * _Nullable err) {
            urlResponse = (NSHTTPURLResponse *)response;
            taskError = err;
            dispatch_semaphore_signal(sema);
        }];
    [dataTask resume];
    // 120s ceiling to mirror the previous synchronous timeout; URLSession also enforces its own.
    dispatch_semaphore_wait(sema, dispatch_time(DISPATCH_TIME_NOW, (int64_t)(120 * NSEC_PER_SEC)));
    if (taskError != nil && outError != NULL) {
        *outError = taskError;
    }

    NSInteger statusCode = urlResponse.statusCode;
    
    if (statusCode == 285)
    {
        // Okay, but we don't need to continue sending these
        DDLogDebug(@"Location was sent to the server, and received an \"HTTP 285 Updated Not Required\"");
        dispatch_async(dispatch_get_main_queue(), ^{
            if (_delegate && [_delegate respondsToSelector:@selector(postLocationTaskRequestedAbortUpdates:)])
            {
                [_delegate postLocationTaskRequestedAbortUpdates:self];
            }
        });
    }

    if (statusCode == 401)
    {   
        dispatch_async(dispatch_get_main_queue(), ^{
            if (_delegate && [_delegate respondsToSelector:@selector(postLocationTaskHttpAuthorizationUpdates:)])
            {
                [_delegate postLocationTaskHttpAuthorizationUpdates:self];
            }
        });
    }
    // All 2xx statuses are okay
    if (statusCode >= 200 && statusCode < 300)
    {
        return YES;
    }
    
    // v4.4.1: guard against outError == NULL (defensive — current callers pass &error).
    if (outError == NULL || *outError == nil) {
        DDLogDebug(@"%@ Server error while posting locations responseCode: %ld", TAG, (long)statusCode);
    } else {
        DDLogError(@"%@ Error while posting locations %@", TAG, [*outError localizedDescription]);
    }
    
    return NO;
}

- (void) sync
{
    if (![self.config syncEnabled] || ![self.config hasValidSyncUrl]) {
        return;
    }
    // v4.5.1 — rescue rows stuck in SyncPending from a previous upload that never completed
    // (app/process killed mid-flight). Anything older than 15 min is safe to revert to
    // PostPending; rows younger than that may still be uploading on a background NSURLSession.
    NSTimeInterval staleCutoff = [[NSDate date] timeIntervalSince1970] - (15 * 60);
    [[MAURSQLiteLocationDAO sharedInstance] restoreStaleSyncLocationsOlderThan:staleCutoff error:nil];
    // For sync (batch) only static queryParams placeholders apply; per-location templating
    // belongs in real-time post (httpMode="single" + httpMethod=GET) instead.
    NSString *resolvedSyncUrl = [MAURUrlTemplateResolver resolve:self.config.syncUrl location:nil queryParams:self.config.queryParams];
    NSString *syncMethod = self.config.syncHttpMethod ?: @"POST";
    [uploader sync:resolvedSyncUrl withTemplate:self.config._template withHttpHeaders:self.config.httpHeaders withMethod:syncMethod];
}

#pragma mark - Location transform

+ (void) setLocationTransform:(MAURLocationTransform _Nullable)transform
{
    s_locationTransform = transform;
}

+ (MAURLocationTransform _Nullable) locationTransform
{
    return s_locationTransform;
}

#pragma mark - MAURBackgroundSyncDelegate

- (void)backgroundSyncRequestedAbortUpdates:(MAURBackgroundSync *)task
{
    if (_delegate && [_delegate respondsToSelector:@selector(postLocationTaskRequestedAbortUpdates:)])
    {
        [_delegate postLocationTaskRequestedAbortUpdates:self];
    }
}

- (void)backgroundSyncHttpAuthorizationUpdates:(MAURBackgroundSync *)task
{
    if (_delegate && [_delegate respondsToSelector:@selector(postLocationTaskHttpAuthorizationUpdates:)])
    {
        [_delegate postLocationTaskHttpAuthorizationUpdates:self];
    }
}

@end
