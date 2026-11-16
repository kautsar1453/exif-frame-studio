package id.exifstudio.app

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = WebViewClient()
            addJavascriptInterface(AndroidBridge(), "AndroidBridge")
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    vw: WebView?,
                    cb: ValueCallback<Array<Uri>>?,
                    params: FileChooserParams?
                ): Boolean {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = cb
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }
                    startActivityForResult(Intent.createChooser(intent, "Pilih Foto"), 1001)
                    return true
                }
            }
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1001) {
            val results = mutableListOf<Uri>()
            if (resultCode == RESULT_OK && data != null) {
                val clip = data.clipData
                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i).uri?.let { results.add(it) }
                    }
                } else {
                    data.data?.let { results.add(it) }
                }
            }
            fileUploadCallback?.onReceiveValue(if (results.isNotEmpty()) results.toTypedArray() else null)
            fileUploadCallback = null
        }
    }

    inner class AndroidBridge {
        @JavascriptInterface
        fun saveBase64Image(dataUrl: String, fileName: String, mimeType: String) {
            try {
                val base64Part = dataUrl.substringAfter(",")
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EXIFStudio")
                    }
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) }
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Tersimpan di Galeri: Pictures/EXIFStudio/$fileName", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Gagal menyimpan: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
