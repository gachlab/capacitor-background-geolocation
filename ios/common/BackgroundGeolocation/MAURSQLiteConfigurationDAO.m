//
//  MAURSQLiteConfigurationDAO.m
//  BackgroundGeolocation
//
//  Created by Marian Hello on 01/12/2017.
//  Copyright © 2017 mauron85. All rights reserved.
//

#import <sqlite3.h>
#import <CoreLocation/CoreLocation.h>
#import "MAURSQLiteHelper.h"
#import "MAURGeolocationOpenHelper.h"
#import "MAURSQLiteConfigurationDAO.h"
#import "MAURConfigurationContract.h"
#import "MAURConfig.h"

@implementation MAURSQLiteConfigurationDAO {
    FMDatabaseQueue* queue;
    MAURGeolocationOpenHelper *helper;
}

#pragma mark Singleton Methods

+ (instancetype) sharedInstance
{
    static MAURSQLiteConfigurationDAO *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[self alloc] init];
    });
    
    return instance;
}

- (id) init {
    if (self = [super init]) {
        helper = [[MAURGeolocationOpenHelper alloc] init];
        queue = [helper getWritableDatabase];
    }
    return self;
}

- (BOOL) persistConfiguration:(MAURConfig*)config
{
    __block BOOL success = NO;

    NSError *error = nil;
    
    NSString *httpHeadersString = [config getHttpHeadersAsString:&error];
    if (error != nil) {
        NSLog(@"Http headers serialization error: %@", error);
        return false;
    }

    NSString *templateString = [config getTemplateAsString:&error];
    if (error != nil) {
        NSLog(@"Template serialization error: %@", error);
        return false;
    }
   
    NSString *sql = @"INSERT OR REPLACE INTO " @CC_TABLE_NAME @" ("
        @CC_COLUMN_NAME_ID
        @COMMA_SEP @CC_COLUMN_NAME_RADIUS
        @COMMA_SEP @CC_COLUMN_NAME_DISTANCE_FILTER
        @COMMA_SEP @CC_COLUMN_NAME_DESIRED_ACCURACY
        @COMMA_SEP @CC_COLUMN_NAME_DEBUG
        @COMMA_SEP @CC_COLUMN_NAME_ACTIVITY_TYPE
        @COMMA_SEP @CC_COLUMN_NAME_NOTIF_TITLE
        @COMMA_SEP @CC_COLUMN_NAME_NOTIF_TEXT
        @COMMA_SEP @CC_COLUMN_NAME_NOTIF_ICON_LARGE
        @COMMA_SEP @CC_COLUMN_NAME_NOTIF_ICON_SMALL
        @COMMA_SEP @CC_COLUMN_NAME_NOTIF_COLOR
        @COMMA_SEP @CC_COLUMN_NAME_STOP_TERMINATE
        @COMMA_SEP @CC_COLUMN_NAME_START_BOOT
        @COMMA_SEP @CC_COLUMN_NAME_START_FOREGROUND
        @COMMA_SEP @CC_COLUMN_NAME_STOP_ON_STILL
        @COMMA_SEP @CC_COLUMN_NAME_LOCATION_PROVIDER
        @COMMA_SEP @CC_COLUMN_NAME_INTERVAL
        @COMMA_SEP @CC_COLUMN_NAME_FASTEST_INTERVAL
        @COMMA_SEP @CC_COLUMN_NAME_ACTIVITIES_INTERVAL
        @COMMA_SEP @CC_COLUMN_NAME_URL
        @COMMA_SEP @CC_COLUMN_NAME_SYNC_URL
        @COMMA_SEP @CC_COLUMN_NAME_SYNC_THRESHOLD
        @COMMA_SEP @CC_COLUMN_NAME_SYNC_ENABLED
        @COMMA_SEP @CC_COLUMN_NAME_HEADERS
        @COMMA_SEP @CC_COLUMN_NAME_SAVE_BATTERY
        @COMMA_SEP @CC_COLUMN_NAME_MAX_LOCATIONS
        @COMMA_SEP @CC_COLUMN_NAME_PAUSE_LOCATION_UPDATES
        @COMMA_SEP @CC_COLUMN_NAME_TEMPLATE
        @COMMA_SEP @CC_COLUMN_NAME_LAST_UPDATED_AT
        @COMMA_SEP @CC_COLUMN_NAME_CONFIG_JSON
        @") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,DateTime('now'),?)";

    [queue inDatabase:^(FMDatabase *database) {
        success = [database executeUpdate:sql,
                    @(1), // config id
                    [config hasStationaryRadius] ? config.stationaryRadius : @CC_COLUMN_NAME_NULLABLE,
                    [config hasDistanceFilter] ? config.distanceFilter : @CC_COLUMN_NAME_NULLABLE,
                    [config hasDesiredAccuracy] ? config.desiredAccuracy : @CC_COLUMN_NAME_NULLABLE,
                    [config hasDebug] ? config._debug : @CC_COLUMN_NAME_NULLABLE,
                    [config hasActivityType] ? config.activityType : @CC_COLUMN_NAME_NULLABLE,
                    [NSNull null], // unsupported notificationTitle
                    [NSNull null], // unsupported notificationText
                    [NSNull null], // unsupported notificationIconLarge
                    [NSNull null], // unsupported notificationIconSmall
                    [NSNull null], // unsupported notificationIconColor
                    [config hasStopOnTerminate] ? config._stopOnTerminate : @CC_COLUMN_NAME_NULLABLE,
                    [NSNull null], // unsupported startOnBoot,
                    [NSNull null], // unsupported startForeground
                    [NSNull null], // unsupported stopOnStillActivity
                    [config hasLocationProvider] ? config.locationProvider : @CC_COLUMN_NAME_NULLABLE,
                    [NSNull null], // unsupported interval
                    [NSNull null], // unsupported fastestInterval
                    [config hasActivitiesInterval] ? config.activitiesInterval : @CC_COLUMN_NAME_NULLABLE,
                    [config hasUrl] ? config.url : @CC_COLUMN_NAME_NULLABLE,
                    [config hasSyncUrl] ? config.syncUrl : @CC_COLUMN_NAME_NULLABLE,
                    [config hasSyncThreshold] ? config.syncThreshold : @CC_COLUMN_NAME_NULLABLE,
                    [config hasSyncEnabled] ? [NSNumber numberWithBool:[config syncEnabled]] : @CC_COLUMN_NAME_NULLABLE,
                    (httpHeadersString != nil) ? httpHeadersString : @CC_COLUMN_NAME_NULLABLE,
                    [config hasSaveBatteryOnBackground] ? config._saveBatteryOnBackground : @CC_COLUMN_NAME_NULLABLE,
                    [config hasMaxLocations] ? config.maxLocations : @CC_COLUMN_NAME_NULLABLE,
                    [config hasPauseLocationUpdates] ? config._pauseLocationUpdates : @CC_COLUMN_NAME_NULLABLE,
                    (templateString != nil) ? templateString : @CC_COLUMN_NAME_NULLABLE,
                    // v4.5: full Config as JSON for paridad con Android
                    [self serializeConfigToJson:config]
                ];

        if (success) {
            NSLog(@"Configuration persisted succesfuly");
        } else {
            NSLog(@"Persisting configuration has failed code: %d: message: %@", [database lastErrorCode], [database lastErrorMessage]);
        }
    }];
    
    return success;
}

