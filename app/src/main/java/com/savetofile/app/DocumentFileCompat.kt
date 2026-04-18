package com.savetofile.app

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

object DocumentFileCompat {
    fun fromTreeUri(context: Context, treeUri: Uri): DocumentFile? {
        return DocumentFile.fromTreeUri(context, treeUri)
    }
}