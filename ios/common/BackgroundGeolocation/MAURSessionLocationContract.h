//
//  MAURSessionLocationContract.h
//  BackgroundGeolocation
//
//  Session table for current recording route. Independent of sync; cleared on startSession/clearSession.
//

#ifndef MAURSessionLocationContract_h
#define MAURSessionLocationContract_h

#define LSC_TABLE_NAME "location_session"
#define LSC_COLUMN_NAME_ID "id"
#define LSC_COLUMN_NAME_TIME "time"
#define LSC_COLUMN_NAME_ACCURACY "accuracy"
#define LSC_COLUMN_NAME_SPEED "speed"
#define LSC_COLUMN_NAME_BEARING "bearing"
#define LSC_COLUMN_NAME_ALTITUDE "altitude"
#define LSC_COLUMN_NAME_LATITUDE "latitude"
#define LSC_COLUMN_NAME_LONGITUDE "longitude"
#define LSC_COLUMN_NAME_PROVIDER "provider"
#define LSC_COLUMN_NAME_LOCATION_PROVIDER "service_provider"
#define LSC_COLUMN_NAME_STATUS "valid"
#define LSC_COLUMN_NAME_RECORDED_AT "recorded_at"

@interface MAURSessionLocationContract : NSObject
+ (NSString*) createTableSQL;
@end

#endif /* MAURSessionLocationContract_h */
