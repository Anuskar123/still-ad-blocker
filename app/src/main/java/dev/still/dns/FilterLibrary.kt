package dev.still.dns

import android.content.Context
import java.io.File

object FilterLibrary {
    @Volatile private var instance: FilterRepository? = null
    fun get(context: Context): FilterRepository = instance ?: synchronized(this) {
        instance ?: FilterRepository(File(context.applicationContext.filesDir, "dns-filters")).also { instance = it }
    }
}
