//
//  MAURBackgroundSync.m
//
//  Created by Marian Hello on 07/07/16.
//  Copyright © 2016 mauron85. All rights reserved.
//

#import "UIKit/UIKit.h"
#import "MAURLogging.h"
#import "MAURBackgroundSync.h"
#import "MAURSQLiteLocationDAO.h"
#import <objc/runtime.h>

NSString * const MAURBackgroundSyncDidStartNotification    = @"MAURBackgroundSyncDidStart";
NSString * const MAURBackgroundSyncDidSucceedNotification  = @"MAURBackgroundSyncDidSucceed";
NSString * const MAURBackgroundSyncDidFailNotification     = @"MAURBackgroundSyncDidFail";
NSString * const MAURBackgroundSyncDidProgressNotification = @"MAURBackgroundSyncDidProgress";

@interface MAURBackgroundSync ()  <NSURLSessionDelegate, NSURLSessionTaskDelegate>
{
    NSURLSession *urlSession;
    NSMutableArray *tasks;
}
@end

@implementation MAURBackgroundSync

- (instancetype) init
{
    if(!(self = [super init])) return nil;

    // v3.5 Phase 4: previously `tasks` was never allocated; addObject/removeObject/cancel/status
    // silently no-op'd on nil. Allocate now so cancel and status actually work.
    tasks = [[NSMutableArray alloc] init];

    NSURLSessionConfiguration *conf = [NSURLSessionConfiguration backgroundSessionConfiguration:@"com.marianhello.session"];
    conf.allowsCellularAccess = YES;
    urlSession = [NSURLSession sessionWithConfiguration:conf delegate:self delegateQueue:[NSOperationQueue mainQueue]];

    return self;
}

- (void)start
{
    __block UIBackgroundTaskIdentifier bgTask = [[UIApplication sharedApplication] beginBackgroundTaskWithExpirationHandler:^{
        [[UIApplication sharedApplication] endBackgroundTask:bgTask];
    }];    
    
    [urlSession getTasksWithCompletionHandler:^(NSArray *dataTasks, NSArray *uploadTasks, NSArray *downloadTasks) {
        for(NSURLSessionUploadTask *task in uploadTasks) {
            DDLogInfo(@"Restored upload task %zu for %@", (unsigned long)task.taskIdentifier, task.originalRequest.URL);
            [tasks addObject:task];
            [task resume];
        }
        
        [[UIApplication sharedApplication] endBackgroundTask:bgTask];
    }];
}

- (void)cancel
{
    for(NSURLSessionTask *task in tasks) {
        [task cancel];
    }
}

- (void) sync:(NSString * _Nonnull)url withTemplate:(id)locationTemplate withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders
{
    [self sync:url withTemplate:locationTemplate withHttpHeaders:httpHeaders withMethod:@"POST"];
}

