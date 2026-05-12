//
//  MAURGeolocationOpenHelper.m
//  BackgroundGeolocation
//
//  Created by Marian Hello on 27/06/16.
//  Copyright © 2016 mauron85. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "MAURGeolocationOpenHelper.h"
#import "MAURLocationContract.h"
#import "MAURConfigurationContract.h"
#import "MAURSessionLocationContract.h"

@implementation MAURGeolocationOpenHelper

static NSString *const kDatabaseName = @"cordova_bg_geolocation.db";
// v4.5.0: bumped to 7 to add events_json + battery_level + is_charging on locations,
// and config_json on configuration (paridad con Android v22).
static NSInteger const kDatabaseVersion = 7;

- (instancetype)init
{
    self = [super init:kDatabaseName version:kDatabaseVersion];
    return self;
}

- (void) drop:(NSString*)table inDatabase:(FMDatabase*)database
{
    NSString *sql = [NSString stringWithFormat: @"DROP TABLE IF EXISTS %@", table];
    if (![database executeStatements:sql]) {
        NSLog(@"%@ failed code: %d: message: %@", sql, [database lastErrorCode], [database lastErrorMessage]);
    }
}

- (void) onCreate:(FMDatabaseQueue*)queue
{
    [queue inDatabase:^(FMDatabase *database) {
        // because of some legacy code we have to drop table
        [self drop:@LC_TABLE_NAME inDatabase:database];
        
        NSString *sql = [@[
                           [MAURLocationContract createTableSQL],
                           [MAURConfigurationContract createTableSQL],
                           @"CREATE INDEX recorded_at_idx ON " @LC_TABLE_NAME @" (" @LC_COLUMN_NAME_RECORDED_AT @")",
                           [MAURSessionLocationContract createTableSQL],
                           @"CREATE INDEX session_recorded_at_idx ON " @LSC_TABLE_NAME @" (" @LSC_COLUMN_NAME_RECORDED_AT @")"
                           ]  componentsJoinedByString:@";"];
        if (![database executeStatements:sql]) {
            NSLog(@"%@ failed code: %d: message: %@", sql, [database lastErrorCode], [database lastErrorMessage]);
        }
    }];
}

- (void) onDowngrade:(FMDatabaseQueue*)queue fromVersion:(NSInteger)oldVersion toVersion:(NSInteger)newVersion
{
    NSLog(@"Downgrading geolocation db oldVersion: %ld, newVersion: %ld", oldVersion, newVersion);

    NSString *sql = [@[
         @"DROP TABLE IF EXISTS " @LC_TABLE_NAME,
         @"DROP TABLE IF EXISTS " @LSC_TABLE_NAME
    ] componentsJoinedByString:@";"];

    [queue inDatabase:^(FMDatabase *database) {
        if (![database executeStatements:sql]) {
            NSLog(@"Db downgrade failed code: %d: message: %@", [database lastErrorCode], [database lastErrorMessage]);
        } else {
            [self onCreate:queue];
        }
    }];
}

- (void) onUpgrade:(FMDatabaseQueue*)queue fromVersion:(NSInteger)oldVersion toVersion:(NSInteger)newVersion
{
    NSLog(@"Upgrading geolocation db oldVersion: %ld, newVersion: %ld", oldVersion, newVersion);
    NSMutableArray *sql = [[NSMutableArray alloc] init];
    
    switch (oldVersion) {
        case 1:
            [sql addObjectsFromArray: @[
                @"ALTER TABLE " @LC_TABLE_NAME @" ADD COLUMN " @LC_COLUMN_NAME_RECORDED_AT @" INTEGER",
                @"UPDATE " @LC_TABLE_NAME @" SET " @LC_COLUMN_NAME_RECORDED_AT @" =" @LC_COLUMN_NAME_TIME,
                @"CREATE INDEX recorded_at_idx ON " @LC_TABLE_NAME @" (" @LC_COLUMN_NAME_RECORDED_AT @")",
                @"DROP INDEX IF EXISTS time_idx"
            ]];
        case 2:
            [sql addObjectsFromArray: @[
                [MAURConfigurationContract createTableSQL]
            ]];
        case 3:
            [sql addObjectsFromArray: @[
                [NSString stringWithFormat:@"ALTER TABLE %s ADD COLUMN %s INTEGER", CC_TABLE_NAME, CC_COLUMN_NAME_SYNC_ENABLED]
            ]];
        case 4:
            [sql addObjectsFromArray: @[
                [MAURSessionLocationContract createTableSQL],
                [NSString stringWithFormat:@"CREATE INDEX session_recorded_at_idx ON %@ (%@)", @LSC_TABLE_NAME, @LSC_COLUMN_NAME_RECORDED_AT]
            ]];
        case 5:
            // v4.5.0: persist driving events / battery / charging on locations.
            [sql addObjectsFromArray: @[
                @"ALTER TABLE " @LC_TABLE_NAME @" ADD COLUMN " @LC_COLUMN_NAME_EVENTS_JSON @" TEXT",
                @"ALTER TABLE " @LC_TABLE_NAME @" ADD COLUMN " @LC_COLUMN_NAME_BATTERY_LEVEL @" INTEGER",
                @"ALTER TABLE " @LC_TABLE_NAME @" ADD COLUMN " @LC_COLUMN_NAME_IS_CHARGING @" INTEGER"
            ]];
        case 6:
            // v4.5.0: full config persisted as JSON blob (paridad con Android config_json).
            [sql addObjectsFromArray: @[
                [NSString stringWithFormat:@"ALTER TABLE %s ADD COLUMN %s TEXT", CC_TABLE_NAME, CC_COLUMN_NAME_CONFIG_JSON]
            ]];
            break; // break only for previous db version (cascade statements)
        default:
            return;
    }

    [queue inDatabase:^(FMDatabase *database) {
        NSString *stmt = [sql componentsJoinedByString:@";"];
        if (![database executeStatements:stmt]) {
            NSLog(@"Db upgrade failed code: %d: message: %@", [database lastErrorCode], [database lastErrorMessage]);
        }
    }];
}

@end