- (MAURConfig*) retrieveConfiguration
{
    __block MAURConfig *config = nil;

    NSString *sql = @"SELECT "
    CC_COLUMN_NAME_ID
    @COMMA_SEP @CC_COLUMN_NAME_RADIUS
    @COMMA_SEP @CC_COLUMN_NAME_DISTANCE_FILTER
    @COMMA_SEP @CC_COLUMN_NAME_DESIRED_ACCURACY
    @COMMA_SEP @CC_COLUMN_NAME_DEBUG
    @COMMA_SEP @CC_COLUMN_NAME_ACTIVITY_TYPE
    @COMMA_SEP @CC_COLUMN_NAME_NOTIF_TITLE
    @COMMA_SEP @CC_COLUMN_NAME_NOTIF_TEXT
    @COMMA_SEP @CC_COLUMN_NAME_NOTIF_ICON_LARGE
    @COMMA_SEP @CC_COLUMN_NAME_NOTIF_ICON_SMALL
    @COMMA_SEP @CC_COLUMN_NAME_NOTIF_COLOR
    @COMMA_SEP @CC_COLUMN_NAME_STOP_TERMINATE
    @COMMA_SEP @CC_COLUMN_NAME_START_BOOT
    @COMMA_SEP @CC_COLUMN_NAME_START_FOREGROUND
    @COMMA_SEP @CC_COLUMN_NAME_STOP_ON_STILL
    @COMMA_SEP @CC_COLUMN_NAME_LOCATION_PROVIDER
    @COMMA_SEP @CC_COLUMN_NAME_INTERVAL
    @COMMA_SEP @CC_COLUMN_NAME_FASTEST_INTERVAL
    @COMMA_SEP @CC_COLUMN_NAME_ACTIVITIES_INTERVAL
    @COMMA_SEP @CC_COLUMN_NAME_URL
    @COMMA_SEP @CC_COLUMN_NAME_SYNC_URL
    @COMMA_SEP @CC_COLUMN_NAME_SYNC_THRESHOLD
    @COMMA_SEP @CC_COLUMN_NAME_SYNC_ENABLED
    @COMMA_SEP @CC_COLUMN_NAME_HEADERS
    @COMMA_SEP @CC_COLUMN_NAME_SAVE_BATTERY
    @COMMA_SEP @CC_COLUMN_NAME_MAX_LOCATIONS
    @COMMA_SEP @CC_COLUMN_NAME_PAUSE_LOCATION_UPDATES
    @COMMA_SEP @CC_COLUMN_NAME_TEMPLATE
    @COMMA_SEP @CC_COLUMN_NAME_CONFIG_JSON
    @" FROM " @CC_TABLE_NAME @" WHERE " @CC_COLUMN_NAME_ID @" = 1";
    
    [queue inDatabase:^(FMDatabase *database) {
        FMResultSet *rs = [database executeQuery:sql];
        while([rs next]) {
            config = [[MAURConfig alloc] init];
            if ([self isNonNull:rs columnIndex:1]) {
                config.stationaryRadius = [NSNumber numberWithInt:[rs intForColumnIndex:1]];
            }
            if ([self isNonNull:rs columnIndex:2]) {
                config.distanceFilter = [NSNumber numberWithInt:[rs intForColumnIndex:2]];
            }
            if ([self isNonNull:rs columnIndex:3]) {
                config.desiredAccuracy = [NSNumber numberWithInt:[rs intForColumnIndex:3]];
            }
            if ([self isNonNull:rs columnIndex:4]) {
                config._debug = [NSNumber numberWithBool:[rs intForColumnIndex:4] == 1 ? YES : NO];
            }
            if ([self isNonNull:rs columnIndex:5]) {
                config.activityType = [rs stringForColumnIndex:5];
            }
            if ([self isNonNull:rs columnIndex:11]) {
                config._stopOnTerminate =  [NSNumber numberWithBool:[rs intForColumnIndex:11] == 1 ? YES : NO];
            }
            if ([self isNonNull:rs columnIndex:15]) {
                config.locationProvider = [NSNumber numberWithInt:[rs intForColumnIndex:15]];
            }
            if ([self isNonNull:rs columnIndex:18]) {
                config.activitiesInterval = [NSNumber numberWithInt:[rs intForColumnIndex:18]];
            }
            if ([self isNonNull:rs columnIndex:19]) {
                config.url = [rs stringForColumnIndex:19];
            }
            if ([self isNonNull:rs columnIndex:20]) {
                config.syncUrl = [rs stringForColumnIndex:20];
            }
            if ([self isNonNull:rs columnIndex:21]) {
                config.syncThreshold = [NSNumber numberWithInt:[rs intForColumnIndex:21]];
            }
            id syncEnabledVal = [rs objectForColumnIndex:22];
            if (syncEnabledVal != nil && syncEnabledVal != [NSNull null]) {
                config.syncEnabled = [NSNumber numberWithBool:[rs intForColumnIndex:22] == 1 ? YES : NO];
            }
            if ([self isNonNull:rs columnIndex:23]) {
                NSString *httpHeadersString = [rs stringForColumnIndex:23];
                if (httpHeadersString != nil) {
                    NSData *jsonHttpHeaders = [httpHeadersString dataUsingEncoding:NSUTF8StringEncoding];
                    config.httpHeaders = [NSJSONSerialization JSONObjectWithData:jsonHttpHeaders options:0 error:nil];
                }
            }
            if ([self isNonNull:rs columnIndex:24]) {
                config._saveBatteryOnBackground = [NSNumber numberWithBool:[rs intForColumnIndex:24] == 1 ? YES : NO];
            }
            if ([self isNonNull:rs columnIndex:25]) {
                config.maxLocations = [NSNumber numberWithInt:[rs intForColumnIndex:25]];
            }
            if ([self isNonNull:rs columnIndex:26]) {
                config._pauseLocationUpdates = [NSNumber numberWithBool:[rs intForColumnIndex:26] == 1 ? YES : NO];
            }
            if ([self isNonNull:rs columnIndex:27]) {
                NSString *templateAsString = [rs stringForColumnIndex:27];
                if (templateAsString != nil) {
                    NSData *jsonTemplate = [templateAsString dataUsingEncoding:NSUTF8StringEncoding];
                    config._template = [NSJSONSerialization JSONObjectWithData:jsonTemplate options:0 error:nil];
                }
            }
            // v4.5: rehydrate post-3.2 keys from config_json blob (paridad Android).
            // Index 28 is the new column. Strict NULL check (no NULLHACK sentinel for JSON column).
            if (![rs columnIndexIsNull:28]) {
                NSString *jsonString = [rs stringForColumnIndex:28];
                if (jsonString != nil && jsonString.length > 0) {
                    [self applyConfigJson:jsonString to:config];
                }
            }
        }

        [rs close];
    }];

    return config;
}

