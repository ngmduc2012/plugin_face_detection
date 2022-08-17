import 'rot.dart';

class Face {
  final double? headEulerAngleX;
  final double? headEulerAngleY;
  final double? headEulerAngleZ;

  Face({
    this.headEulerAngleX,
    this.headEulerAngleY,
    this.headEulerAngleZ,
  });

  /// Create a [face] from native data.
  Face.fromNative(Map data,)
      : headEulerAngleX = data['headEulerAngleX'] as double?,
        headEulerAngleY = data['headEulerAngleY'] as double?,
        headEulerAngleZ = data['headEulerAngleZ'] as double?;
}
