//
//  MAURBackgroundGeolocationFacade.h
//
//  Created by Marian Hello on 04/06/16.
//  Version 2.0.0
//
//  According to apache license
//
//  This is class is using code from christocracy cordova-plugin-background-geolocation plugin
//  https://github.com/christocracy/cordova-plugin-background-geolocation

#ifndef MAURBackgroundGeolocationFacade_h
#define MAURBackgroundGeolocationFacade_h

#import "MAURProviderDelegate.h"
#import "MAURLocation.h"
#import "MAURConfig.h"

// v3.5 Phase 4: heartbeat notification. userInfo[@"location"] is the latest MAURLocation
// (may be absent if no fix has been received yet).
extern NSString * _Nonnull const MAURHeartbeatNotification;

// v4.0 Phase 6: driver-insight notifications. userInfo carries `location` and event-specific keys.
extern NSString * _Nonnull const MAURTripStartNotification;
extern NSString * _Nonnull const MAURTripEndNotification;       // userInfo: location, distance, durationMs
extern NSString * _Nonnull const MAURMovingNotification;
extern NSString * _Nonnull const MAURStoppedNotification;
extern NSString * _Nonnull const MAURSpeedingNotification;      // userInfo: location, speedKmh, limitKmh
extern NSString * _Nonnull const MAURProviderChangeNotification;// userInfo: provider
extern NSString * _Nonnull const MAURSOSNotification;           // userInfo: location, payload (NSDictionary)
// v4.1 GPS-derived sensor-like events. userInfo: location, value (double)
extern NSString * _Nonnull const MAURHardBrakeNotification;
extern NSString * _Nonnull const MAURRapidAccelerationNotification;
extern NSString * _Nonnull const MAURSharpTurnNotification;
extern NSString * _Nonnull const MAURPossibleCrashNotification;
// v4.2 sensor fusion events. userInfo: location, source ("gps"|"sensor"), value (double)
extern NSString * _Nonnull const MAURPhoneUsageWhileDrivingNotification;

@interface MAURBackgroundGeolocationFacade : NSObject

@property (weak, nonatomic) id<MAURProviderDelegate> delegate;
// Set by the Swift DrivingEventsDetector delegate to keep sensor-fusion in sync.
@property (nonatomic, assign) BOOL drivingTripActive;

- (BOOL) configure:(MAURConfig*)config error:(NSError * __autoreleasing *)outError;
- (BOOL) start:(NSError * __autoreleasing *)outError;
- (BOOL) stop:(NSError * __autoreleasing *)outError;
- (BOOL) locationServicesEnabled;
- (MAURLocationAuthorizationStatus) authorizationStatus;
- (BOOL) isStarted;
- (void) showAppSettings;
- (void) showLocationSettings;
- (void) switchMode:(MAUROperationalMode)mode;
- (MAURLocation*)getStationaryLocation;
- (NSArray<MAURLocation*>*) getLocations;
- (NSArray<MAURLocation*>*) getValidLocations;
- (NSArray<MAURLocation*>*) getValidLocationsAndDelete;
- (BOOL) deleteLocation:(NSNumber*)locationId error:(NSError * __autoreleasing *)outError;
- (BOOL) deleteAllLocations:(NSError * __autoreleasing *)outError;
- (MAURLocation*)getCurrentLocation:(int)timeout maximumAge:(long)maximumAge
                 enableHighAccuracy:(BOOL)enableHighAccuracy
                              error:(NSError * __autoreleasing *)outError;
- (MAURConfig*) getConfig;
- (NSArray*) getLogEntries:(NSInteger)limit;
- (NSArray*) getLogEntries:(NSInteger)limit fromLogEntryId:(NSInteger)entryId minLogLevelFromString:(NSString *)minLogLevel;
/** v4.0 Phase 6 — Trigger an SOS event with the latest known location and a user payload. */
- (void) triggerSOS:(NSDictionary * _Nullable)payload;
- (void) forceSync;
- (void) clearSync;
- (NSInteger) getPendingSyncCount;
- (void) startSession;
- (NSArray<MAURLocation*>*) getSessionLocations;
- (void) clearSession;
- (NSInteger) getSessionLocationsCount;
- (void) onAppTerminate;


/**
 * Sets a transform for each coordinate about to be committed (sent or saved for later sync).
 * You can use this for modifying the coordinates in any way.
 *
 * If the transform returns <code>nil</code>, it will prevent the location from being committed.
 * @param transform - the transform block
 */
+ (void) setLocationTransform:(MAURLocationTransform _Nullable)transform;
+ (MAURLocationTransform _Nullable) locationTransform;

@end

#endif /* MAURBackgroundGeolocationFacade_h */
