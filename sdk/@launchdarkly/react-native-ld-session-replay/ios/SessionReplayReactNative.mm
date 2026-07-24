#import <Foundation/Foundation.h>
#import "SessionReplayReactNative.h"

// The Swift-generated interop header is emitted under the module name. When the
// pod is built as a framework (use_frameworks! / :modular_headers => true) it is
// reachable via angle brackets; under default static-library linking it is only
// reachable via the quoted form. Support both so the module compiles regardless
// of how the host app links its pods.
#if __has_include(<SessionReplayReactNative/SessionReplayReactNative-Swift.h>)
#import <SessionReplayReactNative/SessionReplayReactNative-Swift.h>
#else
#import "SessionReplayReactNative-Swift.h" // Auto-generated header
#endif

@implementation SessionReplayReactNative

RCT_EXPORT_MODULE()

+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

RCT_EXPORT_METHOD(configure:(NSString *)mobileKey
                  options:(NSDictionary *)options
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
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

RCT_EXPORT_METHOD(startSessionReplay:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
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

RCT_EXPORT_METHOD(stopSessionReplay:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
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

RCT_EXPORT_METHOD(afterIdentify:(NSDictionary *)contextKeys
                  canonicalKey:(NSString *)canonicalKey
                  completed:(BOOL)completed
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
    @try {
      [[SessionReplayClientAdapter shared] afterIdentifyWithContextKeys:contextKeys canonicalKey:canonicalKey completed:completed];
      resolve(nil);
    } @catch(NSException *exception) {
      NSLog(@"⚠️ afterIdentify crash: %@", exception);
      reject(@"after_identify_failed", exception.reason, nil);
    }
}

#ifdef RCT_NEW_ARCH_ENABLED
- (NSNumber *)getProcessStartTimeMillis
{
    return @([SessionReplayClientAdapter processStartTimeMillis]);
}
#else
RCT_EXPORT_BLOCKING_SYNCHRONOUS_METHOD(getProcessStartTimeMillis)
{
    return @([SessionReplayClientAdapter processStartTimeMillis]);
}
#endif

#ifdef RCT_NEW_ARCH_ENABLED
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeSessionReplayReactNativeSpecJSI>(params);
}
#endif

@end
