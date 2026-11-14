package com.tuapp.fintrack.ui.common

import android.content.Context
import java.io.File

/**
 * Returns an empty `cache/exports` directory. Previous exports are deleted first so shared files
 * do not pile up in the cache. Served by the FileProvider `cache-path` entry.
 */
fun freshExportDir(context: Context): File {
    val dir = File(context.cacheDir, "exports")
    dir.deleteRecursively()
    dir.mkdirs()
    return dir
}
