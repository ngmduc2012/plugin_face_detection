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

  /// Create a [face] from native data.
  Face.fromNative(
    Map data,
  )   : headEulerAngleX = data['headEulerAngleX'] as double?,
        headEulerAngleY = data['headEulerAngleY'] as double?,
        headEulerAngleZ = data['headEulerAngleZ'] as double?,
        smileProb = data['smileProb'] as double?,
        rightEyeOpenProbability = data['rightEyeOpenProbability'] as double?,
        leftEyeOpenProbability = data['leftEyeOpenProbability'] as double?;
}
