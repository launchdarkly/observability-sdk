#ifdef RCT_NEW_ARCH_ENABLED
#import <SessionReplayReactNativeSpec/SessionReplayReactNativeSpec.h>

@interface SessionReplayReactNative : NSObject <NativeSessionReplayReactNativeSpec>
@end
#else
#import <React/RCTBridgeModule.h>

@interface SessionReplayReactNative : NSObject <RCTBridgeModule>
@end
#endif
