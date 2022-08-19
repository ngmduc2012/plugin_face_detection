import 'rot.dart';

class Face {
  final double? headEulerAngleX;
  final double? headEulerAngleY;
  final double? headEulerAngleZ;
  final double? smileProb;
  final double? rightEyeOpenProbability;
  final double? leftEyeOpenProbability;

  Face({
    this.headEulerAngleX,
    this.headEulerAngleY,
    this.headEulerAngleZ,
    this.smileProb,
    this.rightEyeOpenProbability,
    this.leftEyeOpenProbability,
  });

}
