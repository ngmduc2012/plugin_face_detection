

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

}
