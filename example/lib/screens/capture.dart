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
    ratio: (MediaQueryData.fromWindow(WidgetsBinding.instance.window).size.shortestSide < 600 ? false : true) ? Ratio.ratio_4_3 : Ratio.ratio_16_9,

    // formats: [BarcodeFormat.qrCode]
    // facing: CameraFacing.back,
  );

  bool isStarted = true;
  String? pathImage;
  String? pathImageCrop;
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
                        pathImage = data.faceImage;
                      }
                      if (data.flippedFaceImage != null) {
                        pathImageCrop = data.flippedFaceImage;
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
                        alignment: Alignment.topCenter,
                        child: Container(
                          key: croppedBoxKeyDocCapture,
                          constraints: const BoxConstraints(
                            maxWidth: 350,
                          ),
                          padding: const EdgeInsets.only(
                            top: 100,

                          ),
                          child: AspectRatio(
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
                  height: 200,
                  alignment: Alignment.bottomCenter,
                  color: Colors.black.withOpacity(0.4),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          pathImage != null
                              ? SizedBox(
                                  height: 100,
                                  child: ExtendedImage.file(
                                    File(pathImage!),
                                    fit: BoxFit.fill,
                                    shape: BoxShape.rectangle,
                                    borderRadius: const BorderRadius.all(
                                        Radius.circular(5.0)),
                                  ),
                                )
                              : const SizedBox(),
                          pathImageCrop != null
                              ? SizedBox(
                                  height: 100,
                                  child: ExtendedImage.file(
                                    File(pathImageCrop!),
                                    fit: BoxFit.fill,
                                    shape: BoxShape.rectangle,
                                    borderRadius: const BorderRadius.all(
                                        Radius.circular(5.0)),
                                  ),
                                )
                              : const SizedBox(),
                        ],
                      ),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                        children: [
                          Flexible(
                              child: TextButton(
                            onPressed: () async {
                              if (croppedBoxKeyDocCapture.currentContext !=
                                  null) {
                                final RenderBox renderBox =
                                    croppedBoxKeyDocCapture.currentContext!
                                        .findRenderObject() as RenderBox;
                                final size = renderBox.size;
                                final pos =
                                    renderBox.localToGlobal(Offset.zero);
                                final screenSize = Get.size;
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
                          IconButton(
                            color: Colors.white,
                            icon: ValueListenableBuilder(
                              valueListenable: controller.cameraFacingState,
                              builder: (context, state, child) {
                                if (state == null) {
                                  return const Icon(Icons.camera_front);
                                }
                                switch (state as CameraFacing) {
                                  case CameraFacing.front:
                                    return const Icon(Icons.camera_front);
                                  case CameraFacing.back:
                                    return const Icon(Icons.camera_rear);
                                }
                              },
                            ),
                            iconSize: 32.0,
                            onPressed: () => controller.switchCamera(),
                          ),
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
