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
        Log.d(TAG, "Intent: ${intent?.action}, Type: ${intent?.type}")
        
        sharedContent = intent
        if (intent != null) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
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
                Log.d(TAG, "ACTION_SEND received")
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!text.isNullOrEmpty()) {
                        Log.d(TAG, "Text content: ${text.take(50)}...")
                        saveTextDirectly(text)
                    } else {
                        sharedUri = getSharedUri(intent)
                        if (sharedUri != null) {
                            Log.d(TAG, "File URI: $sharedUri")
                            saveFileDirectly(sharedUri!!)
                        } else {
                            Log.e(TAG, "No content found")
                            Toast.makeText(this, R.string.no_content, Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                } else {
                    sharedUri = getSharedUri(intent)
                    if (sharedUri != null) {
                        Log.d(TAG, "File URI: $sharedUri")
                        saveFileDirectly(sharedUri!!)
                    } else {
                        Log.e(TAG, "No URI found")
                        Toast.makeText(this, R.string.no_content, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                Log.d(TAG, "ACTION_SEND_MULTIPLE received")
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                uris?.firstOrNull()?.let {
                    Log.d(TAG, "First file URI: $it")
                    saveFileDirectly(it)
                }
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
                finish()
            }
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
            Log.d(TAG, "Saving text to: $fileName")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(text.toByteArray())
                    }
                    
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, contentValues, null, null)
                    
                    Log.d(TAG, "Text saved to Downloads via MediaStore")
                    Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "Failed to create MediaStore entry")
                    Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(text.toByteArray())
                }
                Log.d(TAG, "Text saved to Downloads (legacy)")
                Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
            }
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving text: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveFileDirectly(sourceUri: Uri) {
        try {
            val fileName = "SaveTo_${System.currentTimeMillis()}"
            val mimeType = contentResolver.getType(sourceUri) ?: "application/octet-stream"
            val extension = when {
                mimeType.contains("pdf") -> "pdf"
                mimeType.contains("png") -> "png"
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                mimeType.contains("gif") -> "gif"
                mimeType.contains("zip") -> "zip"
                mimeType.contains("text") -> "txt"
                else -> "bin"
            }
            
            Log.d(TAG, "Saving file: $fileName.$extension, mime: $mimeType")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "$fileName.$extension")
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                
                val destUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (destUri != null) {
                    try {
                        contentResolver.openInputStream(sourceUri)?.use { input ->
                            contentResolver.openOutputStream(destUri)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Permission error: ${e.message}")
                        Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
                        finish()
                        return
                    }
                    
                    contentValues.clear()
                    contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(destUri, contentValues, null, null)
                    
                    Log.d(TAG, "File saved to Downloads via MediaStore")
                    Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
                } else {
                    Log.e(TAG, "Failed to create MediaStore entry")
                    Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
            } else {
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, "$fileName.$extension")
                
                try {
                    if (sourceUri.scheme == "content") {
                        contentResolver.openInputStream(sourceUri)?.use { input ->
                            FileOutputStream(file).use { output ->
                                input.copyTo(output)
                            }
                        }
                    } else {
                        sourceUri.path?.let { path ->
                            File(path).inputStream().use { input ->
                                FileOutputStream(file).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                    Log.d(TAG, "File saved to Downloads (legacy)")
                    Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show()
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission error: ${e.message}")
                    Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
                }
            }
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving file: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, R.string.save_failed, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}