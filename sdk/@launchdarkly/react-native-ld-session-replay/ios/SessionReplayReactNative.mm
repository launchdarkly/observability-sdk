#import <Foundation/Foundation.h>
#import "SessionReplayReactNative.h"
#import "SessionReplayReactNative-Swift.h" // Auto-generated header

@implementation SessionReplayReactNative
- (void)configure:(NSString *)mobileKey
           options:(NSDictionary *)options
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
    NSString *trimmed = [mobileKey stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if (!trimmed || trimmed.length == 0) {
      reject(@"invalid_mobile_key", @"Session replay requires a non-empty mobile key.", nil);
      return;
    }
    @try {
      [[SessionReplayClientAdapter shared] setMobileKey:trimmed options:options];
      resolve(nil);
    } @catch(NSException *exception) {
      NSLog(@"⚠️ configure crash: %@", exception);
      reject(@"configure_failed", exception.reason, nil);
    }
}

- (void)startSessionReplay:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
    @try {
      [[SessionReplayClientAdapter shared] startWithCompletion:^(BOOL success, NSString * _Nullable errorMessage) {
        if (success) {
          resolve(nil);
        } else {
          NSString *error = errorMessage ?: @"Session replay failed to start";
          reject(@"start_failed", error, nil);
        }
      }];
    } @catch(NSException *exception) {
      NSLog(@"⚠️ startSessionReplay crash: %@", exception);
      reject(@"start_failed", exception.reason, nil);
    }
}

- (void)stopSessionReplay:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
    @try {
      [[SessionReplayClientAdapter shared] stopWithCompletion:^{
        resolve(nil);
      }];      
    } @catch(NSException *exception) {
      NSLog(@"⚠️ stopSessionReplay crash: %@", exception);
      reject(@"stop_failed", exception.reason, nil);
    }
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeSessionReplayReactNativeSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"SessionReplayReactNative";
}

@end
