package com.example.photomatch.util
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import android.graphics.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min


object FaceNetHelper {
    private const val MODEL_FILE = "facenet.tflite"
    private const val IMAGE_SIZE = 160
    private const val EMBEDDING_SIZE = 128
    private const val MAX_IMAGE_DIMENSION = 1024  // Maximum dimension for processing

    private var interpreter: Interpreter? = null

    private val faceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)  // Changed to FAST mode
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    private fun initializeInterpreter(context: Context) {
        if (interpreter == null) {
            val options = Interpreter.Options().apply {
                numThreads = 4
                useXNNPACK = true  // Enable hardware acceleration if available
            }
            interpreter = Interpreter(loadModelFile(context), options)
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val modelPath = MODEL_FILE
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    suspend fun getFaceEmbeddings(originalBitmap: Bitmap, context: Context): FloatArray {
        try {
            // Scale down the image if it's too large
            val scaledBitmap = scaleDownBitmap(originalBitmap)
            Log.d("FaceNetHelper", "Scaled bitmap size: ${scaledBitmap.width}x${scaledBitmap.height}")

            // Initialize interpreter
            initializeInterpreter(context)

            // Detect face
            val face = detectFace(scaledBitmap) ?: throw IllegalStateException("No face detected")
            Log.d("FaceNetHelper", "Face detected with bounds: ${face.boundingBox}")

            // Process face
            val faceBitmap = cropFace(scaledBitmap, face.boundingBox)
            val byteBuffer = bitmapToByteBuffer(faceBitmap) // Input for model

            // Clean up scaled bitmap if it's different from original
            if (scaledBitmap != originalBitmap) {
                scaledBitmap.recycle()
            }

            // Generate embedding
            val outputArray = Array(1) { FloatArray(EMBEDDING_SIZE) }
            interpreter?.run(byteBuffer, outputArray)

            // Normalize embedding
            val embedding = outputArray[0]
            val norm = sqrt(embedding.map { it * it }.sum())
            for (i in embedding.indices) {
                embedding[i] /= norm
            }

            return embedding

        } catch (e: Exception) {
            Log.e("FaceNetHelper", "Error during inference: ${e.message}")
            throw e
        }
    }

    private fun scaleDownBitmap(bitmap: Bitmap): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        // If image is already small enough, return original
        if (originalWidth <= MAX_IMAGE_DIMENSION && originalHeight <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }

        // Calculate new dimensions
        val ratio = min(
            MAX_IMAGE_DIMENSION.toFloat() / originalWidth,
            MAX_IMAGE_DIMENSION.toFloat() / originalHeight
        )

        val newWidth = (originalWidth * ratio).toInt()
        val newHeight = (originalHeight * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private suspend fun detectFace(bitmap: Bitmap) = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    continuation.resume(null)
                } else {
                    val largestFace = faces.maxByOrNull {
                        it.boundingBox.width() * it.boundingBox.height()
                    }
                    continuation.resume(largestFace)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FaceNetHelper", "Face detection failed: ${e.message}")
                continuation.resume(null)
            }
    }

    private fun cropFace(bitmap: Bitmap, boundingBox: Rect): Bitmap {
        val padding = (max(boundingBox.width(), boundingBox.height()) * 0.2f).toInt()

        val left = max(0, boundingBox.left - padding)
        val top = max(0, boundingBox.top - padding)
        val right = min(bitmap.width, boundingBox.right + padding)
        val bottom = min(bitmap.height, boundingBox.bottom + padding)

        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            left,
            top,
            right - left,
            bottom - top
        )

        return Bitmap.createScaledBitmap(croppedBitmap, IMAGE_SIZE, IMAGE_SIZE, true).also {
            if (it != croppedBitmap) {
                croppedBitmap.recycle()
            }
        }
    }

    private fun preprocessImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val inputArray = Array(1) { Array(IMAGE_SIZE) { Array(IMAGE_SIZE) { FloatArray(3) } } }

        for (x in 0 until IMAGE_SIZE) {
            for (y in 0 until IMAGE_SIZE) {
                val pixel = bitmap.getPixel(x, y)
                inputArray[0][x][y][0] = (Color.red(pixel) - 127.5f) / 127.5f
                inputArray[0][x][y][1] = (Color.green(pixel) - 127.5f) / 127.5f
                inputArray[0][x][y][2] = (Color.blue(pixel) - 127.5f) / 127.5f
            }
        }

        return inputArray
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * IMAGE_SIZE * IMAGE_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        var pixelIndex = 0
        for (i in 0 until IMAGE_SIZE) {
            for (j in 0 until IMAGE_SIZE) {
                val pixelValue = intValues[pixelIndex++]
                byteBuffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f)
                byteBuffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)
                byteBuffer.putFloat((pixelValue and 0xFF) / 255.0f)
            }
        }

        return byteBuffer
    }

    private fun sqrt(value: Float): Float = kotlin.math.sqrt(value)
}