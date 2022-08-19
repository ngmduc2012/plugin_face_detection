package cmc.com.vn.plugin_face_detection.models

class FaceDetectionData(
    val faceData: FaceData?,
    val size: Int?,
    val guidID: Int?,
    val faceImage: String?,
    val flippedFaceImage: String?,
    val eKYCID: Int?,
    val rot: Rot?,
)