import 'package:flutter/material.dart';
import 'package:path_drawing/path_drawing.dart';

class OverlayWidget extends CustomPainter {
  final double x;
  final double y;
  final bool strokeDasharray;
  final Color color;
  final double strokeWidth;
  final double r;

  const OverlayWidget(
      {required this.r,
        required this.x,
        required this.y,
        required this.color,
        this.strokeDasharray = false,
        this.strokeWidth = 3});

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint();
    paint.color = this.color;
    paint.strokeWidth = this.strokeWidth;
    paint.style = PaintingStyle.stroke;
    final handlePoint = Offset(x * 3, y * 3);

    final curvePathx = Path()
      ..moveTo(0, -r)
      ..quadraticBezierTo(handlePoint.dx, handlePoint.dy, 0, r);

    final curvePathy = Path()
      ..moveTo(-r, 0)
      ..quadraticBezierTo(handlePoint.dx, handlePoint.dy, r, 0);
    canvas.drawPath(
        strokeDasharray
            ? dashPath(
          curvePathx,
          dashArray: CircularIntervalList<double>(<double>[15.0, 10.5]),
        )
            : curvePathx,
        paint);
    canvas.drawPath(
        strokeDasharray
            ? dashPath(
          curvePathy,
          dashArray: CircularIntervalList<double>(<double>[15.0, 10.5]),
        )
            : curvePathy,
        paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) {
    return true;
  }
}