- (void) sync:(NSString * _Nonnull)url withTemplate:(id)locationTemplate withHttpHeaders:(NSMutableDictionary * _Nullable)httpHeaders withMethod:(NSString * _Nullable)method
{
    MAURSQLiteLocationDAO* locationDAO = [MAURSQLiteLocationDAO sharedInstance];
    NSArray *locations = [locationDAO getLocationsForSync];

    NSString *resolvedMethod = (method != nil && method.length > 0) ? [method uppercaseString] : @"POST";

    NSDateFormatter *dateFormatter = [[NSDateFormatter alloc] init];
    dateFormatter.locale = [[NSLocale alloc] initWithLocaleIdentifier:@"en_US_POSIX"];
    dateFormatter.dateFormat = @"yyyyMMdd_HHmmss";
    dateFormatter.timeZone = [NSTimeZone timeZoneForSecondsFromGMT:0];
    NSString *dateString = [dateFormatter stringFromDate:[NSDate date]];

    // Send one upload task per location so each request body is a single
    // JSON object — matching what the single-POST path (MAURPostLocationTask)
    // sends and what strict REST APIs (Fastify/Hapi schema validation) expect.
    // The old batch-array format produced HTTP 400 "Invalid request payload
    // JSON format" from such servers.
    NSTimeInterval uploadCutoff = [[NSDate date] timeIntervalSince1970];
    NSUInteger index = 0;

    // Emit syncStart once before the first task.
    if (locations.count > 0) {
        if (self.delegate && [self.delegate respondsToSelector:@selector(backgroundSyncStarted:)]) {
            [self.delegate backgroundSyncStarted:self];
        }
        [[NSNotificationCenter defaultCenter] postNotificationName:MAURBackgroundSyncDidStartNotification object:self];
    }

    for (MAURLocation *location in locations) {
        NSDictionary *locationDict = [location toResultFromTemplate:locationTemplate];
        NSError *error = nil;
        NSData *jsonData = [NSJSONSerialization dataWithJSONObject:locationDict options:0 error:&error];
        if (!jsonData || error) {
            DDLogError(@"MAURBackgroundSync: failed to serialize location: %@", error);
            index++;
            continue;
        }

        NSString *fileName = [NSString stringWithFormat:@"locations_%@_%tu.json", dateString, index];
        NSURL *jsonUrl = [NSURL fileURLWithPath:[NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES)[0] stringByAppendingPathComponent:fileName]];
        [jsonData writeToFile:jsonUrl.path atomically:NO];
        uint64_t bytesTotalForThisFile = [[[NSFileManager defaultManager] attributesOfItemAtPath:jsonUrl.path error:nil] fileSize];

        NSMutableURLRequest *request = [[NSMutableURLRequest alloc] initWithURL:[NSURL URLWithString:url]];
        [request setHTTPMethod:resolvedMethod];
        [request setTimeoutInterval:120];
        [request setValue:[NSString stringWithFormat:@"%llu", bytesTotalForThisFile] forHTTPHeaderField:@"Content-Length"];
        [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];

        if (httpHeaders != nil) {
            for(id key in httpHeaders) {
                if ([key isEqualToString:@"Content-Type"]) continue;
                id value = [httpHeaders objectForKey:key];
                [request addValue:value forHTTPHeaderField:key];
            }
        }

        NSURLSessionTask *task = [urlSession uploadTaskWithRequest:request fromFile:jsonUrl];
        task.taskDescription = fileName;
        [tasks addObject:task];
        DDLogInfo(@"Started upload for %@ as task %zu/%@/%@", jsonUrl.lastPathComponent, (unsigned long)task.taskIdentifier, task.taskDescription, task);

        objc_setAssociatedObject(task, "locationsSent", @(1), OBJC_ASSOCIATION_RETAIN_NONATOMIC);
        objc_setAssociatedObject(task, "uploadCutoff", @(uploadCutoff), OBJC_ASSOCIATION_RETAIN_NONATOMIC);

        [task resume];
        index++;
    }
}

// http://stackoverflow.com/a/572623/48125
NSString *stringFromFileSize(unsigned long long theSize)
{
    double floatSize = theSize;
    if (theSize<1023)
        return([NSString stringWithFormat:@"%lli bytes",theSize]);
    floatSize = floatSize / 1024;
    if (floatSize<1023)
        return([NSString stringWithFormat:@"%1.1f KB",floatSize]);
    floatSize = floatSize / 1024;
    if (floatSize<1023)
        return([NSString stringWithFormat:@"%1.1f MB",floatSize]);
    floatSize = floatSize / 1024;
    
    return([NSString stringWithFormat:@"%1.1f GB",floatSize]);
}

- (NSString*)status
{
    int64_t sent = 0, toSend = 0;
    for(NSURLSessionUploadTask *task in tasks) {
        sent += task.countOfBytesSent;
        toSend += task.countOfBytesExpectedToSend;
    }
    return [NSString stringWithFormat:@"%@ being uploaded (%@ of %@)\nFiles on disk: %@",
        [tasks valueForKeyPath:@"taskDescription"],
        stringFromFileSize(sent),
        stringFromFileSize(toSend),

        [[NSFileManager defaultManager]
         contentsOfDirectoryAtPath:NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES)[0]
         error:NULL]
    ];
}


#pragma mark -
// v3.5 Phase 4: forward upload progress as syncProgress (0..100).
- (void)URLSession:(NSURLSession *)session
              task:(NSURLSessionTask *)task
   didSendBodyData:(int64_t)bytesSent
    totalBytesSent:(int64_t)totalBytesSent
