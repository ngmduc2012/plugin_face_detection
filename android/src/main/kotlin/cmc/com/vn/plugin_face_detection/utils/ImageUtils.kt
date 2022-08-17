package cmc.com.vn.plugin_face_detection.utils

import android.graphics.Bitmap
import android.graphics.Matrix
import java.io.ByteArrayOutputStream

fun Bitmap.toFlip(xFlip: Boolean, yFlip: Boolean): Bitmap {
    val matrix = Matrix()
    matrix.postScale(
            if (xFlip) -1f else 1f,
            if (yFlip) -1f else 1f,
            this.width / 2f,
            this.height / 2f
    )
    return Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
}


fun Bitmap.toJpeg(quality: Int = 100): ByteArray {
    val out = ByteArrayOutputStream()
    this.compress(Bitmap.CompressFormat.JPEG, quality, out)
    out.flush()
    out.close()
    return out.toByteArray()
}