import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import 'face_detection_arguments.dart';
import 'face_detection_controller.dart';
import 'objects/data.dart';
import 'objects/face.dart';
import 'dart:developer' as d;

enum Ratio { ratio_4_3, ratio_16_9 }

/// A widget showing a live camera preview.
class FaceDetection extends StatefulWidget {
  /// The controller of the camera.
  final FaceDetectionController? controller;

  /// Function that gets called when a Barcode is detected.
  ///
  /// [face] The barcode object with all information about the scanned code.
  /// [args] Information about the state of the MobileScanner widget
  final Function(Data face, FaceDetectionArguments? args) onDetect;

  /// TODO: Function that gets called when the Widget is initialized. Can be usefull
  /// to check wether the device has a torch(flash) or not.
  ///
  /// [args] Information about the state of the MobileScanner widget
  // final Function(MobileScannerArguments args)? onInitialize;

  /// Handles how the widget should fit the screen.
  final BoxFit fit;

  /// Set to false if you don't want duplicate scans.
  final bool allowDuplicates;

  /// Create a [FaceDetection] with a [controller], the [controller] must has been initialized.
  const FaceDetection({
    super.key,
    required this.onDetect,
    this.controller,
    this.fit = BoxFit.cover,
    this.allowDuplicates = false,
  });

  @override
  State<FaceDetection> createState() => _MobileScannerState();
}

class _MobileScannerState extends State<FaceDetection>
    with WidgetsBindingObserver {
  late FaceDetectionController controller;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    controller = widget.controller ?? FaceDetectionController();
    if (!controller.isStarting) controller.start();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.resumed:
        if (!controller.isStarting && controller.autoResume) controller.start();
        break;
      case AppLifecycleState.inactive:
      case AppLifecycleState.paused:
      case AppLifecycleState.detached:
        controller.stop();
        break;
    }
  }

  // String? lastScanned;

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder(
      valueListenable: controller.args,
      builder: (context, value, child) {
        value = value as FaceDetectionArguments?;
        if (value == null) {
          return const ColoredBox(color: Colors.black);
        } else {
          controller.faces.listen((face) {
            if (!widget.allowDuplicates) {
              // if (lastScanned != face) {
              //   lastScanned = face.toString();
              widget.onDetect(face, value! as FaceDetectionArguments);
              // }
            } else {
              widget.onDetect(face, value! as FaceDetectionArguments);
            }
          });
          return ClipRect(
              child: SizedBox(
                  width: MediaQuery.of(context).size.width,
                  height: MediaQuery.of(context).size.height,
                  child: FittedBox(
                    fit: widget.fit,
                    child: SizedBox(
                        width: value.size.width,
                        height: value.size.height,
                        child: kIsWeb
                            ? HtmlElementView(viewType: value.webId!)
                            : Texture(textureId: value.textureId!)),
                  )));
        }
      },
    );
  }

  @override
  void didUpdateWidget(covariant FaceDetection oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller == null) {
      if (widget.controller != null) {
        controller.dispose();
        controller = widget.controller!;
      }
    } else {
      if (widget.controller == null) {
        controller = FaceDetectionController();
      } else if (oldWidget.controller != widget.controller) {
        controller = widget.controller!;
      }
    }
  }

  @override
  void dispose() {
    controller.dispose();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }
}
