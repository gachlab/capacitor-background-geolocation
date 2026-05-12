//
//  MAURSessionLocationDAO.m
//  BackgroundGeolocation
//

#import <Foundation/Foundation.h>
#import "MAURGeolocationOpenHelper.h"
#import "MAURSessionLocationContract.h"
#import "MAURSessionLocationDAO.h"
#import "MAURLocation.h"
#import "FMDB.h"

static NSString *const kSessionActiveKey = @"bgloc_session_active";

@interface MAURSessionLocationDAO ()
- (instancetype)initPrivate;
@end

@implementation MAURSessionLocationDAO {
    FMDatabaseQueue* queue;
    MAURGeolocationOpenHelper *helper;
}

+ (instancetype) sharedInstance
{
    static MAURSessionLocationDAO *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[self alloc] initPrivate];
    });
    return instance;
}

- (instancetype) initPrivate
{
    if (self = [super init]) {
        helper = [[MAURGeolocationOpenHelper alloc] init];
        queue = [helper getWritableDatabase];
    }
    return self;
}

- (void) startSession
{
    [queue inDatabase:^(FMDatabase *database) {
        NSString *sql = [NSString stringWithFormat:@"DELETE FROM %@", @LSC_TABLE_NAME];
        [database executeUpdate:sql];
    }];
    [[NSUserDefaults standardUserDefaults] setBool:YES forKey:kSessionActiveKey];
    [[NSUserDefaults standardUserDefaults] synchronize];
}

- (void) clearSession
{
    [queue inDatabase:^(FMDatabase *database) {
        NSString *sql = [NSString stringWithFormat:@"DELETE FROM %@", @LSC_TABLE_NAME];
        [database executeUpdate:sql];
    }];
    [[NSUserDefaults standardUserDefaults] setBool:NO forKey:kSessionActiveKey];
    [[NSUserDefaults standardUserDefaults] synchronize];
}

- (BOOL) isSessionActive
{
    return [[NSUserDefaults standardUserDefaults] boolForKey:kSessionActiveKey];
}

- (void) persistSessionLocation:(MAURLocation*)location
{
    if (![self isSessionActive]) return;
    if (!location || !location.time) return;
    NSTimeInterval timestamp = [[NSDate date] timeIntervalSince1970];
    NSNumber *recordedAt = [NSNumber numberWithDouble:timestamp];

    NSString *sql = @"INSERT INTO " @LSC_TABLE_NAME @" ("
        @LSC_COLUMN_NAME_TIME @"," @LSC_COLUMN_NAME_ACCURACY @"," @LSC_COLUMN_NAME_SPEED @","
        @LSC_COLUMN_NAME_BEARING @"," @LSC_COLUMN_NAME_ALTITUDE @"," @LSC_COLUMN_NAME_LATITUDE @","
        @LSC_COLUMN_NAME_LONGITUDE @"," @LSC_COLUMN_NAME_PROVIDER @"," @LSC_COLUMN_NAME_LOCATION_PROVIDER @","
        @LSC_COLUMN_NAME_STATUS @"," @LSC_COLUMN_NAME_RECORDED_AT
        @") VALUES (?,?,?,?,?,?,?,?,?,?,?)";

    [queue inDatabase:^(FMDatabase *database) {
        [database executeUpdate:sql,
            [NSNumber numberWithDouble:[location.time timeIntervalSince1970]],
            location.accuracy ?: @0,
            location.speed ?: @0,
            location.heading ?: @0,
            location.altitude ?: @0,
            location.latitude ?: @0,
            location.longitude ?: @0,
            location.provider ?: [NSNull null],
            location.locationProvider ? [NSString stringWithFormat:@"%@", location.locationProvider] : [NSNull null],
            @(1),
            recordedAt
        ];
    }];
}

- (NSArray<MAURLocation*>*) getSessionLocations
{
    __block NSMutableArray* locations = [[NSMutableArray alloc] init];
    NSString *sql = @"SELECT " @LSC_COLUMN_NAME_ID @"," @LSC_COLUMN_NAME_TIME @"," @LSC_COLUMN_NAME_ACCURACY @","
        @LSC_COLUMN_NAME_SPEED @"," @LSC_COLUMN_NAME_BEARING @"," @LSC_COLUMN_NAME_ALTITUDE @","
        @LSC_COLUMN_NAME_LATITUDE @"," @LSC_COLUMN_NAME_LONGITUDE @"," @LSC_COLUMN_NAME_PROVIDER @","
        @LSC_COLUMN_NAME_LOCATION_PROVIDER @"," @LSC_COLUMN_NAME_STATUS @"," @LSC_COLUMN_NAME_RECORDED_AT
        @" FROM " @LSC_TABLE_NAME @" ORDER BY " @LSC_COLUMN_NAME_RECORDED_AT;

    [queue inDatabase:^(FMDatabase *database) {
        FMResultSet *rs = [database executeQuery:sql];
        while ([rs next]) {
            MAURLocation *loc = [self convertToLocation:rs];
            [locations addObject:loc];
        }
        [rs close];
    }];
    return locations;
}

- (NSInteger) getSessionLocationsCount
{
    __block NSInteger count = 0;
    NSString *sql = [NSString stringWithFormat:@"SELECT COUNT(*) FROM %@", @LSC_TABLE_NAME];
    [queue inDatabase:^(FMDatabase *database) {
        FMResultSet *rs = [database executeQuery:sql];
        if ([rs next]) {
            count = [rs intForColumnIndex:0];
        }
        [rs close];
    }];
    return count;
}

- (MAURLocation*) convertToLocation:(FMResultSet*)rs
{
    MAURLocation *location = [[MAURLocation alloc] init];
    location.locationId = [NSNumber numberWithLongLong:[rs longLongIntForColumnIndex:0]];
    NSTimeInterval timestamp = [rs doubleForColumnIndex:1];
    location.time = [NSDate dateWithTimeIntervalSince1970:timestamp];
    location.accuracy = [NSNumber numberWithDouble:[rs doubleForColumnIndex:2]];
    location.speed = [NSNumber numberWithDouble:[rs doubleForColumnIndex:3]];
    location.heading = [NSNumber numberWithDouble:[rs doubleForColumnIndex:4]];
    location.altitude = [NSNumber numberWithDouble:[rs doubleForColumnIndex:5]];
    location.latitude = [NSNumber numberWithDouble:[rs doubleForColumnIndex:6]];
    location.longitude = [NSNumber numberWithDouble:[rs doubleForColumnIndex:7]];
    location.provider = [rs stringForColumnIndex:8];
    location.locationProvider = [NSNumber numberWithInt:[rs intForColumnIndex:9]];
    location.isValid = [rs intForColumnIndex:10] == 1 ? YES : NO;
    NSTimeInterval recordedAt = [rs longForColumnIndex:11];
    location.recordedAt = [NSDate dateWithTimeIntervalSince1970:recordedAt];
    return location;
}

@end
