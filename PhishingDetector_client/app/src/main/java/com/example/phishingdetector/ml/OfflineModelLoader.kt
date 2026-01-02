package com.example.phishingdetector.ml

import android.app.Application
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import java.io.File
import java.io.FileOutputStream

object OfflineModelLoader {

    fun load(application: Application): Module {
        return LiteModuleLoader.load(assetFilePath(application, "offline_model.pt"))
    }

    private fun assetFilePath(application: Application, assetName: String): String {
        val file = File(application.filesDir, assetName)
        if (!file.exists()) {
            application.assets.open(assetName).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (true) {
                        read = inputStream.read(buffer)
                        if (read == -1) break
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            }
        }
        return file.absolutePath
    }
}
