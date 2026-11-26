package com.mando.app

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

/** Mando: interfaz HTML (assets/index.html) + emisor infrarrojo del móvil. Sin red ni más permisos. */
class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private val prefs by lazy { getSharedPreferences("mando", Context.MODE_PRIVATE) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.addJavascriptInterface(Bridge(), "Rumbo")
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun ir(): ConsumerIrManager? =
        getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

    inner class Bridge {

        @JavascriptInterface
        fun irInfo(): String {
            val o = JSONObject()
            val m = ir()
            val has = m?.hasIrEmitter() == true
            o.put("has", has)
            val arr = JSONArray()
            if (has) {
                val rs = m?.carrierFrequencies
                if (rs != null) for (r in rs) arr.put(JSONArray().put(r.minFrequency).put(r.maxFrequency))
            }
            o.put("ranges", arr)
            return o.toString()
        }

        /** Frecuencia en Hz y duraciones en µs separadas por comas. */
        @JavascriptInterface
        fun irSend(freq: Int, pattern: String): String {
            val m = ir() ?: return "no"
            if (!m.hasIrEmitter()) return "no"
            val rs = m.carrierFrequencies
            if (rs != null && rs.isNotEmpty() && rs.none { freq >= it.minFrequency && freq <= it.maxFrequency }) return "freq"
            val p = pattern.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
            if (p.isEmpty()) return "bad"
            return try { m.transmit(freq, p); "ok" } catch (e: Exception) { "err" }
        }

        @JavascriptInterface
        fun save(k: String, v: String) { prefs.edit().putString(k, v).apply() }

        @JavascriptInterface
        fun load(k: String): String = prefs.getString(k, "") ?: ""
    }
}
