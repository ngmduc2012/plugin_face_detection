import 'package:flutter/material.dart';

/// Camera args for [CameraView].
class FaceDetectionArguments {
  /// The texture id.
  final int? textureId;

  /// Size of the texture.
  final Size size;

  final bool hasTorch;

  final String? webId;

  /// Create a [FaceDetectionArguments].
  FaceDetectionArguments({
    this.textureId,
    required this.size,
    required this.hasTorch,
    this.webId,
  });
}
