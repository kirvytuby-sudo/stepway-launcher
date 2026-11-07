package com.kirvy.stepwaylauncher

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.LocationManager
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true // para mostrar la foto de fondo guardada
        web.setBackgroundColor(0xFF061456.toInt())
        web.webViewClient = WebViewClient()
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Bridge(this, this), "Android")
        web.loadUrl("file:///android_asset/launcher/index.html")
        setContentView(web)
        ocultarBarras()

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        }
    }

    private fun ocultarBarras() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onResume() { super.onResume(); ocultarBarras() }

    // Un launcher no se cierra con "atrás": solo cierra el cajón de apps.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { web.evaluateJavascript("window.onBack && window.onBack()", null) }

    // Al presionar HOME estando ya en el launcher, cerrar el cajón.
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (::web.isInitialized) web.evaluateJavascript("window.onBack && window.onBack()", null)
    }

    // ===== Foto de fondo elegida por el usuario =====
    fun elegirImagen(tipo: String) {
        val req = if (tipo == "carro") REQ_CARRO else REQ_FONDO
        val i = Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
        try { startActivityForResult(Intent.createChooser(i, "Elige una imagen"), req) } catch (_: Exception) {}
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode != REQ_FONDO && requestCode != REQ_CARRO) || resultCode != RESULT_OK) return
        val tipo = if (requestCode == REQ_CARRO) "carro" else "fondo"
        val uri = data?.data ?: return
        try {
            val destino = java.io.File(filesDir, "$tipo.img")
            contentResolver.openInputStream(uri)?.use { inp -> destino.outputStream().use { inp.copyTo(it) } }
            val url = "file://" + destino.absolutePath
            val fn = if (tipo == "carro") "onCarro" else "onFondo"
            web.evaluateJavascript("window.$fn && window.$fn('$url')", null)
        } catch (_: Exception) {}
    }

    companion object { const val REQ_FONDO = 42; const val REQ_CARRO = 43 }

    class Bridge(private val ctx: Context, private val act: MainActivity) {

        @JavascriptInterface
        fun pickImage(tipo: String) { act.runOnUiThread { act.elegirImagen(tipo) } }

        @JavascriptInterface
        fun launch(pkg: String): Boolean {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return try { ctx.startActivity(i); true } catch (e: Exception) { false }
        }

        @JavascriptInterface
        fun launchFirst(json: String): Boolean {
            val arr = JSONArray(json)
            for (k in 0 until arr.length()) if (launch(arr.getString(k))) return true
            return false
        }

        @JavascriptInterface
        fun openDialer() {
            try { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun openSettings() {
            try { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun media(cmd: String) {
            val code = when (cmd) {
                "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            }
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }

        @SuppressLint("MissingPermission")
        @JavascriptInterface
        fun getLocation(): String {
            if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return "{}"
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            for (p in lm.getProviders(true)) {
                val l = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } ?: continue
                return JSONObject().put("lat", l.latitude).put("lon", l.longitude).toString()
            }
            return "{}"
        }

        @JavascriptInterface
        fun listApps(): String {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != ctx.packageName }
                .sortedBy { it.loadLabel(pm).toString().lowercase() }
            val out = JSONArray()
            for (r in apps) {
                val o = JSONObject()
                o.put("name", r.loadLabel(pm).toString())
                o.put("pkg", r.activityInfo.packageName)
                o.put("icon", iconoBase64(r.loadIcon(pm)))
                out.put(o)
            }
            return out.toString()
        }

        private fun iconoBase64(d: android.graphics.drawable.Drawable): String = try {
            val size = 96
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, size, size); d.draw(c)
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) { "" }
    }
}
