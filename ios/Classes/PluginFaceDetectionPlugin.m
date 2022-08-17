#import "PluginFaceDetectionPlugin.h"
#if __has_include(<plugin_face_detection/plugin_face_detection-Swift.h>)
#import <plugin_face_detection/plugin_face_detection-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "plugin_face_detection-Swift.h"
#endif

@implementation PluginFaceDetectionPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftPluginFaceDetectionPlugin registerWithRegistrar:registrar];
}
@end
