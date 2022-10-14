import 'dart:async';
import 'dart:io';

import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'face_detection.dart';
import 'face_detection_arguments.dart';
import 'objects/data.dart';
import 'objects/face.dart';
import 'objects/rot.dart';
import 'objects/utility.dart';

/// The facing of a camera.
enum CameraFacing {
  /// Front facing camera.
  front,

  /// Back facing camera.
  back,
}

enum MobileScannerState { undetermined, authorized, denied }

/// The state of torch.
enum TorchState {
  /// Torch is off.
  off,

  /// Torch is on.
  on,
}

// enum AnalyzeMode { none, barcode }

class FaceDetectionController {
  MethodChannel methodChannel = const MethodChannel('cmc.com.vn/camera/method');
  EventChannel eventChannel = const EventChannel('cmc.com.vn/camera/event');

  //Must be static to keep the same value on new instances
  static int? _controllerHashcode;
  StreamSubscription? events;

  final ValueNotifier<FaceDetectionArguments?> args = ValueNotifier(null);
  final ValueNotifier<TorchState> torchState = ValueNotifier(TorchState.off);
  late final ValueNotifier<CameraFacing> cameraFacingState;
  final Ratio? ratio;
  final bool? torchEnabled;
  final bool? faceDetected;
  final bool? iseKYC;

  /// If provided, the scanner will only detect those specific formats.
  ///
  /// WARNING: On iOS, only 1 format is supported.
  // final List<BarcodeFormat>? formats;

  CameraFacing facing;
  bool hasTorch = false;
  late StreamController<Data> facesController;

  /// Whether to automatically resume the camera when the application is resumed
  bool autoResume;

  Stream<Data> get faces => facesController.stream;

  FaceDetectionController({
    this.facing = CameraFacing.back,
    this.ratio,
    this.torchEnabled,
    this.faceDetected,
    this.iseKYC,
    // this.formats,
    this.autoResume = true,
  }) {
    // In case a new instance is created before calling dispose()
    if (_controllerHashcode != null) {
      stop();
    }
    _controllerHashcode = hashCode;

    cameraFacingState = ValueNotifier(facing);

    // Sets analyze mode and barcode stream
    facesController = StreamController.broadcast(
        // onListen: () => setAnalyzeMode(AnalyzeMode.barcode.index),
        // onCancel: () => setAnalyzeMode(AnalyzeMode.none.index),
        );

    // Listen to events from the platform specific code
    events = eventChannel
        .receiveBroadcastStream()
        .listen((data) => handleEvent(data as Map));
  }

  void handleEvent(Map event) {
    final name = event['name'];
    final data = event['data'];

    switch (name) {
      case 'torchState':
        final state = TorchState.values[data as int? ?? 0];
        torchState.value = state;
        break;

      /// Only work on android.
      case 'faceAndroid':
        final face = Data.fromNative(
          data as Map? ?? {},
        );
        facesController.add(face);
        break;
      case 'barcodeMac':
        final face = Data.fromNative(
          data as Map? ?? {},
        );
        facesController.add(face);
        break;
      case 'barcodeWeb':
        final face = Data.fromNative(
          data as Map? ?? {},
        );
        facesController.add(face);
        break;
      default:
        throw UnimplementedError();
    }
  }

  // TODO: Add more analyzers like text analyzer
  // void setAnalyzeMode(int mode) {
  //   if (hashCode != _controllerHashcode) {
  //     return;
  //   }
  //   methodChannel.invokeMethod('analyze', mode);
  // }

  // List<BarcodeFormats>? formats = _defaultBarcodeFormats,
  bool isStarting = false;

  /// Start barcode scanning. This will first check if the required permissions
  /// are set.
  Future<void> start() async {
    ensure('startAsync');
    if (isStarting) {
      throw Exception('face_detection: Called start() while already starting.');
    }
    isStarting = true;
    // setAnalyzeMode(AnalyzeMode.barcode.index);

    // Check authorization status
    if (!kIsWeb) {
      MobileScannerState state = MobileScannerState
          .values[await methodChannel.invokeMethod('state') as int? ?? 0];
      switch (state) {
        case MobileScannerState.undetermined:
          final bool result =
              await methodChannel.invokeMethod('request') as bool? ?? false;
          state = result
              ? MobileScannerState.authorized
              : MobileScannerState.denied;
          break;
        case MobileScannerState.denied:
          isStarting = false;
          throw PlatformException(code: 'NO ACCESS');
        case MobileScannerState.authorized:
          break;
      }
    }

    cameraFacingState.value = facing;

    // Set the starting arguments for the camera
    final Map arguments = {};
    arguments['facing'] = facing.index;
    if (ratio != null) {
      arguments['ratio'] = (ratio == Ratio.ratio_4_3) ? 0 : 1;
    }
    if (torchEnabled != null) arguments['torch'] = torchEnabled;
    if (faceDetected != null) arguments['faceDetected'] = faceDetected;
    if (iseKYC != null) arguments['iseKYC'] = iseKYC;

    // if (formats != null) {
    //   if (Platform.isAndroid) {
    //     arguments['formats'] = formats!.map((e) => e.index).toList();
    //   } else if (Platform.isIOS || Platform.isMacOS) {
    //     arguments['formats'] = formats!.map((e) => e.rawValue).toList();
    //   }
    // }

    // Start the camera with arguments
    Map<String, dynamic>? startResult = {};
    try {
      startResult = await methodChannel.invokeMapMethod<String, dynamic>(
        'start',
        arguments,
      );
    } on PlatformException catch (error) {
      debugPrint('${error.code}: ${error.message}');
      isStarting = false;
      // setAnalyzeMode(AnalyzeMode.none.index);
      return;
    }

    if (startResult == null) {
      isStarting = false;
      throw PlatformException(code: 'INITIALIZATION ERROR');
    }

    hasTorch = startResult['torchable'] as bool? ?? false;

    if (kIsWeb) {
      args.value = FaceDetectionArguments(
        webId: startResult['ViewID'] as String?,
        size: Size(
          startResult['videoWidth'] as double? ?? 0,
          startResult['videoHeight'] as double? ?? 0,
        ),
        hasTorch: hasTorch,
      );
    } else {
      args.value = FaceDetectionArguments(
        textureId: startResult['textureId'] as int?,
        size: toSize(startResult['size'] as Map? ?? {}),
        hasTorch: hasTorch,
      );
    }

    isStarting = false;
  }

