package com.chuyen_de_2.foodorder.core.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class CccdScanResult(
    val cccdNumber: String? = null,
    val fullName: String? = null,
    val dateOfBirth: String? = null,
    val gender: String? = null,
    val address: String? = null,
    val district: String? = null,
    val rawText: String = ""
) {
    val isSuccess: Boolean
        get() = !cccdNumber.isNullOrBlank() || !fullName.isNullOrBlank()
}

object CccdOcrHelper {

    private val DISTRICTS_HCMC = listOf(
        "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6",
        "Quận 7", "Quận 8", "Quận 10", "Quận 11", "Quận 12",
        "Bình Thạnh", "Gò Vấp", "Phú Nhuận", "Tân Bình", "Tân Phú",
        "Bình Tân", "Thủ Đức", "Hóc Môn", "Bình Chánh", "Nhà Bè"
    )

    private val IGNORED_NAME_KEYWORDS = listOf(
        "CONG HOA", "CỘNG HÒA", "XA HOI", "XÃ HỘI", "CHU NGHIA", "CHỦ NGHĨA",
        "DOC LAP", "ĐỘC LẬP", "TU DO", "TỰ DO", "HANH PHUC", "HẠNH PHÚC",
        "CAN CUOC", "CĂN CƯỚC", "CHUNG MINH", "CHỨNG MINH", "CONG DAN", "CÔNG DÂN",
        "NHAN DAN", "NHÂN DÂN", "VIET NAM", "VIỆT NAM", "CITIZEN", "IDENTITY", "CARD",
        "HO VA TEN", "HỌ VÀ TÊN", "FULL NAME", "GIOI TINH", "GIỚI TÍNH", "NGAY SINH",
        "DATE OF BIRTH", "NOI THUONG TRU", "NƠI THƯỜNG TRÚ", "QUE QUAN", "QUÊ QUÁN",
        "QUOC TICH", "QUỐC TỊCH", "CO GIA TRI", "CÓ GIÁ TRỊ"
    )

