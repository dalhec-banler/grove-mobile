package io.nativeplanet.grove.ui.upload

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.nativeplanet.grove.GroveApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (intent?.action) {
            Intent.ACTION_SEND -> handleSingleFile()
            Intent.ACTION_SEND_MULTIPLE -> handleMultipleFiles()
            else -> finish()
        }
    }

    private fun handleSingleFile() {
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        if (uri == null) {
            finish()
            return
        }

        val sourceApp = getSourceAppName()
        uploadFile(uri, sourceApp)
    }

    private fun handleMultipleFiles() {
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        if (uris.isNullOrEmpty()) {
            finish()
            return
        }

        val sourceApp = getSourceAppName()
        uris.forEach { uri ->
            uploadFile(uri, sourceApp)
        }
    }

    private fun getSourceAppName(): String {
        val referrer = referrer?.host
            ?: intent.getStringExtra(Intent.EXTRA_REFERRER)
            ?: callingPackage

        return referrer?.let { pkg ->
            try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
                    .lowercase()
                    .replace(" ", "-")
            } catch (e: Exception) {
                pkg.substringAfterLast('.')
            }
        } ?: "shared"
    }

    private fun uploadFile(uri: Uri, sourceApp: String) {
        lifecycleScope.launch {
            try {
                val fileName = getFileName(uri)
                val tempFile = copyToTempFile(uri, fileName)

                if (tempFile != null) {
                    val success = GroveApp.instance.repository.uploadFile(
                        localPath = tempFile.absolutePath,
                        name = fileName,
                        tags = listOf(sourceApp),
                        sourceApp = sourceApp
                    )

                    withContext(Dispatchers.Main) {
                        if (success) {
                            Toast.makeText(
                                this@ShareReceiverActivity,
                                "Uploaded to Grove",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this@ShareReceiverActivity,
                                "Queued for upload",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ShareReceiverActivity,
                        "Upload failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                finish()
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "unnamed"

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }

        if (name == "unnamed") {
            name = uri.lastPathSegment ?: "unnamed"
        }

        return name
    }

    private suspend fun copyToTempFile(uri: Uri, fileName: String): File? = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(cacheDir, "uploads").also { it.mkdirs() }
            val tempFile = File(tempDir, "${System.currentTimeMillis()}_$fileName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            tempFile
        } catch (e: Exception) {
            null
        }
    }
}