totalBytesExpectedToSend:(int64_t)totalBytesExpectedToSend
{
    if (totalBytesExpectedToSend <= 0) return;
    NSInteger progress = (NSInteger)((totalBytesSent * 100) / totalBytesExpectedToSend);
    if (progress < 0) progress = 0;
    if (progress > 100) progress = 100;
    [[NSNotificationCenter defaultCenter]
        postNotificationName:MAURBackgroundSyncDidProgressNotification
                      object:self
                    userInfo:@{@"progress": @(progress)}];
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(nullable NSError *)error
{
    NSInteger statusCode = [(NSHTTPURLResponse *)task.response statusCode];
    
    DDLogInfo(@"Finished uploading task %zu %@: %@ %@, HTTP %ld", (unsigned long)[task taskIdentifier], task.originalRequest.URL, error ?: @"Success", task.response, (long)statusCode);
    
    [tasks removeObject:task];
    NSURL *fullPath = [NSURL fileURLWithPath:[NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES)[0] stringByAppendingPathComponent:task.taskDescription]];
    [[NSFileManager defaultManager] removeItemAtURL:fullPath error:NULL];
    
    if (statusCode == 285)
    {
        // Okay, but we don't need to continue sending these
        DDLogDebug(@"Locations were uploaded to the server, and received an \"HTTP 285 Updates Not Required\"");
        
        dispatch_async(dispatch_get_main_queue(), ^{
            if (_delegate && [_delegate respondsToSelector:@selector(backgroundSyncRequestedAbortUpdates:)])
            {
                [_delegate backgroundSyncRequestedAbortUpdates:self];
            }
        });
    }

    if (statusCode == 401)
    {
        dispatch_async(dispatch_get_main_queue(), ^{
            if (_delegate && [_delegate respondsToSelector:@selector(backgroundSyncHttpAuthorizationUpdates:)])
            {
                [_delegate backgroundSyncHttpAuthorizationUpdates:self];
            }
        });
    }

    // v3.5 Phase 4: emit syncSuccess / syncError.
    NSNumber *sentNum = objc_getAssociatedObject(task, "locationsSent");
    NSInteger locationsSent = sentNum != nil ? [sentNum integerValue] : 0;
    BOOL isStatusOkay = (statusCode >= 200 && statusCode < 300);

    dispatch_async(dispatch_get_main_queue(), ^{
        if (error != nil) {
            // v4.5.1 — restore SyncPending → PostPending so the failed locations get retried
            // on the next sync window. Without this, a single network drop loses everything.
            NSError *restErr = nil;
            [[MAURSQLiteLocationDAO sharedInstance] restoreFailedSyncLocations:&restErr];
            NSString *msg = error.localizedDescription ?: @"";
            if (_delegate && [_delegate respondsToSelector:@selector(backgroundSyncFailed:httpStatus:message:)]) {
                [_delegate backgroundSyncFailed:self httpStatus:0 message:msg];
            }
            [[NSNotificationCenter defaultCenter]
                postNotificationName:MAURBackgroundSyncDidFailNotification
                              object:self
                            userInfo:@{@"httpStatus": @0, @"message": msg}];
        } else if (!isStatusOkay) {
            // v4.5.1 — server-side failure (5xx, 4xx other than 285/401): also restore the rows.
            NSError *restErr = nil;
            [[MAURSQLiteLocationDAO sharedInstance] restoreFailedSyncLocations:&restErr];
            NSString *msg = [NSString stringWithFormat:@"HTTP %ld", (long)statusCode];
            if (_delegate && [_delegate respondsToSelector:@selector(backgroundSyncFailed:httpStatus:message:)]) {
                [_delegate backgroundSyncFailed:self httpStatus:statusCode message:msg];
            }
            [[NSNotificationCenter defaultCenter]
                postNotificationName:MAURBackgroundSyncDidFailNotification
                              object:self
                            userInfo:@{@"httpStatus": @(statusCode), @"message": msg}];
        } else {
            // v4.5.1: drop SYNC_PENDING locations whose recorded_at is <= the captured upload-start
            // cutoff. This preserves any new locations persisted DURING the upload (race window).
            NSNumber *cutoffNum = objc_getAssociatedObject(task, "uploadCutoff");
            NSTimeInterval cutoff = cutoffNum != nil ? [cutoffNum doubleValue] : [[NSDate date] timeIntervalSince1970];
            NSError *delErr = nil;
            BOOL deleted = [[MAURSQLiteLocationDAO sharedInstance] deleteSyncedLocationsBefore:cutoff error:&delErr];
            if (!deleted) {
                NSLog(@"deleteSyncedLocationsBefore after success failed: %@", delErr.localizedDescription ?: @"unknown");
            }
            if (_delegate && [_delegate respondsToSelector:@selector(backgroundSyncSucceeded:locationsSent:)]) {
                [_delegate backgroundSyncSucceeded:self locationsSent:locationsSent];
            }
            [[NSNotificationCenter defaultCenter]
                postNotificationName:MAURBackgroundSyncDidSucceedNotification
                              object:self
                            userInfo:@{@"sent": @(locationsSent)}];
        }
    });
}

- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)dataTask didReceiveData:(NSData *)data
{
    DDLogInfo(@"Response:: %@", [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding]);
}

- (void)URLSession:(NSURLSession *)session didBecomeInvalidWithError:(nullable NSError *)error
{
    DDLogError(@"Autosync failed :( %@", error);
}

- (void)URLSessionDidFinishEventsForBackgroundURLSession:(NSURLSession *)session
{
    DDLogInfo(@"finished events for bg session");
}

@end
