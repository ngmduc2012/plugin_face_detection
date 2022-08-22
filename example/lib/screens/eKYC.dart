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

class Ekyc extends StatefulWidget {
  const Ekyc({Key? key}) : super(key: key);

  @override
  _EkycState createState() => _EkycState();
}

class _EkycState extends State<Ekyc> {
  Data? face;
  FaceDetectionController controller = FaceDetectionController(
    torchEnabled: false,
    ratio: (MediaQueryData.fromWindow(WidgetsBinding.instance.window).size.shortestSide < 600 ? false : true) ? Ratio.ratio_4_3 : Ratio.ratio_16_9,
    // formats: [BarcodeFormat.qrCode]
    facing: CameraFacing.front,
    iseKYC: true,
  );

  @override
  void initState() {
    super.initState();
  }

  String? compileeKYCID(eKYCID){
    if (eKYCID == 0) {
      return "Turn left";
    }
    if (eKYCID == 1) {
      return "Turn right";
    }
    if (eKYCID == 2) {
      return "Look straight";
    }
    if (eKYCID == 3) {
      return "Close Right Eye";
    }
    if (eKYCID == 4) {
      return "Close Left Eye";
    }
    if (eKYCID == 5) {
      return "Tilt head to the left";
    }
    if (eKYCID == 6) {
      return "Tilt head to the right";
    }
    if (eKYCID == 7) {
      return "head down";
    }
    if (eKYCID == 8) {
      return "head up";
    }
    if (eKYCID == 9) {
      return "Smiling";
    }
    return "No detect status";
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
                    if (data == this.data) return;
                    this.data = data;

                    setState(() {
                      face = Data(
                          face: Face(
                            headEulerAngleX: data.face?.headEulerAngleX,
                            headEulerAngleY: data.face?.headEulerAngleY,
                            headEulerAngleZ: data.face?.headEulerAngleZ,
                            smileProb: data.face?.smileProb,
                            rightEyeOpenProbability:
                                data.face?.rightEyeOpenProbability,
                            leftEyeOpenProbability:
                                data.face?.leftEyeOpenProbability,
                          ),
                          size: data.size,
                          guidID: data.guidID,
                          faceImage: data.faceImage,
                          flippedFaceImage: data.flippedFaceImage,
                          eKYCID: data.eKYCID,
                          rot: Rot(
                            rotX: data.rot?.rotX,
                            rotY: data.rot?.rotY,
                            rotZ: data.rot?.rotZ,
                          ));
                    });
                  },
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
                                    "x: ${face?.face?.headEulerAngleX}, y: ${face?.face?.headEulerAngleY}, z: ${face?.face?.headEulerAngleZ}",
                                    style: const TextStyle(color: Colors.green),
                                  ),
                                  Text(
                                    "smileProb: ${face?.face?.smileProb}, rightEyeOpenProbability: ${face?.face?.rightEyeOpenProbability}, leftEyeOpenProbability: ${face?.face?.leftEyeOpenProbability}",
                                    style: const TextStyle(color: Colors.green),
                                  ),
                                  Text(
                                    "eKYCID: ${compileeKYCID(face?.eKYCID)}",
                                    style: const TextStyle(color: Colors.green),
                                  ),
                                ],
                              )
                            : const SizedBox(),
                      ])),
            ],
          );
        },
      ),
    );
  }
}
