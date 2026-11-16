package id.exifstudio.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var uploadMessage: ValueCallback<Array<Uri>>? = null
    private val FILE_CHOOSER_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            webViewClient = WebViewClient()

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    uploadMessage?.onReceiveValue(null)
                    uploadMessage = filePathCallback

                    // 1. Targetkan Galeri Lokal HP (MediaStore Internal)
                    val localGalleryIntent = Intent(
                        Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    ).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    }

                    // 2. Opsi Penyimpanan Internal HP (Non-Cloud)
                    val internalStorageIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val primaryRoot = DocumentsContract.buildRootUri(
                                "com.android.externalstorage.documents",
                                "primary"
                            )
                            putExtra(DocumentsContract.EXTRA_INITIAL_URI, primaryRoot)
                        }
                    }

                    // 3. Cari aplikasi Galeri Bawaan HP dan kecualikan Google Photos
                    val localGalleries = packageManager.queryIntentActivities(localGalleryIntent, 0)
                        .filter { !it.activityInfo.packageName.contains("com.google.android.apps.photos") }

                    val primaryIntent = if (localGalleries.isNotEmpty()) {
                        val oemGallery = localGalleries[0].activityInfo
                        Intent(localGalleryIntent).apply {
                            component = ComponentName(oemGallery.packageName, oemGallery.name)
                        }
                    } else {
                        internalStorageIntent
                    }

                    val chooser = Intent.createChooser(primaryIntent, "Pilih Foto dari Galeri HP").apply {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(internalStorageIntent))
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            putExtra(
                                Intent.EXTRA_EXCLUDE_COMPONENTS,
                                arrayOf(
                                    ComponentName(
                                        "com.google.android.apps.photos",
                                        "com.google.android.apps.photos.picker.external.ExternalPickerActivity"
                                    ),
                                    ComponentName(
                                        "com.google.android.apps.photos",
                                        "com.google.android.apps.photos.home.HomeActivity"
                                    )
                                )
                            )
                        }
                    }

                    return try {
                        startActivityForResult(chooser, FILE_CHOOSER_REQUEST_CODE)
                        true
                    } catch (e: Exception) {
                        uploadMessage = null
                        false
                    }
                }
            }
        }

        setContentView(webView)
        webView.loadUrl("file:///android_asset/index.html")
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val callback = uploadMessage
            uploadMessage = null
            if (callback == null) return

            if (resultCode == RESULT_OK && data != null) {
                val uris = mutableListOf<Uri>()
                val clipData = data.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uris.add(it) }
                    }
                } else {
                    data.data?.let { uris.add(it) }
                }
                callback.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
            } else {
                callback.onReceiveValue(null)
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