    /**
     * Nhận diện thẻ CCCD từ ảnh Bitmap
     */
    suspend fun scanCccdFromBitmap(bitmap: Bitmap): Result<CccdScanResult> = withContext(Dispatchers.Default) {
        try {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val text: Text = suspendCancellableCoroutine { cont ->
                recognizer.process(inputImage)
                    .addOnSuccessListener { visionText ->
                        if (cont.isActive) cont.resume(visionText)
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) cont.resumeWithException(error)
                    }
            }

            val result = parseCccdText(text.text)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Nhận diện thẻ CCCD từ Uri (chụp ảnh hoặc thư viện)
     */
    suspend fun scanCccdFromUri(context: Context, uri: Uri): Result<CccdScanResult> = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeRotatedBitmap(context, uri, maxDimension = 1280)
                ?: return@withContext Result.failure(IllegalArgumentException("Không thể đọc ảnh thẻ"))
            scanCccdFromBitmap(bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Thuật toán phân tích chuỗi văn bản OCR để trích xuất CCCD, Họ tên, Ngày sinh, v.v.
     */
    fun parseCccdText(rawText: String): CccdScanResult {
        if (rawText.isBlank()) return CccdScanResult(rawText = "")

        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }

        var cccdNumber: String? = null
        var fullName: String? = null
        var dateOfBirth: String? = null
        var gender: String? = null
        var address: String? = null
        var district: String? = null

        // 1. Tìm số CCCD (12 chữ số) hoặc CMND (9 chữ số)
        // Ưu tiên dòng có chữ "Số" / "No." / "Số định danh"
        for (i in lines.indices) {
            val line = lines[i]
            val digitsOnly = line.replace(Regex("[^0-9]"), "")

            if (digitsOnly.length == 12) {
                cccdNumber = digitsOnly
                break
            }

            val isNumberLabel = line.contains("Số", true) ||
                    line.contains("No", true) ||
                    line.contains("định danh", true) ||
                    line.contains("identification", true)

            if (isNumberLabel) {
                // Kiểm tra 12 số trong cùng dòng
                val match12 = Regex("""\b\d{12}\b""").find(line)
                if (match12 != null) {
                    cccdNumber = match12.value
                    break
                }
                // Hoặc kiểm tra dòng kế tiếp
                if (i + 1 < lines.size) {
                    val nextDigits = lines[i + 1].replace(Regex("[^0-9]"), "")
                    if (nextDigits.length == 12) {
                        cccdNumber = nextDigits
                        break
                    }
                }
            }
        }

        // Fallback số 12 số bất kỳ nếu chưa tìm thấy
        if (cccdNumber == null) {
            val match = Regex("""\b(\d{12})\b""").find(rawText.replace(" ", ""))
            if (match != null) {
                cccdNumber = match.value
            } else {
                // Thử tìm regex có thể có khoảng trắng ở giữa: 079 095 012345
                val spaceMatch = Regex("""\b0\d{2}[\s.-]?\d{3}[\s.-]?\d{6}\b""").find(rawText)
                if (spaceMatch != null) {
                    cccdNumber = spaceMatch.value.replace(Regex("""[\s.-]"""), "")
                }
            }
        }

        // 2. Tìm Họ và tên
        for (i in lines.indices) {
            val line = lines[i]
            val isNameLabel = line.contains("Họ và tên", true) ||
                    line.contains("Họ tên", true) ||
                    line.contains("Họ, chữ đệm", true) ||
                    line.contains("Full name", true)

            if (isNameLabel) {
                // Trường hợp tên nằm sau dấu hai chấm cùng dòng: "Họ và tên / Full name: NGUYỄN VĂN A"
                val colonIdx = line.indexOf(":")
                if (colonIdx >= 0 && colonIdx < line.length - 1) {
                    val afterColon = line.substring(colonIdx + 1).trim()
                    val cleanCandidate = cleanNameCandidate(afterColon)
                    if (isValidVietnameseName(cleanCandidate)) {
                        fullName = cleanCandidate
                        break
                    }
                }

                // Trường hợp tên nằm ở dòng tiếp theo ngay bên dưới (phổ biến nhất trên CCCD gắn chip)
                if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1].trim()
                    val cleanCandidate = cleanNameCandidate(nextLine)
                    if (isValidVietnameseName(cleanCandidate)) {
                        fullName = cleanCandidate
                        break
                    }
                }
            }
        }

        // Fallback Họ và tên: Quét các dòng chữ IN HOA không chứa số/từ cấm
        if (fullName == null) {
            for (line in lines) {
                val clean = cleanNameCandidate(line)
                if (isValidVietnameseName(clean)) {
                    fullName = clean
                    break
                }
            }
        }

