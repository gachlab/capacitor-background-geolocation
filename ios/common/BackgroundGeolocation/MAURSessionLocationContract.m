//
//  MAURSessionLocationContract.m
//  BackgroundGeolocation
//

#import <Foundation/Foundation.h>
#import "MAURSQLiteHelper.h"
#import "MAURSessionLocationContract.h"

@implementation MAURSessionLocationContract

+ (NSString*) createTableSQL
{
    NSArray *columns = @[
        @{ @"name": @LSC_COLUMN_NAME_ID, @"type": [SQLPrimaryKeyAutoIncColumnType sqlColumnWithType: kInteger]},
        @{ @"name": @LSC_COLUMN_NAME_TIME, @"type": [SQLColumnType sqlColumnWithType: kReal]},
        @{ @"name": @LSC_COLUMN_NAME_ACCURACY, @"type": [SQLColumnType sqlColumnWithType: kReal]},
        @{ @"name": @LSC_COLUMN_NAME_SPEED, @"type": [SQLColumnType sqlColumnWithType: kReal]},
        @{ @"name": @LSC_COLUMN_NAME_BEARING, @"type": [SQLColumnType sqlColumnWithType: kReal]},
        @{ @"name": @LSC_COLUMN_NAME_ALTITUDE, @"type": [SQLColumnType sqlColumnWithType: kReal]},
        @{ @"name": @LSC_COLUMN_NAME_LATITUDE, @"type": [SQLColumnType sqlColumnWithType: kReal]},
        @{ @"name": @LSC_COLUMN_NAME_LONGITUDE, @"type": [SQLColumnType sqlColumnWithType: kReal]},
        @{ @"name": @LSC_COLUMN_NAME_PROVIDER, @"type": [SQLColumnType sqlColumnWithType: kText]},
        @{ @"name": @LSC_COLUMN_NAME_LOCATION_PROVIDER, @"type": [SQLColumnType sqlColumnWithType: kText]},
        @{ @"name": @LSC_COLUMN_NAME_STATUS, @"type": [SQLColumnType sqlColumnWithType: kInteger]},
        @{ @"name": @LSC_COLUMN_NAME_RECORDED_AT, @"type": [SQLColumnType sqlColumnWithType: kInteger]}
    ];
    return [MAURSQLiteHelper createTableSqlStatement:@LSC_TABLE_NAME columns:columns];
}

@end
