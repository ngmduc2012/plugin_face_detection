import 'dart:io';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:plugin_face_detection/plugin_face_detection.dart';
import 'package:plugin_face_detection_example/screens/widget/overlay_widget.dart';

import 'dart:developer' as d;
import 'models/data.dart';
import 'models/face.dart';
import 'models/rot.dart';

class OverlayFace extends StatefulWidget {
  const OverlayFace({Key? key}) : super(key: key);

  @override
  _OverlayFaceState createState() => _OverlayFaceState();
}

class _OverlayFaceState extends State<OverlayFace> {
  Data? face;
  FaceDetectionController controller = FaceDetectionController(
    torchEnabled: false,
    ratio: (MediaQueryData.fromWindow(WidgetsBinding.instance.window).size.shortestSide < 600 ? false : true) ? Ratio.ratio_4_3 : Ratio.ratio_16_9,
    // formats: [BarcodeFormat.qrCode]
    facing: CameraFacing.front,
  );

  int distance = 4;
  late int minX;
  late int maxX;

  late int minY;

  late int maxY;

  late int minZ;

  late int maxZ;

  String? pathFaceImage;
  double? rotX;
  double? rotY;

  @override
  void initState() {
    super.initState();
    minX = 0 - distance;
    maxX = 0 + distance;
    minY = 12 - distance;
    maxY = 12 + distance;
    minZ = 0 - distance;
    maxZ = 0 + distance;
    controller.guid(minX, maxX, minY, maxY, minZ, maxZ);
  }

  String? compileGuidID(guidID){
    if (guidID == 0) {
      return "move Device UP";
    }
    if (guidID == 1) {
      return "move Device Down";
    }
    if (guidID == 2) {
      return "move Device Left";
    }
    if (guidID == 3) {
      return "move Device Right";
    }
    if (guidID == 5) {
      return "turn Left";
    }
    if (guidID == 6) {
      return "turn Right";
    }
    if (guidID == 7) {
      return "raise Head";
    }
    if (guidID == 8) {
      return "lower Head";
    }
    if (guidID == 9) {
      return "whole Face In Frame";
    }
    if (guidID == 4) {
      return "captured";
    }
    if (guidID == 10) {
      return "hold Camera";
    }
    if (guidID == 11) {
      return "take Away";
    }
    if (guidID == 12) {
      return "bring Closer";
    }
    return null;
  }

  var data;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Builder(
        builder: (context) {
          return Stack(
            children: [
              SizedBox(
                // height: 3000,
                // width: 2000,
                child: FaceDetection(
                  controller: controller,
                  fit: BoxFit.contain,
                  onDetect: (data, args) {
                    /// caution (*): Add code line: d.log() make app run delay.
                    // d.log("${face.size}");
                    /// This is the solution for (*)
                    if(data == this.data) return;
                    this.data = data;

                    setState(() {
                      face = Data(
                          face: Face(
                            headEulerAngleX: data.face?.headEulerAngleX,
                            headEulerAngleY: data.face?.headEulerAngleY,
                            headEulerAngleZ: data.face?.headEulerAngleZ,
                          ),
                          size: data.size,
                          guidID: data.guidID,
                          faceImage: data.faceImage,
                          flippedFaceImage: data.flippedFaceImage,
                          rot: Rot(
                            rotX: data.rot?.rotX,
                            rotY: data.rot?.rotY,
                            rotZ: data.rot?.rotZ,
                          ));
                      if (data.faceImage != null) {
                        // d.log("data.faceImage : ${data.faceImage}");
                        pathFaceImage = data.faceImage;
                      }
                      if (data.rot!.rotX != null) rotX = data.rot!.rotX;
                      if (data.rot!.rotY != null) rotY = data.rot!.rotY;
                    });
                  },
                ),
              ),
              (face?.guidID == 4 || face?.guidID == 9 || rotX == null ||
                  rotY == null)
                  ?
              const Positioned.fill(child: SizedBox(
                height: 150,
                child: Text("ok", style: TextStyle(color: Colors.blue),),))
                  : Positioned.fill(
                child: Align(
                  alignment: Alignment.center,
                  child: Container(
                    constraints:
                    const BoxConstraints(),
                    child: CustomPaint(
                      painter: OverlayWidget(
                          r: 300 / 2,
                          x: rotX ?? 0,
                          y: rotY ?? 0,
                          color: Colors.blue,
                          strokeWidth: 2),
                    ),
                  ),
                ),
              )
              ,
              Positioned.fill(
                child: Align(
                  alignment: Alignment.center,
                  child: Container(
                    constraints:
                    const BoxConstraints(),
                    child: CustomPaint(
                      painter: OverlayWidget(
                          r: 300 / 2,
                          x: 0,
                          y: (maxY.toDouble() +
                              minY.toDouble()) /
                              2,
                          color: Colors.red,
                          strokeDasharray: true),
                    ),
                  ),
                ),
              ),
              Align(
                  alignment: Alignment.bottomCenter,
                  child: Column(
                      mainAxisSize: MainAxisSize.min,
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        face != null
                            ? Column(
                          mainAxisSize: MainAxisSize.min,
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              "x: ${face?.face?.headEulerAngleX}, y: ${face
                                  ?.face?.headEulerAngleY}, z: ${face?.face
                                  ?.headEulerAngleZ}" +
                                  ", rotX: ${rotX}, rotY: ${rotY}",
                              style: const TextStyle(color: Colors.green),
                            ),
                            Text(
                              "guidID: ${compileGuidID(face?.guidID)}",
                              style: const TextStyle(color: Colors.green),
                            ),
                          ],
                        )
                            : const SizedBox(),
                        Container(
                          alignment: Alignment.bottomCenter,
                          height: 100,
                          color: Colors.black.withOpacity(0.4),
                          child: Row(
                            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                            children: [
                              pathFaceImage != null
                                  ? ExtendedImage.file(
                                File(pathFaceImage!),
                                fit: BoxFit.fill,
                                shape: BoxShape.rectangle,
                                borderRadius: const BorderRadius.all(
                                    Radius.circular(5.0)),
                              )
                                  : const SizedBox()
                            ],
                          ),
                        ),
                      ])),
            ],
          );
        },
      ),
    );
  }
}
