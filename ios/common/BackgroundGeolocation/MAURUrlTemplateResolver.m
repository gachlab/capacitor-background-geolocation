//
//  MAURUrlTemplateResolver.m
//  BackgroundGeolocation
//

#import "MAURUrlTemplateResolver.h"
#import "MAURLocation.h"

@implementation MAURUrlTemplateResolver

+ (NSString *)resolve:(NSString *)urlTemplate
             location:(MAURLocation *)location
          queryParams:(NSDictionary *)queryParams
{
    if (urlTemplate == nil || urlTemplate.length == 0) return urlTemplate;

    NSDictionary *ctx = [self buildContext:location queryParams:queryParams];

    NSError *error = nil;
    NSRegularExpression *regex = [NSRegularExpression
        regularExpressionWithPattern:@"\\{([a-zA-Z0-9_]+)\\}"
        options:0
        error:&error];
    if (regex == nil) return urlTemplate;

    NSMutableString *result = [NSMutableString stringWithString:urlTemplate];
    NSArray *matches = [regex matchesInString:urlTemplate
                                      options:0
                                        range:NSMakeRange(0, urlTemplate.length)];

    // Iterate in reverse so ranges don't shift.
    for (NSTextCheckingResult *match in [matches reverseObjectEnumerator]) {
        NSString *key = [urlTemplate substringWithRange:[match rangeAtIndex:1]];
        NSString *value = ctx[key];
        if (value != nil) {
            NSString *encoded = [self urlEncode:value];
            [result replaceCharactersInRange:[match range] withString:encoded];
        }
        // else: leave the placeholder as-is.
    }
    return result;
}

+ (NSDictionary *)buildContext:(MAURLocation *)loc queryParams:(NSDictionary *)queryParams
{
    NSMutableDictionary *ctx = [NSMutableDictionary dictionary];

    // queryParams first; location-derived values can override below.
    if (queryParams != nil) {
        for (id key in queryParams) {
            id value = queryParams[key];
            if (value != nil && value != [NSNull null]) {
                ctx[key] = [NSString stringWithFormat:@"%@", value];
            }
        }
    }

    if (loc != nil) {
        if (loc.latitude != nil) {
            ctx[@"latitude"] = [loc.latitude stringValue];
            ctx[@"lat"] = [loc.latitude stringValue];
        }
        if (loc.longitude != nil) {
            ctx[@"longitude"] = [loc.longitude stringValue];
            ctx[@"lon"] = [loc.longitude stringValue];
        }
        if (loc.time != nil) {
            long long ms = (long long)([loc.time timeIntervalSince1970] * 1000.0);
            ctx[@"time"] = [NSString stringWithFormat:@"%lld", ms];
            ctx[@"timestamp"] = [NSString stringWithFormat:@"%lld", ms];
            ctx[@"timestamp_iso"] = [self isoUtc:loc.time];
        }
        if (loc.speed != nil) ctx[@"speed"] = [loc.speed stringValue];
        if (loc.altitude != nil) ctx[@"altitude"] = [loc.altitude stringValue];
        if (loc.heading != nil) ctx[@"bearing"] = [loc.heading stringValue];
        if (loc.accuracy != nil) ctx[@"accuracy"] = [loc.accuracy stringValue];
        if (loc.provider != nil) ctx[@"provider"] = loc.provider;
    }

    return ctx;
}

+ (NSString *)urlEncode:(NSString *)s
{
    if (s == nil) return @"";
    NSCharacterSet *allowed = [NSCharacterSet URLQueryAllowedCharacterSet];
    NSString *enc = [s stringByAddingPercentEncodingWithAllowedCharacters:allowed];
    // URLQueryAllowedCharacterSet leaves "+" and "&" unescaped which is unsafe in values.
    enc = [enc stringByReplacingOccurrencesOfString:@"&" withString:@"%26"];
    enc = [enc stringByReplacingOccurrencesOfString:@"+" withString:@"%2B"];
    return enc != nil ? enc : s;
}

+ (NSString *)isoUtc:(NSDate *)date
{
    static NSDateFormatter *fmt = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        fmt = [[NSDateFormatter alloc] init];
        fmt.dateFormat = @"yyyy-MM-dd'T'HH:mm:ss'Z'";
        fmt.timeZone = [NSTimeZone timeZoneWithAbbreviation:@"UTC"];
        fmt.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    });
    return [fmt stringFromDate:date];
}

@end
