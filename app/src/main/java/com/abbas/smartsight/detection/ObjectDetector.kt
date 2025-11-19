package com.abbas.smartsight.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

class ObjectDetector(
    private val context: Context,
    private val modelPath: String,
    private val labelsPath: String = "labels.txt",
    private val inputSize: Int = 640
) {
    private var interpreter: Interpreter? = null
    private val iouThreshold = 0.45f
    private val labelsList: List<String>

    init {
        loadModel()
        labelsList = loadLabels()
    }

    private fun loadModel() {
        try {
            val model = FileUtil.loadMappedFile(context, modelPath)
            val options = Interpreter.Options().apply {
                setNumThreads(4)
                setUseNNAPI(false)
            }
            interpreter = Interpreter(model, options)
            Log.d("ObjectDetector", "Model loaded: $modelPath with ${labelsList.size} classes")
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Error loading model: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadLabels(): List<String> {
        return try {
            context.assets.open(labelsPath).bufferedReader().use { reader ->
                reader.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Error loading labels: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    fun detect(bitmap: Bitmap, frameWidth: Int): List<BoundingBox> {
        if (interpreter == null || labelsList.isEmpty()) {
            Log.e("ObjectDetector", "Interpreter or labels not loaded")
            return emptyList()
        }

        try {
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = bitmapToByteBuffer(resizedBitmap)

            val outputShape = interpreter!!.getOutputTensor(0).shape()
            val numDetections = outputShape[2]
            val numPredictions = outputShape[1]

            val outputBuffer = ByteBuffer.allocateDirect(4 * numPredictions * numDetections).apply {
                order(ByteOrder.nativeOrder())
            }

            interpreter?.run(inputBuffer, outputBuffer)

            return processOutput(outputBuffer, bitmap.width, bitmap.height, numDetections)
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Detection error: ${e.message}")
            e.printStackTrace()
            return emptyList()
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        return byteBuffer
    }

    private fun processOutput(
        outputBuffer: ByteBuffer,
        originalWidth: Int,
        originalHeight: Int,
        numDetections: Int
    ): List<BoundingBox> {
        outputBuffer.rewind()

        val detections = mutableListOf<BoundingBox>()
        val numClasses = labelsList.size

        val leftBoundary = originalWidth / 3f
        val rightBoundary = originalWidth * 2f / 3f

        for (i in 0 until numDetections) {
            val x = outputBuffer.getFloat((0 * numDetections + i) * 4)
            val y = outputBuffer.getFloat((1 * numDetections + i) * 4)
            val w = outputBuffer.getFloat((2 * numDetections + i) * 4)
            val h = outputBuffer.getFloat((3 * numDetections + i) * 4)

            var maxConf = 0f
            var maxIdx = -1

            for (j in 0 until numClasses) {
                val conf = outputBuffer.getFloat(((4 + j) * numDetections + i) * 4)
                if (conf > maxConf) {
                    maxConf = conf
                    maxIdx = j
                }
            }

            if (maxIdx >= 0 && maxIdx < labelsList.size) {
                val className = labelsList[maxIdx]

                // Confidence Thresholds
                // DYNAMIC THRESHOLD: Chair = 0.30, Others = 0.15
                val threshold = if (className.equals("Chair", ignoreCase = true)) {
                    0.35f
                } else {
                    0.15f
                }

                if (maxConf > threshold) {
                    val x1 = ((x - w / 2f) / inputSize) * originalWidth
                    val y1 = ((y - h / 2f) / inputSize) * originalHeight
                    val x2 = ((x + w / 2f) / inputSize) * originalWidth
                    val y2 = ((y + h / 2f) / inputSize) * originalHeight

                    val xCenter = (x1 + x2) / 2f

                    val location = when {
                        xCenter < leftBoundary -> "left side"
                        xCenter > rightBoundary -> "right side"
                        else -> "front"
                    }

                    detections.add(
                        BoundingBox(
                            x1 = x1.coerceIn(0f, originalWidth.toFloat()),
                            y1 = y1.coerceIn(0f, originalHeight.toFloat()),
                            x2 = x2.coerceIn(0f, originalWidth.toFloat()),
                            y2 = y2.coerceIn(0f, originalHeight.toFloat()),
                            cx = x, cy = y, w = w, h = h,
                            cnf = maxConf,
                            cls = maxIdx,
                            clsName = className,
                            location = location
                        )
                    )

                    Log.d("ObjectDetector", "$className: $maxConf (threshold: $threshold)")
                }
            }
        }

        val filtered = applyNMS(detections)
        Log.d("ObjectDetector", "Raw: ${detections.size}, After NMS: ${filtered.size}")
        return filtered
    }

    private fun applyNMS(boxes: List<BoundingBox>): List<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while (sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.removeAt(0)
            selectedBoxes.add(first)

            sortedBoxes.removeAll { box ->
                calculateIoU(first, box) > iouThreshold
            }
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = max(box1.x1, box2.x1)
        val y1 = max(box1.y1, box2.y1)
        val x2 = min(box1.x2, box2.x2)
        val y2 = min(box1.y2, box2.y2)

        val intersectionArea = max(0f, x2 - x1) * max(0f, y2 - y1)
        val box1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val box2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)

        return if (box1Area + box2Area - intersectionArea > 0) {
            intersectionArea / (box1Area + box2Area - intersectionArea)
        } else {
            0f
        }
    }

    fun close() {
        interpreter?.close()
    }
}
