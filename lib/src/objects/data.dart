import 'face.dart';
import 'rot.dart';

class Data {
  final Face? face;
  final int? size;
  final int? guidID;
  final String? faceImage;
  final String? flippedFaceImage;
  final int? eKYCID;
  final Rot? rot;

  Data(
      {this.face,
      this.size,
      this.guidID,
      this.faceImage,
      this.flippedFaceImage,
        this.eKYCID,
      this.rot});

  /// Create a [face] from native data.
  Data.fromNative(
    Map data,
  )   : face = Face.fromNative(data['faceData'] as Map? ?? {}),
        size = data['size'] as int?,
        guidID = data['guidID'] as int?,
        faceImage = data['faceImage'] as String?,
        flippedFaceImage = data['flippedFaceImage'] as String?,
        eKYCID = data['eKYCID'] as int?,
        rot = Rot.fromNative(data['rot'] as Map? ?? {});
}
