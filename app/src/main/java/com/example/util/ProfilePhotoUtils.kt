package com.example.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object ProfilePhotoUtils {
    private const val TAG = "ProfilePhotoUtils"
    private const val TARGET_IMAGE_SIZE = 512
    private const val JPEG_QUALITY = 85
    private const val MAX_INPUT_DIMENSION = 2048

    /**
     * Creates a temporary file in the cache directory and returns a content Uri via FileProvider
     * for camera capture.
     */
    fun createTempCameraUri(context: Context): Pair<Uri, File> {
        val tempDir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val tempFile = File(tempDir, "temp_profile_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        return Pair(uri, tempFile)
    }

    /**
     * Cleans up temporary camera files in cache.
     */
    fun cleanTempCameraFiles(context: Context) {
        try {
            val tempDir = File(context.cacheDir, "camera_photos")
            if (tempDir.exists() && tempDir.isDirectory) {
                tempDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao limpar arquivos temporários de câmera: ${e.message}")
        }
    }

    /**
     * Safely decodes a Bitmap from a Uri with EXIF rotation compensation and safe downsampling.
     */
    fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
        return try {
            // First, get dimensions to calculate sample size
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            var inSampleSize = 1
            if (options.outHeight > MAX_INPUT_DIMENSION || options.outWidth > MAX_INPUT_DIMENSION) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / inSampleSize) >= MAX_INPUT_DIMENSION || (halfWidth / inSampleSize) >= MAX_INPUT_DIMENSION) {
                    inSampleSize *= 2
                }
            }

            // Decode actual bitmap
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null

            // Read EXIF orientation
            var rotationAngle = 0f
            try {
                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    val exif = ExifInterface(exifStream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotationAngle = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível ler EXIF da imagem: ${e.message}")
            }

            if (rotationAngle != 0f) {
                val matrix = Matrix().apply { postRotate(rotationAngle) }
                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao carregar bitmap do Uri: ${e.message}", e)
            null
        }
    }

    /**
     * Crops and scales bitmap to a square 512x512 JPEG byte array.
     */
    fun cropAndCompressBitmap(
        sourceBitmap: Bitmap,
        cropX: Float = 0f, // Normalized 0..1 or center
        cropY: Float = 0f,
        scale: Float = 1.0f,
        rotationDegrees: Float = 0f
    ): ByteArray {
        // Apply manual rotation if requested
        val rotatedSource = if (rotationDegrees != 0f) {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, matrix, true)
        } else {
            sourceBitmap
        }

        val width = rotatedSource.width
        val height = rotatedSource.height
        val minDim = minOf(width, height)

        // Calculate square crop size based on scale
        val cropSize = (minDim / scale.coerceAtLeast(1.0f)).toInt().coerceIn(1, minDim)
        
        // Calculate offsets
        val centerX = (width / 2f) + (cropX * width)
        val centerY = (height / 2f) + (cropY * height)

        val left = (centerX - cropSize / 2f).toInt().coerceIn(0, width - cropSize)
        val top = (centerY - cropSize / 2f).toInt().coerceIn(0, height - cropSize)

        val croppedBitmap = Bitmap.createBitmap(rotatedSource, left, top, cropSize, cropSize)

        // Scale to standard avatar size (512x512)
        val scaledBitmap = Bitmap.createScaledBitmap(
            croppedBitmap,
            TARGET_IMAGE_SIZE,
            TARGET_IMAGE_SIZE,
            true
        )

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Auto center crop and compress to 512x512 JPEG.
     */
    fun autoCenterCropAndCompress(sourceBitmap: Bitmap): ByteArray {
        val minDim = minOf(sourceBitmap.width, sourceBitmap.height)
        val xOffset = (sourceBitmap.width - minDim) / 2
        val yOffset = (sourceBitmap.height - minDim) / 2
        val cropped = Bitmap.createBitmap(sourceBitmap, xOffset, yOffset, minDim, minDim)
        val scaled = Bitmap.createScaledBitmap(cropped, TARGET_IMAGE_SIZE, TARGET_IMAGE_SIZE, true)
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        return outputStream.toByteArray()
    }

    /**
     * Saves photo to device gallery (Pictures/RONYCINE).
     * Handles both local bytes and remote URLs.
     */
    suspend fun savePhotoToDeviceGallery(
        context: Context,
        imageUrl: String?,
        imageBytes: ByteArray?,
        profileName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val bytesToSave = when {
                imageBytes != null -> imageBytes
                !imageUrl.isNullOrBlank() -> {
                    // Download from remote URL
                    val url = URL(imageUrl)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 15000
                    conn.instanceFollowRedirects = true
                    conn.connect()
                    if (conn.responseCode in 200..299) {
                        conn.inputStream.use { it.readBytes() }
                    } else {
                        return@withContext Result.failure(Exception("Falha ao baixar imagem (HTTP ${conn.responseCode})"))
                    }
                }
                else -> return@withContext Result.failure(Exception("Nenhuma imagem disponível para salvar."))
            }

            val sanitizedName = profileName.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val fileName = "RONYCINE_perfil_${sanitizedName}_${System.currentTimeMillis()}.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RONYCINE")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Não foi possível criar arquivo na galeria."))

                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytesToSave)
                    out.flush()
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Log.d(TAG, "Foto salva com sucesso na galeria via MediaStore: $uri")
                Result.success("Foto salva na galeria (Pictures/RONYCINE)!")
            } else {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val appDir = File(picturesDir, "RONYCINE").apply { mkdirs() }
                val targetFile = File(appDir, fileName)

                FileOutputStream(targetFile).use { out ->
                    out.write(bytesToSave)
                    out.flush()
                }

                // Notify MediaScanner
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("image/jpeg")
                ) { path, uri ->
                    Log.d(TAG, "MediaScanner concluiu indexação: $path -> $uri")
                }

                Result.success("Foto salva na galeria!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar foto na galeria: ${e.message}", e)
            Result.failure(e)
        }
    }
}
