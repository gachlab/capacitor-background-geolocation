//
//  MAURUrlTemplateResolver.h
//  BackgroundGeolocation
//
//  Resolves placeholders like {lat}, {lon}, {timestamp_iso}, {device_id}, ...
//  in a URL template using a single MAURLocation and an optional queryParams dictionary.
//
//  Placeholders not found in the location/queryParams are left as-is so partial templates
//  (e.g. only static keys for batch mode) keep working.
//

#ifndef MAURUrlTemplateResolver_h
#define MAURUrlTemplateResolver_h

#import <Foundation/Foundation.h>

@class MAURLocation;

@interface MAURUrlTemplateResolver : NSObject

/**
 * Resolve placeholders in `template` using values from `location` and `queryParams`.
 * Either may be nil. Returns the resolved URL (or the original template if no placeholders).
 */
+ (NSString *)resolve:(NSString *)urlTemplate
             location:(MAURLocation *)location
          queryParams:(NSDictionary *)queryParams;

@end

#endif /* MAURUrlTemplateResolver_h */
