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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.xvpn.ui.C
import app.xvpn.ui.DesignScale
import app.xvpn.ui.HeaderIcon
import app.xvpn.ui.HeaderRow
import app.xvpn.ui.Radius
import app.xvpn.ui.setUpEdgeToEdge
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

class ScanContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit) = Intent(context, ScanActivity::class.java)
    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(ScanActivity.EXTRA_TEXT) else null
}

class ScanActivity : ComponentActivity() {
    companion object { const val EXTRA_TEXT = "text" }

    private fun finishWith(text: String) {
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setUpEdgeToEdge(navigationBarColor = C.Bg.toArgb())
        setContent { DesignScale { ScanScreen() } }
    }

    @Composable
    private fun ScanScreen() {
        val ctx = LocalContext.current
        var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
        var denied by remember { mutableStateOf(false) }
        var scanning by remember { mutableStateOf(true) }
        var torch by remember { mutableStateOf(false) }

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it; denied = !it; if (it) scanning = true
        }
        val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) thread {
                val text = decodeImage(uri)
                runOnUiThread { if (text != null) finishWith(text) else Toast.makeText(ctx, "No QR code found in image", Toast.LENGTH_SHORT).show() }
            }
        }
        LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }

        val active = scanning && granted
        val start: () -> Unit = { if (granted) { scanning = true } else { permission.launch(Manifest.permission.CAMERA) } }

        Column(Modifier.fillMaxSize().background(C.Bg)) {
            Column(Modifier.fillMaxWidth().statusBarsPadding()) {
                Spacer(Modifier.height(20.dp))
                HeaderRow {
                    HeaderIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back") { finish() }
                    Spacer(Modifier.width(6.dp))
                    Text("Scan QR code", color = C.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                    if (active) HeaderIcon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, "Flash", tint = if (torch) C.Accent else C.Text) { torch = !torch }
                    HeaderIcon(Icons.Filled.Image, "Pick from gallery") { gallery.launch("image/*") }
                }
                Spacer(Modifier.height(18.dp))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (active) {
                    CameraArea(torch)
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)) {
                        PillButton("Stop", false) { scanning = false; torch = false }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().clickable { start() },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, null, tint = C.Accent, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Scan a QR code", color = C.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (denied) "Camera permission is needed to scan." else "Tap to open the camera and scan",
                            color = C.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                        PillButton(if (denied) "Allow camera" else "Start scanning", true) { start() }
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    @Composable
    private fun PillButton(label: String, primary: Boolean, onClick: () -> Unit) {
        Box(
            Modifier.height(44.dp).clip(RoundedCornerShape(Radius.Small))
                .background(if (primary) C.Button else C.ButtonGrey)
                .clickable(onClick = onClick).padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) { Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
    }

    @Composable
    private fun CameraArea(torch: Boolean) {
        val ctx = LocalContext.current
        val view = remember {
            BarcodeView(ctx).apply { decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE)) }
        }
        DisposableEffect(view) {
            view.decodeSingle(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult?) { result?.text?.let { finishWith(it) } }
            })
            val observer = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) view.resume() else if (e == Lifecycle.Event.ON_PAUSE) view.pause()
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume()
            onDispose { lifecycle.removeObserver(observer); view.pause() }
        }
        LaunchedEffect(torch) { runCatching { view.setTorch(torch) } }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = maxWidth * 0.69f
            val labelTop = (maxHeight + side) / 2 + 16.dp
            AndroidView({ view }, Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val sd = side.toPx()
                val l = (size.width - sd) / 2f; val t = (size.height - sd) / 2f
                val rt = l + sd; val b = t + sd
                val r = Radius.Card.toPx(); val len = 36.dp.toPx(); val w = 4.dp.toPx()

                val scrim = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(Rect(0f, 0f, size.width, size.height))
                    addRoundRect(RoundRect(Rect(l, t, rt, b), CornerRadius(r)))
                }
                drawPath(scrim, C.Bg.copy(alpha = 0.6f))

                val corners = Path().apply {
                    moveTo(l, t + len); lineTo(l, t + r); arcTo(Rect(l, t, l + 2 * r, t + 2 * r), 180f, 90f, false); lineTo(l + len, t)
                    moveTo(rt - len, t); lineTo(rt - r, t); arcTo(Rect(rt - 2 * r, t, rt, t + 2 * r), 270f, 90f, false); lineTo(rt, t + len)
                    moveTo(rt, b - len); lineTo(rt, b - r); arcTo(Rect(rt - 2 * r, b - 2 * r, rt, b), 0f, 90f, false); lineTo(rt - len, b)
                    moveTo(l + len, b); lineTo(l + r, b); arcTo(Rect(l, b - 2 * r, l + 2 * r, b), 90f, 90f, false); lineTo(l, b - len)
                }
                drawPath(corners, C.Accent, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Text(
                "Align the QR code within the frame", color = C.Muted, fontSize = 14.sp,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = labelTop),
            )
        }
    }

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
