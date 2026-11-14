package app.xvpn

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlin.concurrent.thread

/** Portrait QR scanner (the stock zxing CaptureActivity is locked to landscape). Returns the scanned text. */
class ScanContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit) = Intent(context, ScanActivity::class.java)
    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(ScanActivity.EXTRA_TEXT) else null
}

class ScanActivity : ComponentActivity() {
    companion object { const val EXTRA_TEXT = "text" }

    private val bar = Color(0xFF0A0A0D)
    private val idleBg = Color(0xFF151518)
    private val orange = Color(0xFFFF6B1A)
    private val orangeFrame = Color(0xFFFF8A3D)
    private val muted = Color(0xFFA3A1AE)

    private fun finishWith(text: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(0xFF0A0A0D.toInt()),
        )
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF0A0A0D.toInt()))
        setContent { ScanScreen() }
    }

    @Composable
    private fun ScanScreen() {
        val ctx = LocalContext.current
        var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
        var denied by remember { mutableStateOf(false) }
        var scanning by remember { mutableStateOf(true) }
        var torch by remember { mutableStateOf(false) }

        val permission = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it; denied = !it; if (it) scanning = true
        }
        val gallery = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) thread {
                val text = decodeImage(uri)
                runOnUiThread { if (text != null) finishWith(text) else Toast.makeText(ctx, "No QR code found in image", Toast.LENGTH_SHORT).show() }
            }
        }
        androidx.compose.runtime.LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

        val active = scanning && granted
        Column(Modifier.fillMaxSize().background(bar)) {
            // header
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back") { finish() }
                Text(
                    "Import from QR code", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                if (active) {
                    BarIcon(Icons.Filled.Stop, "Stop") { scanning = false; torch = false }
                    BarIcon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "Flash") { torch = !torch }
                } else {
                    BarIcon(Icons.Outlined.QrCodeScanner, "Scan") { startScan(granted, { permission.launch(it) }) { scanning = true } }
                }
                BarIcon(Icons.Outlined.Image, "Pick from gallery") { gallery.launch("image/*") }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (active) {
                    CameraArea(torch)
                } else {
                    Column(
                        Modifier.fillMaxSize().background(idleBg).clickable { startScan(granted, { permission.launch(it) }) { scanning = true } },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, null, tint = orange, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Scan QR code", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (denied) "Camera permission is needed. Tap to allow." else "Tap the screen to open the camera and scan",
                            color = muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    private fun startScan(granted: Boolean, request: (String) -> Unit, start: () -> Unit) {
        if (granted) start() else request(Manifest.permission.CAMERA)
    }

    @Composable
    private fun BarIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }

    @Composable
    private fun CameraArea(torch: Boolean) {
        val ctx = LocalContext.current
        val view = remember {
            BarcodeView(ctx).apply {
                decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            }
        }
        DisposableEffect(view) {
            val callback = object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult?) { result?.text?.let { finishWith(it) } }
            }
            view.decodeSingle(callback)
            val observer = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) view.resume() else if (e == Lifecycle.Event.ON_PAUSE) view.pause()
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume()
            onDispose { lifecycle.removeObserver(observer); view.pause() }
        }
        androidx.compose.runtime.LaunchedEffect(torch) { runCatching { view.setTorch(torch) } }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val labelTop = (maxHeight + maxWidth * 0.69f) / 2 + 16.dp
            AndroidView({ view }, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val side = size.width * 0.69f
                val left = (size.width - side) / 2f
                val top = (size.height - side) / 2f
                val scrim = Color(0x73000000)
                // dim everything outside the square
                drawRect(scrim, Offset.Zero, Size(size.width, top))
                drawRect(scrim, Offset(0f, top + side), Size(size.width, size.height - top - side))
                drawRect(scrim, Offset(0f, top), Size(left, side))
                drawRect(scrim, Offset(left + side, top), Size(left, side))
                // orange corner brackets
                val len = 24.dp.toPx(); val w = 3.dp.toPx(); val h = w / 2
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(orangeFrame, Offset(x - dx * h, y), Offset(x + dx * len, y), w)
                    drawLine(orangeFrame, Offset(x, y - dy * h), Offset(x, y + dy * len), w)
                }
                corner(left, top, 1f, 1f); corner(left + side, top, -1f, 1f)
                corner(left, top + side, 1f, -1f); corner(left + side, top + side, -1f, -1f)
            }
            Text(
                "Scan QR code", color = Color.White, fontSize = 20.sp,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = labelTop),
            )
        }
    }

    /** Decodes a QR code from a gallery image; null if none is found. */
    private fun decodeImage(uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > 1800 || bounds.outHeight / sample > 1800) sample *= 2
        val bmp: Bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val src = RGBLuminanceSource(bmp.width, bmp.height, px)
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true))
        }
        val attempts = listOf(
            BinaryBitmap(HybridBinarizer(src)),
            BinaryBitmap(GlobalHistogramBinarizer(src)),
            BinaryBitmap(HybridBinarizer(src.invert())),
        )
        attempts.firstNotNullOfOrNull { runCatching { reader.decodeWithState(it).text }.getOrNull() }
    }.getOrNull()
}