  Future<void> stop() async {
    try {
      await methodChannel.invokeMethod('stop');
    } on PlatformException catch (error) {
      debugPrint('${error.code}: ${error.message}');
    }
  }

  /// Switches the torch on or off.
  ///
  /// Only works if torch is available.
  Future<void> toggleTorch() async {
    ensure('toggleTorch');
    if (!hasTorch) {
      debugPrint('Device has no torch/flash.');
      return;
    }

    final TorchState state =
        torchState.value == TorchState.off ? TorchState.on : TorchState.off;

    try {
      await methodChannel.invokeMethod('torch', state.index);
    } on PlatformException catch (error) {
      debugPrint('${error.code}: ${error.message}');
    }
  }

  /// Switches the torch on or off.
  ///
  /// Only works if torch is available.
  Future<void> switchCamera() async {
    ensure('switchCamera');
    try {
      await methodChannel.invokeMethod('stop');
    } on PlatformException catch (error) {
      debugPrint(
        '${error.code}: camera is stopped! Please start before switching camera.',
      );
      return;
    }
    facing =
        facing == CameraFacing.back ? CameraFacing.front : CameraFacing.back;
    await start();
  }

  /// Handles a local image file.
  /// Returns true if a barcode or QR code is found.
  /// Returns false if nothing is found.
  ///
  /// [path] The path of the image on the devices
  Future<bool> analyzeImage(String path) async {
    return methodChannel
        .invokeMethod<bool>('analyzeImage', path)
        .then<bool>((bool? value) => value ?? false);
  }

  /// Disposes the MobileScannerController and closes all listeners.
  void dispose() {
    if (hashCode == _controllerHashcode) {
      stop();
      events?.cancel();
      events = null;
      _controllerHashcode = null;
    }
    facesController.close();
  }

  /// Checks if the MobileScannerController is bound to the correct MobileScanner object.
  void ensure(String name) {
    final message =
        'MobileScannerController.$name called after MobileScannerController.dispose\n'
        'MobileScannerController methods should not be used after calling dispose.';
    assert(hashCode == _controllerHashcode, message);
  }

  /// Guid
  Future<void> guid(
      int minX, int maxX, int minY, int maxY, int minZ, int maxZ) async {
    ensure('guid');

    /// [faceDetected] default is true.
    if (faceDetected != null) {
      if (!faceDetected!) {
        debugPrint('No face detection');
        return;
      }
    }

    try {
      await methodChannel.invokeMethod('guid', {
        "minX": minX,
        "maxX": maxX,
        "minY": minY,
        "maxY": maxY,
        "minZ": minZ,
        "maxZ": maxZ,
      });
    } on PlatformException catch (error) {
      debugPrint('${error.code}: ${error.message}');
    }
  }

  /// Capture
  Future<void> capture(faceDetectedCapture) async {
    ensure('capture');

    try {
      await methodChannel.invokeMethod('capture', {
        "faceDetectedCapture": faceDetectedCapture,
      });
    } on PlatformException catch (error) {
      debugPrint('${error.code}: ${error.message}');
    }
  }

  /// takePicture
  Future<void> takePicture(double boxWidth, double boxHeight, double boxTop,
      double boxLeft, double screenWidth, double screenHeight) async {
    ensure('takePicture');

    try {
      await methodChannel.invokeMethod('takePicture', {
        "boxWidth": boxWidth,
        "boxHeight": boxHeight,
        "boxTop": boxTop,
        "boxLeft": boxLeft,
        "screenWidth": screenWidth,
        "screenHeight": screenHeight,
      });
    } on PlatformException catch (error) {
      debugPrint('${error.code}: ${error.message}');
    }
  }
}
