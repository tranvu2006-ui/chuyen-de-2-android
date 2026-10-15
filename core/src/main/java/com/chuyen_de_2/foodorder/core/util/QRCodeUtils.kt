package com.chuyen_de_2.foodorder.core.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color

object QRCodeUtils {
    /**
     * Tạo Bitmap QR Code giả lập ma trận 2D sắc nét dựa trên chuỗi text đầu vào (ví dụ: TOKEN_OISHI_88291).
     * Đảm bảo tính nhất quán (deterministic) và hiển thị hoàn hảo trên các thiết bị Android.
     */
    fun generateQRCodeBitmap(text: String, width: Int = 400, height: Int = 400): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }

        // Kích thước ma trận (25x25)
        val modules = 25
        val moduleWidth = width.toFloat() / modules
        val moduleHeight = height.toFloat() / modules

        val hash = text.hashCode()

        // Vẽ 3 góc định vị (Finder Patterns) chuẩn QR Code ở 3 góc (Top-Left, Top-Right, Bottom-Left)
        drawFinderPattern(canvas, paint, 0, 0, moduleWidth, moduleHeight)
        drawFinderPattern(canvas, paint, modules - 7, 0, moduleWidth, moduleHeight)
        drawFinderPattern(canvas, paint, 0, modules - 7, moduleWidth, moduleHeight)

        // Vẽ các module dữ liệu dựa trên hash của token
        for (x in 0 until modules) {
            for (y in 0 until modules) {
                // Bỏ qua vùng 3 góc định vị
                if ((x < 8 && y < 8) || (x >= modules - 7 && y < 8) || (x < 8 && y >= modules - 7)) {
                    continue
                }

                val seed = (x * 31 + y * 17 + hash)
                if (seed % 3 == 0 || seed % 7 == 0) {
                    canvas.drawRect(
                        x * moduleWidth,
                        y * moduleHeight,
                        (x + 1) * moduleWidth,
                        (y + 1) * moduleHeight,
                        paint
                    )
                }
            }
        }

        return bitmap
    }

    private fun drawFinderPattern(canvas: Canvas, paint: Paint, startX: Int, startY: Int, mw: Float, mh: Float) {
        for (x in 0..6) {
            for (y in 0..6) {
                val isOuterBorder = x == 0 || x == 6 || y == 0 || y == 6
                val isInnerBox = x in 2..4 && y in 2..4
                if (isOuterBorder || isInnerBox) {
                    canvas.drawRect(
                        (startX + x) * mw,
                        (startY + y) * mh,
                        (startX + x + 1) * mw,
                        (startY + y + 1) * mh,
                        paint
                    )
                }
            }
        }
    }
}
