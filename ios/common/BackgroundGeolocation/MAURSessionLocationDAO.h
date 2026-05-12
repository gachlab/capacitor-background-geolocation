//
//  MAURSessionLocationDAO.h
//  BackgroundGeolocation
//
//  Session storage for current route. Cleared on startSession/clearSession; not cleared by sync.
//

#ifndef MAURSessionLocationDAO_h
#define MAURSessionLocationDAO_h

#import <Foundation/Foundation.h>
#import "MAURLocation.h"

@interface MAURSessionLocationDAO : NSObject
+ (instancetype) sharedInstance;
- (id) init NS_UNAVAILABLE;
- (void) startSession;
- (void) clearSession;
- (BOOL) isSessionActive;
- (void) persistSessionLocation:(MAURLocation*)location;
- (NSArray<MAURLocation*>*) getSessionLocations;
- (NSInteger) getSessionLocationsCount;
@end

#endif /* MAURSessionLocationDAO_h */
