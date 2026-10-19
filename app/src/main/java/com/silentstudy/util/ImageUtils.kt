package com.silentstudy.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import kotlin.math.max

object ImageUtils {

    /**
     * Downscales image so longest side does not exceed 1280px and compresses to JPEG ~80 quality.
     * Prevents excessive token usage while preserving equation readability.
     */
    fun downscaleAndCompressBase64(rawBase64: String, maxDimension: Int = 1280, quality: Int = 80): String {
        return try {
            val clean = rawBase64.substringAfter("base64,")
            val bytes = Base64.decode(clean, Base64.DEFAULT)
            val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return rawBase64

            val width = originalBitmap.width
            val height = originalBitmap.height

            val longest = max(width, height)
            val scaledBitmap = if (longest > maxDimension) {
                val ratio = maxDimension.toFloat() / longest.toFloat()
                val targetW = (width * ratio).toInt()
                val targetH = (height * ratio).toInt()
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            val outStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outStream)
            val compressedBytes = outStream.toByteArray()

            Base64.encodeToString(compressedBytes, Base64.NO_WRAP)
        } catch (_: Exception) {
            rawBase64
        }
    }

    /**
     * Computes SHA-256 hash of the image content for instant answer cache lookup
     */
    fun computeSha256Hash(base64String: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val clean = base64String.substringAfter("base64,")
            val digest = md.digest(clean.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            base64String.hashCode().toString()
        }
    }
}
