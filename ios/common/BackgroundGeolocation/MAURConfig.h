//
//  MAURConfig.h
//  BackgroundGeolocation
//
//  Created by Marian Hello on 11/06/16.
//

#ifndef MAURConfig_h
#define MAURConfig_h

#import <Foundation/Foundation.h>
#import <CoreLocation/CoreLocation.h>

enum {
    DISTANCE_FILTER_PROVIDER = 0,
    ACTIVITY_PROVIDER = 1,
    RAW_PROVIDER = 2
};

@interface MAURConfig : NSObject <NSCopying>

@property NSNumber *stationaryRadius;
@property NSNumber *distanceFilter;
@property NSNumber *desiredAccuracy;
@property NSNumber *_debug;
@property NSString *activityType;
@property NSNumber *activitiesInterval;
@property NSNumber *_stopOnTerminate;
@property NSString *url;
@property NSString *syncUrl;
@property NSNumber *syncThreshold;
@property NSNumber *syncEnabled;
@property NSMutableDictionary* httpHeaders;
// v3.3 Phase 2: backend-agnostic HTTP transport
@property NSString *httpMethod;        // POST | GET | PUT | PATCH (default POST)
@property NSString *syncHttpMethod;    // POST | GET | PUT | PATCH (default POST)
@property NSString *httpMode;          // batch | single (default batch)
@property NSString *syncMode;          // batch | single (default batch)
@property NSMutableDictionary* queryParams; // static placeholder values for URL templating
// v3.4 Phase 3: location API modernization
@property NSNumber *_showsBackgroundLocationIndicator; // iOS 11+: show blue bar when app uses location in background
// v3.5 Phase 4: diagnostics
@property NSNumber *heartbeatInterval;        // ms; 0 disables (default)
@property NSString *mockLocationPolicy;       // allow | flag | drop (default allow)
// v4.0 Phase 6: driver insights — passed through as a dictionary; the facade reads keys at runtime.
@property NSDictionary *drivingEvents;
// v4.4: stamp battery percentage + charging state onto every location (default ON).
@property NSNumber *includeBattery;
// v4.5.2: provider hardening
/** 0-100. Activity-recognition transitions below this confidence are ignored. Default 50. */
@property NSNumber *activityConfidenceThreshold;
/** Discard fixes whose accuracy (m) is worse than this. nil = no filter. */
@property NSNumber *maxAcceptedAccuracy;
@property NSNumber *_saveBatteryOnBackground;
@property NSNumber *maxLocations;
@property NSNumber *_pauseLocationUpdates;
@property NSNumber *locationProvider;
@property NSObject *_template;

- (instancetype) initWithDefaults;
+ (instancetype) fromDictionary:(NSDictionary*)config;
+ (instancetype) merge:(MAURConfig*)config withConfig:(MAURConfig*)newConfig;
+ (NSDictionary*) getDefaultTemplate;

- (BOOL) hasStationaryRadius;
- (BOOL) hasDistanceFilter;
- (BOOL) hasDesiredAccuracy;
- (BOOL) hasDebug;
- (BOOL) hasActivityType;
- (BOOL) hasStopOnTerminate;
- (BOOL) hasUrl;
- (BOOL) hasValidUrl;
- (BOOL) hasSyncUrl;
- (BOOL) hasValidSyncUrl;
- (BOOL) hasSyncThreshold;
- (BOOL) hasSyncEnabled;
- (BOOL) syncEnabled;
- (BOOL) hasHttpHeaders;
- (BOOL) hasSaveBatteryOnBackground;
- (BOOL) hasShowsBackgroundLocationIndicator;
- (BOOL) showsBackgroundLocationIndicator;
- (BOOL) hasMaxLocations;
- (BOOL) hasPauseLocationUpdates;
- (BOOL) hasLocationProvider;
- (BOOL) hasTemplate;
- (BOOL) hasActivitiesInterval;
- (BOOL) isDebugging;
- (BOOL) stopOnTerminate;
- (BOOL) saveBatteryOnBackground;
- (BOOL) pauseLocationUpdates;
- (CLActivityType) decodeActivityType;
- (NSInteger) decodeDesiredAccuracy;
- (NSString*) getHttpHeadersAsString:(NSError * __autoreleasing *)outError;
- (NSString*) getTemplateAsString:(NSError * __autoreleasing *)outError;
- (NSDictionary*) toDictionary;

@end;

#endif /* MAURConfig_h */
