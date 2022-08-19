import 'package:flutter/material.dart';
import 'package:plugin_face_detection_example/screens/capture.dart';
import 'package:plugin_face_detection_example/screens/eKYC.dart';
import 'package:plugin_face_detection_example/screens/overlay_face.dart';
import 'package:plugin_face_detection_example/screens/without_controller.dart';

import 'dart:developer' as d;

import 'screens/controller.dart';


void main() => runApp(
    const MaterialApp(home: MyHome()));

class MyHome extends StatelessWidget {
  const MyHome({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Flutter Demo Home Page')),
      body: SizedBox(
        width: MediaQuery.of(context).size.width,
        height: MediaQuery.of(context).size.height,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const Controller()),
                );
              },
              child: const Text('Controller'),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const WithoutController()),
                );
              },
              child: const Text('without Controller'),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const Capture()),
                );
              },
              child: const Text('Capture Image'),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const OverlayFace()),
                );
              },
              child: const Text('Overlay image'),
            ),
            ElevatedButton(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const Ekyc()),
                );
              },
              child: const Text('eKYC'),
            ),
          ],
        ),
      ),
    );
  }
}