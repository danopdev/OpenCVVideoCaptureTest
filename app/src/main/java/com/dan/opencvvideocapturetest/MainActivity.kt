package com.dan.opencvvideocapturetest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.dan.opencvvideocapturetest.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader
import org.opencv.calib3d.Calib3d.estimateAffinePartial2D
import org.opencv.core.*
import org.opencv.imgproc.Imgproc.*
import org.opencv.utils.Converters
import org.opencv.video.Video.calcOpticalFlowPyrLK
import org.opencv.videoio.VideoCapture
import org.opencv.videoio.VideoWriter
import org.opencv.videoio.Videoio.*
import java.io.File
import java.io.FileNotFoundException


class MainActivity : AppCompatActivity() {
    companion object {
        val PERMISSIONS = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
        )

        const val REQUEST_PERMISSIONS = 1
        const val INTENT_OPEN_VIDEO = 2
    }

    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!askPermissions()) onPermissionsAllowed()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            REQUEST_PERMISSIONS -> handleRequestPermissions(grantResults)
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun exitApp() {
        setResult(0)
        finish()
    }

    private fun fatalError(msg: String) {
        AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_name))
                .setMessage(msg)
                .setIcon(android.R.drawable.stat_notify_error)
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ -> exitApp() }
                .show()
    }

    private fun askPermissions(): Boolean {
        for (permission in PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, REQUEST_PERMISSIONS)
                return true
            }
        }

        return false
    }

    private fun handleRequestPermissions(grantResults: IntArray) {
        var allowedAll = grantResults.size >= PERMISSIONS.size

        if (grantResults.size >= PERMISSIONS.size) {
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allowedAll = false
                    break
                }
            }
        }

        if (allowedAll) onPermissionsAllowed()
        else fatalError("You must allow permissions !")
    }

    private fun onPermissionsAllowed() {
        if (!OpenCVLoader.initDebug()) fatalError("Failed to initialize OpenCV")

        binding.openVideo.setOnClickListener { handleOpenVideo() }

        setContentView(binding.root)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.menuOpenVideo -> handleOpenVideo()
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (resultCode == RESULT_OK) {
            if (requestCode == INTENT_OPEN_VIDEO) {
                intent?.data?.let { uri -> openVideo(uri) }
                return
            }
        }

        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, intent)
    }

    private fun handleOpenVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                .putExtra(Intent.EXTRA_TITLE, "Select video")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("video/*")
        @Suppress("DEPRECATION")
        startActivityForResult(intent, INTENT_OPEN_VIDEO)
    }

    private fun copyUriToFile(inputUri: Uri, outputFile: String) {
        val inputStream = contentResolver.openInputStream(inputUri) ?: throw FileNotFoundException(inputUri.toString())
        val outputStream = File(outputFile).outputStream()
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
    }

    private fun openVideo(videoUri: Uri) {
        //copy the original video to a temporary location than can be used as a string path
        val tmpFilePath = applicationContext.cacheDir.absoluteFile.path + "/tmp.video"
        copyUriToFile(videoUri, tmpFilePath)

        var details = "File: ${DocumentFile.fromSingleUri(applicationContext, videoUri)?.name ?: "" }\n"

        val cap = VideoCapture(tmpFilePath)

        if (cap.isOpened) {
            details += "isOpen: true\n"
            details += "Backend name: ${cap.backendName ?: ""}\n"
            details += "FPS: ${cap.get(CAP_PROP_FPS)}\n"
            details += "Frame width: ${cap.get(CAP_PROP_FRAME_WIDTH)}\n"
            details += "Frame height: ${cap.get(CAP_PROP_FRAME_HEIGHT)}\n"
            details += "Frame count: ${cap.get(CAP_PROP_FRAME_COUNT)}\n"
            details += "Orientation: ${cap.get(CAP_PROP_ORIENTATION_META)}\n"

            val frame = Mat()
            var frameCounter = 0
            while (cap.read(frame)) frameCounter++
            details += "Read frame counted: ${frameCounter}\n"
        } else {
            details += "isOpen: false"
        }

        binding.details.text = details
    }
}