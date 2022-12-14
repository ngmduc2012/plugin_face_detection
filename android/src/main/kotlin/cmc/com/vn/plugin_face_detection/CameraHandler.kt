package cmc.com.vn.plugin_face_detection

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.annotation.NonNull
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import cmc.com.vn.plugin_face_detection.models.FaceData
import cmc.com.vn.plugin_face_detection.models.FaceDetectionData
import cmc.com.vn.plugin_face_detection.models.Rot
import cmc.com.vn.plugin_face_detection.utils.BitmapUtils
import cmc.com.vn.plugin_face_detection.utils.toFlip
import cmc.com.vn.plugin_face_detection.utils.toJpeg
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry
import io.flutter.view.TextureRegistry
import java.io.File

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.nio.ByteBuffer
import java.util.*

class CameraHandler(private val activity: Activity, private val textureRegistry: TextureRegistry) :
    MethodChannel.MethodCallHandler, EventChannel.StreamHandler,
    PluginRegistry.RequestPermissionsResultListener {
    companion object {
        private const val REQUEST_CODE = 22022022
        private const val NO_GUID = 20122012
        private val TAG = CameraHandler::class.java.simpleName
    }

    private var sink: EventChannel.EventSink? = null
    private var listener: PluginRegistry.RequestPermissionsResultListener? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var textureEntry: TextureRegistry.SurfaceTextureEntry? = null

    // S??? d???ng ????? ch???p h??nh
    // Using for take the picture.
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()

    @ExperimentalGetImage
    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
        when (call.method) {
            // Ki???m tra xem ???ng d???ng ???? ???????c c???p quy???n truy c???p v??o camera hay ch??a.
            // Check permission
            "state" -> checkPermission(result)
            // Y??u c???u truy???n truy c???p v??o camera
            // Request Permission
            "request" -> requestPermission(result)
            // B???t camera
            // start camera
            "start" -> start(call, result)
            // B???t flash
            // Open flash light
            "torch" -> toggleTorch(call, result)
            // D???ng camera
            // Stop camera
            "stop" -> stop(result)
            // Detect image in gallery
            // Ph??t hi???n khu??n m???t trong 1 h??nh ???nh
            "analyzeImage" -> analyzeImage(call, result)
            // G???i th??ng s??? ????? l???y g??c m???t
            // Send parameters to get face angle
            "guid" -> guid(call, result)
            // Ch???p ???nh to??n m??n h??nh - c??/ kh??ng ph??t hi???n khu??n m???t
            // Take the photo - setup/ no setup detect face
            "capture" -> capture(call, result)
            // L???y h??nh ???nh ???????c bo trong khung.
            // Take the photo in frame
            "takePicture" -> takePicture(call, result)

            else -> result.notImplemented()
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        this.sink = events
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        return listener?.onRequestPermissionsResult(requestCode, permissions, grantResults) ?: false
    }

    private fun checkPermission(result: MethodChannel.Result) {
        // Can't get exact denied or not_determined state without request. Just return not_determined when state isn't authorized
        val state =
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) 1
            else 0
        result.success(state)
    }

    private fun requestPermission(result: MethodChannel.Result) {
        listener = PluginRegistry.RequestPermissionsResultListener { requestCode, _, grantResults ->
            if (requestCode != REQUEST_CODE) {
                false
            } else {
                val authorized = grantResults[0] == PackageManager.PERMISSION_GRANTED
                result.success(authorized)
                listener = null
                true
            }
        }
        val permissions = arrayOf(Manifest.permission.CAMERA)
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE)
    }

    var facing: Int = 0

    @ExperimentalGetImage
    private fun start(call: MethodCall, result: MethodChannel.Result) {
        if (camera?.cameraInfo != null && preview != null && textureEntry != null) {
            val resolution = preview!!.resolutionInfo!!.resolution
            val portrait = camera!!.cameraInfo.sensorRotationDegrees % 180 == 0
            val width = resolution.width.toDouble()
            val height = resolution.height.toDouble()
            val size = if (portrait) mapOf(
                "width" to width,
                "height" to height
            ) else mapOf("width" to height, "height" to width)
            val answer = mapOf(
                "textureId" to textureEntry!!.id(),
                "size" to size,
                "torchable" to camera!!.cameraInfo.hasFlashUnit()
            )
            result.success(answer)
        } else {
            facing = call.argument<Int>("facing") ?: 0
            val ratio: Int? =
//                AspectRatio.RATIO_16_9
                call.argument<Int>("ratio")
            val torch: Boolean = call.argument<Boolean>("torch") ?: false
            val faceDetected: Boolean = call.argument<Boolean>("faceDetected") ?: true
            this.faceDetected = faceDetected

            val iseKYC: Boolean = call.argument<Boolean>("iseKYC") ?: false
            this.iseKYC = iseKYC


            val future = ProcessCameraProvider.getInstance(activity)
            val executor = ContextCompat.getMainExecutor(activity)

            future.addListener({
                cameraProvider = future.get()
                if (cameraProvider == null) {
                    result.error("cameraProvider", "cameraProvider is null", null)
                    return@addListener
                }
                cameraProvider!!.unbindAll()
//                cameraExecutor.shutdown()
                textureEntry = textureRegistry.createSurfaceTexture()
                if (textureEntry == null) {
                    result.error("textureEntry", "textureEntry is null", null)
                    return@addListener
                }
                // Preview
                val surfaceProvider = Preview.SurfaceProvider { request ->
                    val texture = textureEntry!!.surfaceTexture()
                    texture.setDefaultBufferSize(
                        request.resolution.width,
                        request.resolution.height
                    )
                    val surface = Surface(texture)
                    request.provideSurface(surface, executor) { }
                }

                // Build the preview to be shown on the Flutter texture
                val previewBuilder = Preview.Builder()
                if (ratio != null) {
                    if (ratio == 0) {
                        previewBuilder.setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    } else {
                        previewBuilder.setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    }

                }
                preview = previewBuilder.build().apply { setSurfaceProvider(surfaceProvider) }

                // Build the analyzer to be passed on to MLKit
                val analysisBuilder = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                if (ratio != null) {
                    if (ratio == 0) {
                        analysisBuilder.setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    } else {
                        analysisBuilder.setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    }
                }
                val analysis = analysisBuilder.build().apply { setAnalyzer(executor, analyzer) }

                // Select the correct camera
                val selector =
                    if (facing == 0) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                isImageFlipped = selector == CameraSelector.DEFAULT_FRONT_CAMERA

                camera = cameraProvider!!.bindToLifecycle(
                    activity as LifecycleOwner,
                    selector,
                    imageCapture,
                    preview,
                    analysis
                )

                val analysisSize = analysis.resolutionInfo?.resolution ?: Size(0, 0)
                val previewSize = preview!!.resolutionInfo?.resolution ?: Size(0, 0)
                Log.i("LOG", "Analyzer: $analysisSize")
                Log.i("LOG", "Preview: $previewSize")

                if (camera == null) {
                    result.error("camera", "camera is null", null)
                    return@addListener
                }

                // Register the torch listener
                camera!!.cameraInfo.torchState.observe(activity) { state ->
                    // TorchState.OFF = 0; TorchState.ON = 1
                    sink?.success(mapOf("name" to "torchState", "data" to state))
                }

                // Enable torch if provided
                camera!!.cameraControl.enableTorch(torch)

                val resolution = preview!!.resolutionInfo!!.resolution
                val portrait = camera!!.cameraInfo.sensorRotationDegrees % 180 == 0
                val width = resolution.width.toDouble()
                val height = resolution.height.toDouble()
                val size = if (portrait) mapOf(
                    "width" to width,
                    "height" to height
                ) else mapOf("width" to height, "height" to width)
                val answer = mapOf(
                    "textureId" to textureEntry!!.id(),
                    "size" to size,
                    "torchable" to camera!!.cameraInfo.hasFlashUnit()
                )
                result.success(answer)
            }, executor)
        }
    }

    private fun toggleTorch(call: MethodCall, result: MethodChannel.Result) {
        if (camera == null) {
            result.error(TAG, "Called toggleTorch() while stopped!", null)
            return
        }
        camera!!.cameraControl.enableTorch(call.arguments == 1)
        result.success(null)
    }

    private fun stop(result: MethodChannel.Result) {
        if (camera == null && preview == null) {
            result.error(TAG, "Called stop() while already stopped!", null)
            return
        }

        val owner = activity as LifecycleOwner
        camera?.cameraInfo?.torchState?.removeObservers(owner)
        cameraProvider?.unbindAll()
//        cameraExecutor.shutdown()
        textureEntry?.release()

//        analyzeMode = AnalyzeMode.NONE
        camera = null
        preview = null
        textureEntry = null
        cameraProvider = null

        result.success(null)
    }

    private fun analyzeImage(call: MethodCall, result: MethodChannel.Result) {
        val uri = Uri.fromFile(File(call.arguments.toString()))
        val inputImage = InputImage.fromFilePath(activity, uri)

        var faceFound = false
        if (faceDetected) {
            scanner.process(inputImage)
                .addOnSuccessListener { faces ->
                    faceFound = faces.size > 0
//                    for (face in faces) {
//                        Log.d("face", faces.size.toString())
//                    }
                    if (faces.size > 0) {
                        sendResult(
                            FaceDetectionData(
                                FaceData(
                                    faces.first().headEulerAngleX,
                                    faces.first().headEulerAngleY,
                                    faces.first().headEulerAngleZ,
                                    null, null, null,
                                ),
                                faces.size,
                                null,
                                null,
                                null,
                                null,
                                null
                            )
                        )
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, e.message, e)
                    result.error(TAG, e.message, e)
                }
                .addOnCompleteListener { result.success(faceFound) }
        }

    }

    // Nh???n di???n c?????i, nh??y m???t c???n s??? d???ng FaceDetectorOptions
    // Have to set FaceDetectorOptions for detect  smilingProbability, rightEyeOpenProbability, leftEyeOpenProbability
    private var scanner = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .enableTracking()
            .build()
    )
    private var faceDetected = true
    private var iseKYC = false

    @ExperimentalGetImage
    val analyzer = ImageAnalysis.Analyzer { imageProxy -> // YUV_420_888 format
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        swapDimension = !(rotationDegrees == 0 || rotationDegrees == 180)
//        when (analyzeMode) {
//            AnalyzeMode.BARCODE -> {
        val mediaImage = imageProxy.image ?: return@Analyzer
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        if (faceDetected) {
            scanner.process(inputImage)
                .addOnSuccessListener { faces ->
//                    for (face in faces) {
//                        Log.d("face", faces.size.toString())
//                    }
                    if (iseKYC) {
                        eKYC(faces)
                    }
                    if (faces.size > 0 && !iseKYC) {
                        sendResult(
                            FaceDetectionData(
                                FaceData(
                                    faces.first().headEulerAngleX,
                                    faces.first().headEulerAngleY,
                                    faces.first().headEulerAngleZ,
                                    null, null, null
                                ),
                                faces.size,
                                null,
                                null,
                                null,
                                null,
                                null
                            )
                        )
                        guidDetection(faces.first(), imageProxy, faces)
                    } else return@addOnSuccessListener
                }
                .addOnFailureListener { e -> Log.e(TAG, e.message, e) }
                .addOnCompleteListener { imageProxy.close() }
//            }
//            else -> imageProxy.close()
//        }
        } else return@Analyzer
    }


    // [NO_GIUD] bi???n gi??p x??c ?????nh g??c m???t c???n l???y ???? ???????c g???i t??? flutter xu???ng ch??a
    // Khi ch???p xong th?? gi?? tr??? g??c m???t tr??? v??? [NO_GIUD] ????? kh??ng ch???p ti???p ???nh kh??c.
    var minX: Int = NO_GUID
    var maxX: Int = NO_GUID
    var minY: Int = NO_GUID
    var maxY: Int = NO_GUID
    var minZ: Int = NO_GUID
    var maxZ: Int = NO_GUID
    private fun guid(call: MethodCall, result: MethodChannel.Result) {

        minX = call.argument("minX") ?: return
        maxX = call.argument("maxX") ?: return
        minY = call.argument("minY") ?: return
        maxY = call.argument("maxY") ?: return
        minZ = call.argument("minZ") ?: return
        maxZ = call.argument("maxZ") ?: return

    }

    private var swapDimension: Boolean = false
    private var isImageFlipped: Boolean = true

    /**
     * ################################################################################################
     * FUNCTION   :
     * DESCRIPTION:
     *
     * ------------------------------------------------------------------------------------------------
     * CH???C N??NG: Th??ng b??o h?????ng d???n l???y ???nh g??c m???t & l???y ???nh g??c m???t
     * M?? T???    :
     * (1)
     * (2)
     * (3) ?????m b???o khung h??nh kh??ng b??? gi???t, v???i t???a ????? trong kho???ng [distance] th?? s??? hi???n th??? t???a ?????
     * v???i t???a ????? c?? l??u tr?????c ???? g???m [oldRotX], [oldRotY], [oldRotZ]. C??n n???u kho???ng c??ch l???n h??n
     * [distance] th?? s??? hi???n th??? ch???m v???i c??ng th???c: T???a ????? c?? * [oldDistance] + T???a ????? m???i * [newDistance]
     * nh?? v???y t???a ????? hi???n th??? s??? kh??ng b??? gi???t khi di chuy???n g??c m???t nhanh.
     * ################################################################################################
     */
    private var oldRotX = 0f
    private var oldRotY = 0f
    private var oldRotZ = 0f
    private val distance = 4f
    private val newDistance = 0.6
    private val oldDistance = 0.3

    @SuppressLint("UnsafeOptInUsageError")
    private fun guidDetection(face: Face, imageProxy: ImageProxy, faces: List<Face>) {
        /** guidDetection 1 */
        if (minX != NO_GUID && maxX != NO_GUID && minY != NO_GUID && maxY != NO_GUID) {
            val minX = minX
            val maxX = maxX
            val minY = minY
            val maxY = maxY

            /** guidDetection 2 */
            var rotX =
                (if (isImageFlipped) -1 else 1) * if (swapDimension) face.headEulerAngleY else face.headEulerAngleX
            var rotY = -1 * if (swapDimension) face.headEulerAngleX else face.headEulerAngleY
            var rotZ = face.headEulerAngleZ

            /** guidDetection 3 */
            if (this.oldRotX == 0f) {
                this.oldRotX = rotX
            } else {
                if ((this.oldRotX - rotX < distance && this.oldRotX - rotX >= 0) || (this.oldRotX - rotX > -distance && this.oldRotX - rotX <= 0)) {
                    rotX = this.oldRotX
                } else {
                    rotX = (this.oldRotX*oldDistance + rotX*newDistance).toFloat()
                    this.oldRotX = rotX
                }
            }

            if (this.oldRotY == 0f) {
                this.oldRotY = rotY
            } else {
                if ((this.oldRotY - rotY < distance && this.oldRotY - rotY >= 0) || (this.oldRotY - rotY > -distance && this.oldRotY - rotY <= 0)) {
                    rotY = this.oldRotY
                } else {
                    rotY = (this.oldRotY*oldDistance + rotY*newDistance).toFloat()
                    this.oldRotY = rotY
                }
            }

            if (this.oldRotZ == 0f) {
                this.oldRotZ = rotZ
            } else {
                if ((this.oldRotZ - rotZ < distance && this.oldRotZ - rotZ >= 0) || (this.oldRotZ - rotZ > -distance && this.oldRotZ - rotZ <= 0)) {
                    rotZ = this.oldRotZ
                } else {
                    rotZ = (this.oldRotZ*oldDistance + rotZ*newDistance).toFloat()
                    this.oldRotZ = rotZ
                }
            }


            sendResult(
                FaceDetectionData(
                    FaceData(
                        faces.first().headEulerAngleX,
                        faces.first().headEulerAngleY,
                        faces.first().headEulerAngleZ,
                        null, null, null
                    ),
                    faces.size,
                    null,
                    null,
                    null,
                    null,
                    Rot(rotX, rotY, rotZ)
                )
            )

            /** guidDetection 4 */
            val bounds = face.boundingBox
            val imageHeight = if (swapDimension) imageProxy.width else imageProxy.height
            val imageWidth = if (swapDimension) imageProxy.height else imageProxy.width
            // N???u m???t l???n h??n 3/4 k??ch c??? ???nh th?? s??? hi???n th??? th??ng b??o: ????a thi???t b??? ra xa khu??n m???t
            // If face size > 3/4 of image size, show the notification: Take the device away from your face
            if (bounds.height() > imageHeight * 3.6 / 4 || bounds.width() > imageWidth * 3.6 / 4) {
                sendResult(
                    FaceDetectionData(
                        FaceData(
                            faces.first().headEulerAngleX,
                            faces.first().headEulerAngleY,
                            faces.first().headEulerAngleZ,
                            null, null, null
                        ),
                        faces.size,
                        11,
                        null,
                        null,
                        null,
                        Rot(rotX, rotY, rotZ)
                    )
                )
                firstMatch = 0L
//                stopTimer()
                return
            }
            /** guidDetection 5 */
            // N???u m???t nh??? h??n 1/4 k??ch c??? ???nh th?? s??? hi???n th??? th??ng b??o: ????a thi???t b??? l???i g???n khu??n m???t
            // If face size < 1/4 of image size, show the notification: Bring the device closer your face
            if (bounds.height() < imageHeight * 0.6 / 4 || bounds.width() < imageWidth * 0.6 / 4) {
                sendResult(
                    FaceDetectionData(
                        FaceData(
                            faces.first().headEulerAngleX,
                            faces.first().headEulerAngleY,
                            faces.first().headEulerAngleZ,
                            null, null, null
                        ),
                        faces.size,
                        12,
                        null,
                        null,
                        null,
                        Rot(rotX, rotY, rotZ)
                    )
                )
                firstMatch = 0L
//                stopTimer()
                return
            }

            /** guidDetection 5 */
            if (bounds.top < 0) {
                sendResult(
                    FaceDetectionData(
                        FaceData(
                            faces.first().headEulerAngleX,
                            faces.first().headEulerAngleY,
                            faces.first().headEulerAngleZ,
                            null, null, null
                        ),
                        faces.size,
                        0,
                        null,
                        null,
                        null,
                        Rot(rotX, rotY, rotZ)
                    )
                )
                firstMatch = 0L
//                stopTimer()
                return
            }

            /** guidDetection 6 */
            if (bounds.top + bounds.height() > imageHeight) {
                sendResult(
                    FaceDetectionData(
                        FaceData(
                            faces.first().headEulerAngleX,
                            faces.first().headEulerAngleY,
                            faces.first().headEulerAngleZ,
                            null, null, null
                        ),
                        faces.size,
                        1,
                        null,
                        null,
                        null,
                        Rot(rotX, rotY, rotZ)
                    )
                )
                firstMatch = 0L
//                stopTimer()
                return
            }

            /** guidDetection 7 */
            if (bounds.left < 0) {
                sendResult(
                    FaceDetectionData(
                        FaceData(
                            faces.first().headEulerAngleX,
                            faces.first().headEulerAngleY,
                            faces.first().headEulerAngleZ,
                            null, null, null
                        ),
                        faces.size,
                        3,
                        null,
                        null,
                        null,
                        Rot(rotX, rotY, rotZ)
                    )
                )
                firstMatch = 0L
//                stopTimer()
                return
            }

            /** guidDetection 8 */
            if (bounds.left + bounds.width() > imageWidth) {
                sendResult(
                    FaceDetectionData(
                        FaceData(
                            faces.first().headEulerAngleX,
                            faces.first().headEulerAngleY,
                            faces.first().headEulerAngleZ,
                            null, null, null
                        ),
                        faces.size,
                        2,
                        null,
                        null,
                        null,
                        Rot(rotX, rotY, rotZ)
                    )
                )
                firstMatch = 0L
//                stopTimer()
                return
            }

            /** guidDetection 9 */
            if (rotX > maxX) {
                sendResult(
                    FaceDetectionData(
                        FaceData(
                            faces.first().headEulerAngleX,
                            faces.first().headEulerAngleY,
                            faces.first().headEulerAngleZ,
                            null, null, null
                        ),
                        faces.size,
                        5,
                        null,
                        null,
                        null,
                        Rot(rotX, rotY, rotZ)
                    )
                )
                firstMatch = 0L
//                stopTimer()
                return
            }

            /** guidDetection 10 */
            if (rotX < minX) {
                sendResult(
                    FaceDetectionData(
                        FaceData(
                            faces.first().headEulerAngleX,
                            faces.first().headEulerAngleY,
                            faces.first().headEulerAngleZ,
                            null, null, null
                        ),
                        faces.size,
                        6,
                        null,
                        null,
                        null,
                        Rot(rotX, rotY, rotZ)
                    )
                )
                firstMatch = 0L
//                stopTimer()
                return
            }

            /** guidDetection 11 */
            if (rotY > maxY) {
                sendResult(
                    FaceDetectionData(
                        FaceData(
                            faces.first().headEulerAngleX,
                            faces.first().headEulerAngleY,
                            faces.first().headEulerAngleZ,
                            null, null, null
                        ),
                        faces.size,
                        7,
                        null,
                        null,
                        null,
                        Rot(rotX, rotY, rotZ)
                    )
                )
                firstMatch = 0L
//                stopTimer()
                return
            }

            /** guidDetection 12 */
            if (rotY < minY) {
                sendResult(
                    FaceDetectionData(
                        FaceData(
                            faces.first().headEulerAngleX,
                            faces.first().headEulerAngleY,
                            faces.first().headEulerAngleZ,
                            null, null, null
                        ),
                        faces.size,
                        8,
                        null,
                        null,
                        null,
                        Rot(rotX, rotY, rotZ)
                    )
                )
                firstMatch = 0L
//                stopTimer()
                return
            }

            if (firstMatch == 0L) {
                firstMatch = System.currentTimeMillis()
            }

            if (!allowTakeThePhoto(System.currentTimeMillis())) {
                return
            }
            /**(1)*/
//            if (!isRunningTimer) {
//                timer.start()
//            }
//
//
//            if (!isGetImageCallback) {
//                return
//            }
            /**(3)*/
            val bmp = BitmapUtils.getBitmap(imageProxy) ?: return

            /** guidDetection 13 */
            val croppedFace = resizeImage(cropFace(bmp, bounds))
            val faceImage =
                writeFile("${UUID.randomUUID()}.jpg", croppedFace.toFlip(true, false).toJpeg())
            val flippedFaceImage = writeFile("${UUID.randomUUID()}.jpg", croppedFace.toJpeg())
            sendResult(
                FaceDetectionData(
                    FaceData(
                        faces.first().headEulerAngleX,
                        faces.first().headEulerAngleY,
                        faces.first().headEulerAngleZ,
                        null, null, null
                    ),
                    faces.size,
                    null,
                    faceImage,
                    flippedFaceImage,
                    null,
                    Rot(rotX, rotY, rotZ)
                )
            )
            firstMatch = 0L
//            stopTimer()
            this.minX = NO_GUID
            this.maxX = NO_GUID
            this.minY = NO_GUID
            this.maxY = NO_GUID
            this.minY = NO_GUID
            this.maxY = NO_GUID
        }
    }

    /**
     * Resized image size is less than maxW and maxH
     * Scale w : h is corresponding scale maxW : maxW*h/w when the width of image greater than
     * max width of image maxW
     * Scale w : h is corresponding scale maxH*w/h : maxH when the height of image greater than
     * max height of image maxH
     * =====================================================================================
     * Gi??m k??ch c??? h??nh ???nh kh??ng qu?? maxW v?? maxH
     * T??? l??? w : h t????ng ???ng v???i maxW : maxW*h/w khi chi???u d??i ???nh w l???n h??n chi???u d??i t???i ??a maxW
     * T??? l??? w : h t????ng ???ng v???i maxH*w/h : maxH khi chi???u cao ???nh h l???n h??n chi???u cao t???i ??a maxH
     * */
    private fun resizeImage(bmp: Bitmap): Bitmap {
        val maxW = 1024
        val maxH = 1024
        val w = bmp.width
        val h = bmp.height
        return if (w > maxW && h > maxH) {
            if (maxW / w < maxH / h) {
                Bitmap.createScaledBitmap(bmp, maxW, maxW * h / w, true)
            } else {
                Bitmap.createScaledBitmap(bmp, maxH * w / h, maxH, true)
            }
        } else if (w > maxW && h <= maxH) {
            Bitmap.createScaledBitmap(bmp, maxW, maxW * h / w, true)
        } else if (w <= maxW && h > maxH) {
            Bitmap.createScaledBitmap(bmp, maxH * w / h, maxH, true)
        } else bmp
    }

    /**
     * boundingBox is face.boundingBox
     * get image = face.boundingBox x 2
     * x = x of face.boundingBox reduce 1/2 width of face.boundingBox
     * y = y of face.boundingBox increase 1/2 height of face.boundingBox
     * if x, y, width, height are more than width & height of [bmp] set be equal to width & height of [bmp]
     * =====================================================================================
     * H??nh ???nh ???????c crop s??? g???p ????i ???nh nh???n di???n m???t face.boundingBox
     * x khi ???? s??? b???ng t???a ????? x c???a face.boundingBox - 1/2 chi???u r???ng face.boundingBox
     * y khi ???? s??? b???ng t???c ????? y c???a face.boundingBox + 1/2 chi???u cao face.boundingBox
     * N???u x, y, ????? r???ng, ????? cao v?????t ra ngo??i chi???u r???ng, cao c???a [bmp] th?? s??? l???y ????? r???ng, cao c???a [bmp]
     * */
    private fun cropFace(bmp: Bitmap, boundingBox: Rect): Bitmap {
        return Bitmap.createBitmap(
            bmp,
            //x
            if (boundingBox.left <= boundingBox.width() / 2)
                0
            else
                (boundingBox.left - boundingBox.width() / 2),
            //y
            if (boundingBox.top <= boundingBox.height() / 2)
                0
            else
                (boundingBox.top - boundingBox.height() / 2),
            //w
            if (boundingBox.left <= boundingBox.width() / 2)
                (if (bmp.width >= boundingBox.width() * 2)
                    boundingBox.width() * 2
                else
                    bmp.width)
            else
                (if ((boundingBox.left + boundingBox.width() * 3 / 2) < bmp.width)
                    (boundingBox.width() * 2)
                else
                    (bmp.width - boundingBox.left + boundingBox.width() / 2)),
            //h
            if (boundingBox.top <= boundingBox.height() / 2)
                (if (bmp.height > boundingBox.height() * 2)
                    boundingBox.height() * 2
                else
                    bmp.height)
            else
                (if ((boundingBox.top + boundingBox.height() * 3 / 2) < bmp.height)
                    (boundingBox.height() * 2)
                else
                    (bmp.height - boundingBox.top + boundingBox.height() / 2)),
        )
    }

    // Ghi file v??o th?? m???c
    // Write file to store
    private fun writeFile(fileName: String, data: ByteArray): String {
        val file = File(activity.cacheDir, fileName)
        file.writeBytes(data)
        return file.absolutePath
    }

    // G???i k???t qu??? v??? flutter.
    // Send result to flutter.
    fun sendResult(faceDetectionData: FaceDetectionData) {
        val event = mapOf(
            "name" to "faceAndroid",
            "data" to faceDetectionData.data,
        )
        sink?.success(event)
    }

    private val FaceData.data: Map<String, Any?>
        get() = mapOf(
            "headEulerAngleX" to headEulerAngleX,
            "headEulerAngleY" to headEulerAngleY,
            "headEulerAngleZ" to headEulerAngleZ,
            "smileProb" to smileProb,
            "rightEyeOpenProbability" to rightEyeOpenProbability,
            "leftEyeOpenProbability" to leftEyeOpenProbability,
        )
    private val Rot.data: Map<String, Float?>
        get() = mapOf(
            "rotX" to rotX,
            "rotY" to rotY,
            "rotZ" to rotZ,
        )
    private val FaceDetectionData.data: Map<String, Any?>
        get() = mapOf(
            "faceData" to faceData?.data,
            "size" to size,
            "guidID" to guidID,
            "faceImage" to faceImage,
            "flippedFaceImage" to flippedFaceImage,
            "eKYCID" to eKYCID,
            "rot" to rot?.data,
        )

    /**
     * ?????i 0,5 gi??y ch???p h??nh:
     *
     * [firstMatch] l??u th???i gian ?????u ti??n t???a ????? th???a m??n. C??c l???n th???a m??n t???a ????? ti???p theo
     * s??? so s??nh th???i gian hi???n t???i v?? [firstMatch] n???u l???n h??n 500 mili gi??y th?? cho ph??p
     * ch???p h??nh. Trong kho???ng th?????i gian so s??nh 500 mili gi??y n???u t???a ????? kh??ng th???a m??n th??
     * [firstMatch] = 0L, t???c t??nh l???i t??? ?????u.
     * */
    private var firstMatch: Long = 0L
    private fun allowTakeThePhoto(timeNow: Long): Boolean {
        sendResult(
            FaceDetectionData(
                null,
                null,
                10,
                null,
                null,
                null,
                null
            )
        )
        return timeNow - firstMatch > 500 && firstMatch != 0L
    }


    /**
     * ################################################################################################
     * FUNCTION   : Waiting 0,5 seconds to take the image, The purpose is to make sure no-take the
     * blurry image due to moving.
     * DESCRIPTION:
     *
     * Overview: The first time matching coordinates, start [timer]. After around (1250 -500)
     * milliseconds, if still matching coordinates, take the image. Else, run again [timer]
     *
     * (1) Start running [timer] when the first time matching coordinates (The condition of x,y is
     * satisfied)
     * [isRunningTimer] help ensures the next time of matching coordinates, [timer] will not run again.
     * (2) Show the notification hold the face status to ensure (x,y) no changes
     * (3) when [timer] is less than 500 milliseconds, allow the device takes the image
     * ([isGetImageCallback] = true)
     * (4) when [timer] finish, to stop allow the device takes the image ([isGetImageCallback] = false)
     * and set [isRunningTimer] = false
     * (5) [timer] run again if (x,y) does not match the condition.
     * ------------------------------------------------------------------------------------------------
     * CH???C N??NG: ?????i 0,5 gi??y ????? ch???p h??nh, m???c ????ch nh???m ?????o b???o ???nh kh??ng b??? m??? do ??ang di chuy???n.
     * M?? T???    :
     *
     * T???ng quan: L???n ?????u sau khi kh???p t???a ????? c???n ch???p, bi???n [timer] b???t ?????u ch???y. Sau kho???ng d?????i (1250
     * - 500) mili gi??y, n???u to??? ????? v???n kh???p s??? ti???n h??nh l???y h??nh ???nh ????, n???u kh??ng s??? t??nh l???i [timer]
     * t??? ?????u.
     *
     * (1) B???t ?????u ch???y [timer] khi kh???p t???a ????? l???n ?????u ti??n (c??c ??i???u ki???n to???n ????? x,y th???a m??n)
     * Bi???n [isRunningTimer] ?????m b???o r??ng l???n kh???p t???a ????? sau, [timer] s??? kh??ng b??? kh???i t???o l???i l???n 2
     * (2) Hi???n th??? th??ng b??o gi??? tr???ng th??i khu??n m???t ????? ?????m b???o t???a ????? (x,y) kh??ng thay ?????i
     * (3) Khi [timer] c??n d?????i 500 mili gi??y s??? cho ph??p ch???p h??nh ???nh (bi???n [isGetImageCallback] =
     * true)
     * (4) Khi [timer] k???t th??c s??? d???ng cho ph??p l???y ???nh ([isGetImageCallback] = false) v?? t???t tr???ng
     * th??i [timer] ??ang ho???t ?????ng ([isRunningTimer] = false)
     * (5) [timer] s??? ???????c t??nh l???i t??? ?????u khi t???a ????? (x,y) kh??ng th???a m??n ??i???u ki???n
     * ################################################################################################
     */
