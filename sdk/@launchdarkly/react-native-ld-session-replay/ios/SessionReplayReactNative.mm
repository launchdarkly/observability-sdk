#import <Foundation/Foundation.h>
#import "SessionReplayReactNative.h"
#import "SessionReplayReactNative-Swift.h" // Auto-generated header

@implementation SessionReplayReactNative
- (void)configure:(NSString *)mobileKey
           options:(NSDictionary *)options
             resolve:(RCTPromiseResolveBlock)resolve
              reject:(RCTPromiseRejectBlock)reject
{
    @try {
      NSLog(@"Configure called with mobileKey: %@ options: %@", mobileKey, options);
      [[SessionReplayAdapter shared] setMobileKey:mobileKey options:options];
      resolve(nil);
    } @catch(NSException *exception) {
      NSLog(@"⚠️ configure crash: %@", exception);
      reject(@"configure_failed", exception.reason, nil);
    }    
}

- (void)startSessionReplay:(RCTPromiseResolveBlock)resolve
                    reject:(RCTPromiseRejectBlock)reject
{
    [[SessionReplayAdapter shared] start];
    resolve(nil);
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
