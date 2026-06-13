package app.pact.android.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Encodes [content] into a QR Bitmap with zxing. White modules on transparent. */
fun encodeQrBitmap(
    content: String,
    sizePx: Int = 720,
    foreground: Int = 0xFF14151C.toInt(),
    background: Int = 0xFFFFFFFF.toInt(),
): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val w = matrix.width
    val h = matrix.height
    val bmp = createBitmap(w, h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            bmp[x, y] = if (matrix[x, y]) foreground else background
        }
    }
    return bmp
}

@Composable
fun QrImage(
    content: String,
    modifier: Modifier = Modifier,
    sizePx: Int = 720,
) {
    val bitmap = remember(content, sizePx) { encodeQrBitmap(content, sizePx) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR code to join this pact",
        modifier = modifier.semantics { contentDescription = "QR code to join this pact" },
        contentScale = ContentScale.FillBounds,
    )
}