//    var isGetImageCallback: Boolean = false
//    var isRunningTimer: Boolean = false
//    private val timer = object : CountDownTimer(1250, 250) {
//        override fun onTick(millisUntilFinished: Long) {
//            isRunningTimer = true
//            /**(2)*/
////            listener.onChangeGuide(10)
////            Log.d("ok" , "$millisUntilFinished")
//            sendResult(
//                FaceDetectionData(
//                    null,
//                    null,
//                    10,
//                    null,
//                    null,
//                    null,
//                    null
//                )
//            )
//            /**(3)*/
//            if (millisUntilFinished <= 500) isGetImageCallback = true
//        }
//
//        /**(4)*/
//        override fun onFinish() {
//            isGetImageCallback = false
//            isRunningTimer = false
//        }
//    }
//
//    /**(5)*/
//    private fun stopTimer() {
//        timer.cancel()
//        isGetImageCallback = false
//        isRunningTimer = false
//    }


    /**
     * ################################################################################################
     * FUNCTION   : Capture Image
     * DESCRIPTION:
     *
     * ------------------------------------------------------------------------------------------------
     * CH???C N??NG: C??i ?????t tr???ng th??i ban ?????u
     * M?? T???    :
     * (1) L???y gi?? tr??? trong bi???n [faceDetectedCapture] g???i t??? flutter. X??c ?????nh c?? ph??t hi???n khu??n m???t
     * trong khung h??nh kh??ng.
     * (2) T???o file l??u d?????i d???ng cache.
     * (3) Chuy???n h??nh ???nh v??? d???ng bitmap.
     * (4) Ph??t hi???n khu??n m???t c?? trong h??nh ???nh kh??ng.
     * (5) Tr??? v??? k???t qu??? l?? ???????ng d???n file.
     * ################################################################################################
     */
    @ExperimentalGetImage
    private fun capture(call: MethodCall, result: MethodChannel.Result) {
        /** capture 1 */
        val faceDetectedCapture: Boolean = call.argument<Boolean>("faceDetectedCapture") ?: false

        /** capture 2 */
        val file = File(activity.cacheDir, "${UUID.randomUUID()}.jpg")

        imageCapture.takePicture(ContextCompat.getMainExecutor(activity.applicationContext),
            object : ImageCapture.OnImageCapturedCallback() {
                @SuppressLint("UnsafeExperimentalUsageError")
                override fun onCaptureSuccess(image: ImageProxy) {

                    /** capture 3 */
                    val bmp = imageProxyToBitmap(image)
                    val rotation = image.imageInfo.rotationDegrees
                    val matrix = Matrix()
                    matrix.postRotate(rotation.toFloat())
                    val sceneBitmap = Bitmap.createBitmap(
                        bmp,
                        0,
                        0,
                        bmp.width,
                        bmp.height,
                        matrix,
                        true
                    )


                    if (faceDetectedCapture) {
                        /** capture 4 */
                        val mediaImage = image.image ?: return
                        val inputImage =
                            InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                        scanner.process(inputImage)
                            .addOnSuccessListener { faces ->
                                if (faces.size > 0) {
                                    /** capture 5 */
                                    val bytes = sceneBitmap.toFlip(true, false).toJpeg(75)
                                    file.writeBytes(bytes)
                                    sendResult(
                                        FaceDetectionData(
                                            null,
                                            null,
                                            null,
                                            file.absolutePath,
                                            null,
                                            null,
                                            null
                                        )
                                    )
                                } else {
                                    sendResult(
                                        FaceDetectionData(
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null
                                        )
                                    )
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, e.message, e)
                                result.error(TAG, e.message, e)
                            }
                            .addOnCompleteListener { }

                    } else {
                        /** capture 5 */
                        val bytes = sceneBitmap.toFlip(true, false).toJpeg(75)
                        file.writeBytes(bytes)
                        sendResult(
                            FaceDetectionData(
                                null,
                                null,
                                null,
                                file.absolutePath,
                                null,
                                null,
                                null
                            )
                        )
                    }
                    image.close()
                }

                override fun onError(error: ImageCaptureException) {
                    FaceDetectionData(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                }
            })
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * ################################################################################################
     * FUNCTION   : Capture Image in the frame
     * DESCRIPTION:
     *
     * ------------------------------------------------------------------------------------------------
     * CH???C N??NG: Ch???p h??nh ???nh trong 1 khung h??nh.
     * M?? T???    :
     * (1) L???y c??c gi?? tr??? c???a khung h??nh ???????c g???i t??? flutter
     * (2) T???o file l??u d?????i d???ng cache.
     * (3) X??? l?? ???nh.
     * (4) T???o flie v?? tr??? v??? k???t qu??? l?? ???????ng d???n file.
     * ################################################################################################
     */
    @ExperimentalGetImage
    private fun takePicture(call: MethodCall, result: MethodChannel.Result) {

        /** takePicture 1 */
        val boxWidth: Double = call.argument<Double>("boxWidth") ?: return
        val boxHeight: Double = call.argument<Double>("boxHeight") ?: return
        val boxTop: Double = call.argument<Double>("boxTop") ?: return
        val boxLeft: Double = call.argument<Double>("boxLeft") ?: return
        val screenWidth: Double = call.argument<Double>("screenWidth") ?: return
        val screenHeight: Double = call.argument<Double>("screenHeight") ?: return
        result.success(null)

        /** takePicture 2 */
        val fileCrop = File(activity.cacheDir, "${UUID.randomUUID()}.jpg")
        val file = File(activity.cacheDir, "${UUID.randomUUID()}TruePath.jpg")
        imageCapture.takePicture(ContextCompat.getMainExecutor(activity.applicationContext),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {

                    /** takePicture 3 */
                    processImage(
                        boxWidth,
                        boxHeight,
                        boxTop,
                        boxLeft,
                        screenWidth,
                        screenHeight,
                        fileCrop,
                        file,
                        image
                    )
                    /** takePicture 4 */
                    sendResult(
                        FaceDetectionData(
                            null,
                            null,
                            null,
                            fileCrop.absolutePath,
                            file.absolutePath,
                            null,
                            null
                        )
                    )
                    image.close()

                }

                override fun onError(error: ImageCaptureException) {
                    sendResult(
                        FaceDetectionData(
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                        )
                    )
                }
            })

    }

    //X??? l?? h??nh ???nh
    @SuppressWarnings("unchecked")
    private fun processImage(
        boxWidth: Double, boxHeight: Double,
        boxTop: Double, boxLeft: Double,
        screenWidth: Double, screenHeight: Double,
        fileCrop: File, file: File, image: ImageProxy
    ) {
        val bmp = imageProxyToBitmap(image)

        val rotation = image.imageInfo.rotationDegrees
        val matrix = Matrix()
        matrix.postRotate(rotation.toFloat())
        val croppedWidth: Double
        val croppedHeight: Double
        val top: Double
        val left: Double
        val bytesFace: ByteArray
        val bytes: ByteArray


//        Log.d("rotation", "Rotation: $rotation")
        // I. X??? l?? h??nh ???nh n???u h??nh ???nh b??? xoay.
        // rotation = 0f
        if (rotation == 0) {
            top = bmp.height * boxTop / screenHeight
            croppedHeight = boxHeight * bmp.height / screenHeight
            croppedWidth = croppedHeight * boxWidth / boxHeight
            left = (bmp.width - croppedWidth) / 2
        }
        // rotation = 90f/270f
        else {
            croppedWidth = bmp.width / screenHeight * boxHeight
            croppedHeight = croppedWidth * boxWidth / boxHeight
            top = (bmp.height - croppedHeight) / 2
            // Camera Front
            left = if (facing == 0) {
                bmp.width - bmp.width / screenHeight * boxTop - croppedWidth
            }
            // Camera Back
            else {
                bmp.width / screenHeight * boxTop
            }
        }

        // II. Crop h??nh ???nh
        val bmpSelf = Bitmap.createScaledBitmap(
            bmp,
            (bmp.width * 0.3).toInt(),
            (bmp.height * 0.3).toInt(),
            true
        )

        bytesFace = Bitmap.createBitmap(
            bmpSelf,
            0,
            0,
            bmpSelf.width,
            bmpSelf.height,
            matrix,
            true
        ).toFlip(xFlip = true, yFlip = false).toJpeg(75)
        file.writeBytes(bytesFace)
        // Camera Front
        if (facing == 0) {
//            Log.d("ok", "facing: ${facing}")
            bytes = Bitmap.createBitmap(
                bmp,
                left.toInt(),
                top.toInt(),
                croppedWidth.toInt(),
                croppedHeight.toInt(),
                matrix,
                true
            ).toFlip(xFlip = true, yFlip = false).toJpeg(75)
        }
        // Camera Back
        else {
//            Log.d("ok", "left.toInt() : ${left.toInt()}")
//            Log.d("ok", "top.toInt() : ${top.toInt()}")
            bytes = Bitmap.createBitmap(
                bmp,
                left.toInt(),
                top.toInt(),
                croppedWidth.toInt(),
                croppedHeight.toInt(),
                matrix,
                true
            ).toJpeg(75)

        }

        fileCrop.writeBytes(bytes)
        image.close()

    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun eKYC(faces: List<Face>) {
        if (faces.isNotEmpty()) {
            val rotX = faces.first().headEulerAngleX
            val rotY = faces.first().headEulerAngleY
            val rotZ = faces.first().headEulerAngleZ
            val smileProb = faces.first().smilingProbability
            val rightEyeOpenProbability = faces.first().rightEyeOpenProbability
            val leftEyeOpenProbability = faces.first().leftEyeOpenProbability

            var eKYCID: Int? = null
            //Detect the face in turn left depends rotY
            if (checkFaceTurnLeft()) {
//            Log.d("ok", "Turn left")
                eKYCID = 0
            }
            //detect the face in turn right depends rotY
            else if (
                rotX > -6 && rotX < 6 && rotZ > -6 && rotZ < 6 &&
                rotY < -18
            ) {
//            Log.d("ok", "Turn right")
                eKYCID = 1
            } else if (rotX > -4 && rotX < 4 && rotZ > -4 && rotZ < 4 && rotY > -4 && rotY < 4 &&
                rightEyeOpenProbability != null && leftEyeOpenProbability != null && smileProb != null
            ) {
                //Detect look straight face
                if ((rightEyeOpenProbability > 0.8f) && (leftEyeOpenProbability > 0.8f) && smileProb < 0.5f) {
//                Log.d("ok", "Look straight")
                    eKYCID = 2
                }

            }
            //tilt your head to the left depends rotZ
            else if (
                rotX > -10 && rotX < 10 && rotY > -10 && rotY < 10 &&
                rotZ < -18
            ) {
//            Log.d("ok", "Tilt head to the left")
                eKYCID = 5
            }
            //tilt your head to the right depends rotZ
            else if (
                rotX > -10 && rotX < 10 && rotY > -10 && rotY < 10 &&
                rotZ > 18
            ) {
//            Log.d("ok", "Tilt head to the right")
                eKYCID = 6
            }
            //head down depends rotX
            else if (
                rotY > -6 && rotY < 6 && rotZ > -6 && rotZ < 6 &&
                rotX < -18
            ) {
//            Log.d("ok", "head down")
                eKYCID = 7
            }
            //head up depends rotX
            else if (
                rotY > -6 && rotY < 6 && rotZ > -6 && rotZ < 6 &&
                rotX > 18
            ) {
//            Log.d("ok", "head up")
                eKYCID = 8
            }
            //Detect the smiling face
            else if (smileProb != null && smileProb > 0.55f) {
//            Log.d("ok", "Smiling")
                eKYCID = 9


            }
            //Detect close just right eye
            else if (
                (rightEyeOpenProbability != null)
                && (leftEyeOpenProbability != null)
                && ((rightEyeOpenProbability / leftEyeOpenProbability) > 2)
            ) {
//                Log.d("ok", "Close Right Eye")
                eKYCID = 3
            }
            //Detect close just left eye

            else if ((rightEyeOpenProbability != null)
                && (leftEyeOpenProbability != null)
                && ((leftEyeOpenProbability / rightEyeOpenProbability) > 2)
            ) {
//                Log.d("ok", "Close Left Eye")
                eKYCID = 4
            } else {
                eKYCID = null
            }

            sendResult(
                FaceDetectionData(
                    FaceData(
                        rotX,
                        rotY,
                        rotZ,
                        smileProb,
                        rightEyeOpenProbability,
                        leftEyeOpenProbability,
                    ),
                    null,
                    null,
                    null,
                    null,
                    eKYCID,
                    null
                )
            )

        } else {
            sendResult(
                FaceDetectionData(
                    null,
                    null,
                    null,
                    null,
                    null,
                    10,
                    null
                )
            )
        }
    }

    private fun checkFaceTurnLeft() = rotX > -6 && rotX < 6 && rotZ > -6 && rotZ < 6 &&
            rotY > 18
}