// v4.5: serialize all post-3.2 keys to JSON for storage. Mirrors Android ConfigJsonMapper.
- (NSString*) serializeConfigToJson:(MAURConfig*)config
{
    NSMutableDictionary *j = [NSMutableDictionary dictionary];
    if (config.httpMethod != nil)       j[@"httpMethod"]       = config.httpMethod;
    if (config.syncHttpMethod != nil)   j[@"syncHttpMethod"]   = config.syncHttpMethod;
    if (config.httpMode != nil)         j[@"httpMode"]         = config.httpMode;
    if (config.syncMode != nil)         j[@"syncMode"]         = config.syncMode;
    if (config.queryParams != nil)      j[@"queryParams"]      = config.queryParams;
    if (config.heartbeatInterval != nil) j[@"heartbeatInterval"] = config.heartbeatInterval;
    if (config.mockLocationPolicy != nil) j[@"mockLocationPolicy"] = config.mockLocationPolicy;
    if (config.drivingEvents != nil)    j[@"drivingEvents"]    = config.drivingEvents;
    if (config.includeBattery != nil)   j[@"includeBattery"]   = config.includeBattery;
    if (config._showsBackgroundLocationIndicator != nil) j[@"showsBackgroundLocationIndicator"] = config._showsBackgroundLocationIndicator;
    NSError *err = nil;
    NSData *data = [NSJSONSerialization dataWithJSONObject:j options:0 error:&err];
    if (err != nil || data == nil) return @"";
    return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
}

