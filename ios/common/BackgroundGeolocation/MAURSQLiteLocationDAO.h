//
//  MAURSQLiteLocationDAO.h
//  BackgroundGeolocation
//
//  Created by Marian Hello on 10/06/16.
//

#ifndef MAURSQLiteLocationDAO_h
#define MAURSQLiteLocationDAO_h

#import <Foundation/Foundation.h>
#import "FMDB.h"
#import "MAURLocation.h"

@class Location;

@interface MAURSQLiteLocationDAO : NSObject

+ (instancetype) sharedInstance;
- (id) init NS_UNAVAILABLE;
- (NSArray<MAURLocation*>*) getAllLocations;
- (NSArray<MAURLocation*>*) getLocationsForSync;
- (NSArray<MAURLocation*>*) getValidLocations;
- (NSNumber*) getLocationsForSyncCount;
- (NSNumber*) persistLocation:(MAURLocation*)location;
- (NSNumber*) persistLocation:(MAURLocation*)location limitRows:(NSInteger)maxRows;
- (BOOL) deleteLocation:(NSNumber*)locationId error:(NSError * __autoreleasing *)outError;
- (BOOL) deleteAllLocations:(NSError * __autoreleasing *)outError;
/** Mark all locations pending sync (PostPending) as deleted. Clears the sync queue without sending. */
- (BOOL) deletePendingSyncLocations:(NSError * __autoreleasing *)outError;
/** v4.5.1 — soft-delete only sync-pending rows whose `recorded_at` is <= cutoff (UNIX seconds).
 *  Used after a successful background-sync POST so locations persisted DURING the upload (race
 *  window) are NOT incorrectly marked deleted. */
- (BOOL) deleteSyncedLocationsBefore:(NSTimeInterval)cutoff error:(NSError * __autoreleasing *)outError;
/** v4.5.1 — undo the in-flight SyncPending state when the upload failed. SyncPending → PostPending
 *  so the next sync window re-tries them. Without this, a network failure during background-sync
 *  would silently drop every pending location. */
- (BOOL) restoreFailedSyncLocations:(NSError * __autoreleasing *)outError;
/** v4.5.1 — recover SyncPending rows that got stuck (app/process killed between getLocationsForSync
 *  and the upload's success/failure callback). Rows whose `recorded_at` is older than `cutoff`
 *  (UNIX seconds) are restored to PostPending so they get retried. Call before each sync window. */
- (BOOL) restoreStaleSyncLocationsOlderThan:(NSTimeInterval)cutoff error:(NSError * __autoreleasing *)outError;
- (BOOL) clearDatabase;
- (NSString*) getDatabaseName;
- (NSString*) getDatabasePath;
// private
- (NSString*) getLocationSelectString;
- (MAURLocation*) convertToLocation:(FMResultSet*)rs;

@end

#endif /* MAURSQLiteLocationDAO_h */