        // 3. Tìm Ngày sinh
        val dobRegex = Regex("""\b(\d{1,2}[/-]\d{1,2}[/-]\d{4})\b""")
        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains("sinh", true) || line.contains("birth", true)) {
                val match = dobRegex.find(line)
                if (match != null) {
                    dateOfBirth = match.value.replace("-", "/")
                    break
                }
                if (i + 1 < lines.size) {
                    val nextMatch = dobRegex.find(lines[i + 1])
                    if (nextMatch != null) {
                        dateOfBirth = nextMatch.value.replace("-", "/")
                        break
                    }
                }
            }
        }
        if (dateOfBirth == null) {
            dateOfBirth = dobRegex.find(rawText)?.value?.replace("-", "/")
        }

        // 4. Tìm Giới tính
        for (line in lines) {
            if (line.contains("Giới tính", true) || line.contains("Sex", true)) {
                when {
                    line.contains("Nam", true) || line.contains("Male", true) -> gender = "Nam"
                    line.contains("Nữ", true) || line.contains("Female", true) -> gender = "Nữ"
                }
                break
            }
        }

        // 5. Tìm Quận/Huyện và Địa chỉ nơi thường trú / nơi cư trú
        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains("thường trú", true) ||
                line.contains("cư trú", true) ||
                line.contains("residence", true) ||
                line.contains("Quê quán", true) ||
                line.contains("origin", true)
            ) {
                val candidateAddress = buildString {
                    val colonIdx = line.indexOf(":")
                    if (colonIdx >= 0 && colonIdx < line.length - 1) {
                        append(line.substring(colonIdx + 1).trim()).append(" ")
                    }
                    var k = i + 1
                    while (k < lines.size && k <= i + 3) {
                        val nextL = lines[k]
                        if (nextL.contains("giá trị", true) || nextL.contains("expiry", true)) break
                        append(nextL).append(" ")
                        k++
                    }
                }.trim()

                if (candidateAddress.isNotBlank()) {
                    address = candidateAddress
                    break
                }
            }
        }

        // Trích xuất Quận/Huyện TP.HCM nếu xuất hiện trong address hoặc rawText
        val searchText = (address ?: rawText)
        for (d in DISTRICTS_HCMC) {
            if (searchText.contains(d, ignoreCase = true)) {
                district = d
                break
            }
        }

        return CccdScanResult(
            cccdNumber = cccdNumber,
            fullName = fullName,
            dateOfBirth = dateOfBirth,
            gender = gender,
            address = address,
            district = district,
            rawText = rawText
        )
    }

    private fun cleanNameCandidate(text: String): String {
        return text.replace(Regex("""[^a-zA-ZÀ-ỹ\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isValidVietnameseName(candidate: String): Boolean {
        if (candidate.length < 5 || candidate.length > 40) return false
        val words = candidate.split(" ").filter { it.isNotBlank() }
        if (words.size < 2 || words.size > 7) return false

        val unaccented = removeAccents(candidate).uppercase()
        for (kw in IGNORED_NAME_KEYWORDS) {
            if (unaccented.contains(kw)) return false
        }

        // Tên CCCD phải là chữ in hoa toàn bộ hoặc in hoa từng từ
        val isAllUpper = candidate.all { !it.isLetter() || it.isUpperCase() }
        val isTitleCase = words.all { it.first().isUpperCase() }
        return isAllUpper || isTitleCase
    }

    /**
     * Bỏ dấu tiếng Việt (chuẩn hóa ASCII) phục vụ in hoa không dấu cho Tên tài khoản ngân hàng
     */
    fun removeAccents(str: String): String {
        val nfd = Normalizer.normalize(str, Normalizer.Form.NFD)
        val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
        return pattern.matcher(nfd)
            .replaceAll("")
            .replace("Đ", "D")
            .replace("đ", "d")
            .trim()
    }

    /**
     * Chuẩn hóa Họ tên theo dạng Title Case (Nguyễn Văn A)
     */
    fun toTitleCase(str: String): String {
        return str.lowercase().split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }.trim()
    }

    /**
     * Đọc Bitmap xoay đúng chiều EXIF từ Uri
     */
    private fun decodeRotatedBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val contentResolver = context.contentResolver
        val rawBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (rawBytes.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val origW = bounds.outWidth
        val origH = bounds.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        var halfW = origW
        var halfH = origH
        while (halfW > maxDimension * 2 || halfH > maxDimension * 2) {
            sampleSize *= 2
            halfW /= 2
            halfH /= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions) ?: return null

        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            ByteArrayInputStream(rawBytes).use { exifInput ->
                val exif = ExifInterface(exifInput)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (_: Exception) {}

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        }

        val currentW = bitmap.width
        val currentH = bitmap.height
        val maxCurr = maxOf(currentW, currentH).toFloat()
        val scale = if (maxCurr > maxDimension) maxDimension.toFloat() / maxCurr else 1f
        if (scale < 1f) {
            matrix.postScale(scale, scale)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, currentW, currentH, matrix, true)
    }
}
