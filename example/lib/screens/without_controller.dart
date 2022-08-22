
import 'package:flutter/material.dart';
import 'package:plugin_face_detection/plugin_face_detection.dart';
import 'dart:developer' as d;

class WithoutController extends StatefulWidget {
  const WithoutController({Key? key}) : super(key: key);

  @override
  _WithoutControllerState createState() => _WithoutControllerState();
}

class _WithoutControllerState extends State<WithoutController> {
  String? face;

  FaceDetectionController controller = FaceDetectionController(
    ratio: (MediaQueryData.fromWindow(WidgetsBinding.instance.window).size.shortestSide < 600 ? false : true) ? Ratio.ratio_4_3 : Ratio.ratio_16_9,
  );

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
                  onDetect: (faceNumber, args) {
                    setState(() {
                      face = faceNumber.size.toString();
                    });
                  },
                ),
              ),
              Align(
                alignment: Alignment.bottomCenter,
                child: Container(
                  alignment: Alignment.bottomCenter,
                  height: 100,
                  color: Colors.black.withOpacity(0.4),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      Center(
                        child: SizedBox(
                          width: MediaQuery.of(context).size.width - 120,
                          height: 50,
                          child: FittedBox(
                            child: Text(
                              face ?? 'Scan something!',
                              overflow: TextOverflow.fade,
                              style: Theme.of(context)
                                  .textTheme
                                  .headline4!
                                  .copyWith(color: Colors.white),
                            ),
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
