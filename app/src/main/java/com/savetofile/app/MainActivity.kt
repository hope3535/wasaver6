package com.savetofile.app

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private var sharedContent: Intent? = null
    private var sharedUri: Uri? = null

    companion object {
        private const val TAG = "SaveToFile"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "App started")
        
        sharedContent = intent
        intent?.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedContent = intent
        intent?.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                Log.d(TAG, "ACTION_SEND")
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrEmpty()) {
                        saveTextDirectly(text)
                    } else {
                        sharedUri = getSharedUri(intent)
                        sharedUri?.let { saveFileDirectly(it) }
                            ?: finish()
                    }
                } else {
                    sharedUri = getSharedUri(intent)
                    sharedUri?.let { saveFileDirectly(it) }
                        ?: finish()
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                uris?.firstOrNull()?.let { saveFileDirectly(it) }
            }
            else -> finish()
        }
    }

    private fun getSharedUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun saveTextDirectly(text: String) {
        try {
            val fileName = "SaveTo_${System.currentTimeMillis()}.txt"
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    contentResolver.openOutputStream(it)?.buffered()?.use { os ->
                        os.write(text.toByteArray())
                    }
                    
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(it, values, null, null)
                    
                    Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(dir, fileName).outputStream().buffered().use { os ->
                    os.write(text.toByteArray())
                }
                Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun saveFileDirectly(sourceUri: Uri) {
        try {
            val fileName = "SaveTo_${System.currentTimeMillis()}"
            val mimeType = contentResolver.getType(sourceUri) ?: "application/octet-stream"
            val ext = getExtension(mimeType)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.$ext")
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                
                val destUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                destUri?.let { dest ->
                    contentResolver.openInputStream(sourceUri)?.use { input ->
                        contentResolver.openOutputStream(dest)?.buffered()?.use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(dest, values, null, null)
                    
                    Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
                }
            } else {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(dir, "$fileName.$ext")
                
                contentResolver.openInputStream(sourceUri)?.use { input ->
                    file.outputStream().buffered().use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                        }
                    }
                }
                Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
        }
        finish()
    }
    
    private fun getExtension(mimeType: String): String {
        return when {
            mimeType.contains("pdf") -> "pdf"
            mimeType.contains("png") -> "png"
            mimeType.contains("jpg") || mimeType.contains("jpeg") -> "jpg"
            mimeType.contains("gif") -> "gif"
            mimeType.contains("zip") -> "zip"
            mimeType.contains("xml") -> "xml"
            mimeType.contains("json") -> "json"
            mimeType.contains("text") -> "txt"
            else -> "bin"
        }
    }
}