- (void) applyConfigJson:(NSString*)jsonString to:(MAURConfig*)config
{
    NSData *data = [jsonString dataUsingEncoding:NSUTF8StringEncoding];
    NSError *err = nil;
    NSDictionary *j = [NSJSONSerialization JSONObjectWithData:data options:0 error:&err];
    if (err != nil || ![j isKindOfClass:[NSDictionary class]]) return;
    if (j[@"httpMethod"])         config.httpMethod         = j[@"httpMethod"];
    if (j[@"syncHttpMethod"])     config.syncHttpMethod     = j[@"syncHttpMethod"];
    if (j[@"httpMode"])           config.httpMode           = j[@"httpMode"];
    if (j[@"syncMode"])           config.syncMode           = j[@"syncMode"];
    if ([j[@"queryParams"] isKindOfClass:[NSDictionary class]]) config.queryParams = [j[@"queryParams"] mutableCopy];
    if (j[@"heartbeatInterval"]) config.heartbeatInterval   = j[@"heartbeatInterval"];
    if (j[@"mockLocationPolicy"]) config.mockLocationPolicy = j[@"mockLocationPolicy"];
    if ([j[@"drivingEvents"] isKindOfClass:[NSDictionary class]]) config.drivingEvents = j[@"drivingEvents"];
    if (j[@"includeBattery"] != nil) config.includeBattery = j[@"includeBattery"];
    if (j[@"showsBackgroundLocationIndicator"] != nil) config._showsBackgroundLocationIndicator = j[@"showsBackgroundLocationIndicator"];
}

- (BOOL) clearDatabase
{
    __block BOOL success;
    
    [queue inDatabase:^(FMDatabase *database) {
        NSString *sql = [NSString stringWithFormat: @"DELETE FROM %@", @CC_TABLE_NAME];
        if (![database executeStatements:sql]) {
            NSLog(@"%@ failed code: %d: message: %@", sql, [database lastErrorCode], [database lastErrorMessage]);
            success = NO;
        } else {
            success = YES;
        }
    }];
    
    return success;
}

- (NSString*) getDatabaseName
{
    return [helper getDatabaseName];
}

- (NSString*) getDatabasePath
{
    return [helper getDatabasePath];
}

- (BOOL) isNonNull:(FMResultSet*)resultSet columnIndex:(int)index
{
    if (![[resultSet stringForColumnIndex:index] isEqualToString:@CC_COLUMN_NAME_NULLABLE]) {
        return YES;
    }

    return NO;
}

- (void) dealloc {
    [helper close];
    [queue close];
    helper = nil;
    queue = nil;
}

@end
