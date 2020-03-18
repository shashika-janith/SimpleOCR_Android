package com.janith.simpleocr

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.gms.vision.Frame
import com.google.android.gms.vision.text.TextRecognizer
import com.yalantis.ucrop.UCrop
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private var TAG = this.javaClass.simpleName
    // intent request codes
    private var REQUEST_CAPTURE_IMAGE = 101
    private var REQUEST_PERMISSION_CAMERA = 102
    private var REQUEST_PERMISSION_WRITE_EXT_STORAGE = 103
    private var REQUEST_PERMISSION_READ_EXT_STORAGE = 104
    private var REQUEST_PICK_IMAGE = 105

    private var capturedText: String = ""
    private var imagePath: String? = null
    private var croppedImagePath: String? = null

    private var originalImage: File? = null
    private var croppedImage: File? = null

    private var originalContentUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initActivity()
    }

    private fun initActivity() {
        btnScan.setOnClickListener {
            Log.d(TAG, "Start camera")
//            startCameraActivity()
            showAlert()
        }
    }

    private fun showAlert() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.choose_an_imae_to_scan)
        builder.setPositiveButton("Camera") { _, id -> startCameraActivity() }
        builder.setNegativeButton("Gallery") { dialog, id -> pickImageFromGallery() }
        val alert = builder.create()
        alert.show()
    }

    private fun startCameraActivity() {
        if (checkPermission(Manifest.permission.CAMERA)) {
            try {
                originalImage = createImageFile()
                imagePath = originalImage!!.absolutePath
//                var contentUri: Uri = Uri.fromFile(originalImage)

                originalContentUri = FileProvider.getUriForFile(
                    this,
                    "com.janith.simpleocr.fileprovider",
                    originalImage!!
                )

                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, originalContentUri)
                startActivityForResult(cameraIntent, REQUEST_CAPTURE_IMAGE)
                Log.d(TAG, "Camera started")
            } catch(e: IOException) {
                Log.e(TAG, "Error")
                e.stackTrace
            }
        }
    }

    private fun pickImageFromGallery() {
        if (checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(galleryIntent, REQUEST_PICK_IMAGE)
        }
    }

    private fun doOCR(bitmap: Bitmap) {
        val textRecognizer = TextRecognizer.Builder(this).build()

        if (!textRecognizer.isOperational) {
            AlertDialog.Builder(this)
                .setMessage("Text recognizer could not be set up on your device :(")
                .show()
            return
        }

        val frame = Frame.Builder().setBitmap(bitmap).build()
        val text = textRecognizer.detect(frame)

        capturedText = ""

        for (i in 0 until text.size()) {
            val item = text.valueAt(i)
            val textComponents =
                item.components

            for (elements in textComponents) {
                Log.i("current lines ", ": " + elements.value)
                capturedText = capturedText.plus(elements.value + "\n")
            }
        }
        txtViewCapturedTxt.text = capturedText
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CAPTURE_IMAGE -> {
                if (resultCode == Activity.RESULT_OK) {
                    cropImage()
                }
            }
            REQUEST_PICK_IMAGE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    //TODO: to be implemented
                }
            }
            UCrop.REQUEST_CROP -> {
                if (resultCode == RESULT_OK && data != null) {
                    val resultUri = UCrop.getOutput(data)
                    val bitmap = resultUri?.let { getBitmapFromUri(it) }
                    if (bitmap != null) {
                        doOCR(bitmap)
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val imageFileName = "OCR_" + timeStamp + "_"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val image = File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        )
        return image
    }

    private  fun cropImage() {
        croppedImage = createImageFile()

        val uri: Uri = Uri.fromFile(File(imagePath))
        val croppedContentUri: Uri = Uri.fromFile(croppedImage)

        UCrop.of(uri, croppedContentUri).start(this)
    }

    private fun getBitmapFromUri(uri: Uri): Bitmap? {
        var bmp: Bitmap? = null
        try {
            val imageStream: InputStream?
            try {
                imageStream = contentResolver.openInputStream(uri)
                bmp = BitmapFactory.decodeStream(imageStream)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return bmp
    }

    private fun checkPermission(permission: String) : Boolean {
        when (permission) {
            Manifest.permission.CAMERA -> {
                return if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    true
                } else {
                    requestPermission(REQUEST_PERMISSION_CAMERA)
                    false
                }
            }
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> {
                return if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    true
                } else {
                    requestPermission(REQUEST_PERMISSION_WRITE_EXT_STORAGE)
                    false
                }
            }
            Manifest.permission.READ_EXTERNAL_STORAGE -> {
                return if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    true
                } else {
                    requestPermission(REQUEST_PERMISSION_READ_EXT_STORAGE)
                    false
                }
            }
        }

        return false
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun requestPermission(requestCode: Int) {
        when (requestCode) {
            REQUEST_PERMISSION_CAMERA -> {
                this.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_PERMISSION_CAMERA)
            }
            REQUEST_PERMISSION_WRITE_EXT_STORAGE -> {
                this.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_PERMISSION_WRITE_EXT_STORAGE)
            }
        }
    }

}
