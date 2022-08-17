import 'dart:io';

import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:image_picker/image_picker.dart';
import 'package:plugin_face_detection/plugin_face_detection.dart';

import 'models/data.dart';
import 'models/face.dart';
import 'models/rot.dart';

import 'dart:developer' as d;

class Capture extends StatefulWidget {
  const Capture({Key? key}) : super(key: key);

  @override
  _CaptureState createState() => _CaptureState();
}

class _CaptureState extends State<Capture> {
  Data? face;
  FaceDetectionController controller = FaceDetectionController(
    torchEnabled: false,
    faceDetected: false,
    // formats: [BarcodeFormat.qrCode]
    // facing: CameraFacing.front,
  );

  bool isStarted = true;
  String? pathFaceImage;
  GlobalKey croppedBoxKeyDocCapture = GlobalKey();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Builder(
        builder: (context) {
          return Stack(
            children: [
              SizedBox(
                // height: 200,
                // width: 200,
                child: FaceDetection(
                  controller: controller,
                  fit: BoxFit.contain,
                  onDetect: (data, args) {
                    setState(() {
                      // d.log("$faceNumber");
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
                        pathFaceImage = data.faceImage;
                      }
                    });
                  },
                ),
              ),
              Positioned.fill(
                  child: Container(
                child: ColorFiltered(
                  colorFilter: ColorFilter.mode(
                      Colors.black.withOpacity(0.5), BlendMode.srcOut),
                  child: Stack(
                    fit: StackFit.expand,
                    children: [
                      Container(
                        decoration: const BoxDecoration(
                            color: Colors.black,
                            backgroundBlendMode: BlendMode.dstOut),
                      ),
                      Align(
                        alignment: Alignment.center,
                        child: Container(
                          constraints: const BoxConstraints(
                            maxWidth: 300,
                          ),
                          padding: const EdgeInsets.only(
                            bottom: 16,
                          ),
                          child: AspectRatio(
                            key: croppedBoxKeyDocCapture,
                            aspectRatio: 1.6,
                            child: Container(
                                decoration: BoxDecoration(
                                  color: Colors.red,
                                  borderRadius: BorderRadius.circular(10),
                                  // borderRadius: BorderRadius.circular(20),
                                ),
                                child: const Opacity(
                                  opacity: 0.8,
                                )),
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              )),
              Align(
                alignment: Alignment.bottomCenter,
                child: Container(
                  alignment: Alignment.bottomCenter,
                  height: 100,
                  color: Colors.black.withOpacity(0.4),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      Flexible(
                          child: TextButton(
                        onPressed: () async {
                          if (croppedBoxKeyDocCapture.currentContext != null) {
                            d.log("not null");
                            final RenderBox renderBox = croppedBoxKeyDocCapture
                                .currentContext!
                                .findRenderObject() as RenderBox;
                            final size = renderBox.size;
                            final pos = renderBox.localToGlobal(Offset.zero);
                            final screenSize = Get.size;
                            d.log("get size");
                            await controller.takePicture(
                                size.width,
                                size.height,
                                pos.dy,
                                pos.dx,
                                screenSize.width,
                                screenSize.height);
                          }
                        },
                        child: const Text(
                          "Chụp trong khung",
                        ),
                      )),
                      pathFaceImage != null
                          ? ExtendedImage.file(
                              File(pathFaceImage!),
                              fit: BoxFit.fill,
                              shape: BoxShape.rectangle,
                              borderRadius:
                                  const BorderRadius.all(Radius.circular(5.0)),
                            )
                          : const SizedBox(),
                      Flexible(
                        child: TextButton(
                          onPressed: () async {
                            await controller.capture(false);
                          },
                          child: const Text(
                            "Chụp toàn màn hình",
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
