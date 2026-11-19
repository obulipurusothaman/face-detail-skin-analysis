package com.example.faceapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;

import com.example.faceapp.databinding.ActivityMainBinding;

import java.io.File;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private FaceAnalyzerHelper analyzer;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> { if (granted) startCamera(); else Toast.makeText(this,"Camera permission is required",Toast.LENGTH_LONG).show(); });

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> { if (uri != null) analyzeGallery(uri); });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater()); setContentView(binding.getRoot());
        cameraExecutor = Executors.newSingleThreadExecutor(); analyzer = new FaceAnalyzerHelper();
        binding.scanButton.setOnClickListener(v -> captureAndAnalyze());
        binding.galleryButton.setOnClickListener(v -> galleryLauncher.launch("image/*"));
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera();
        else permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider provider = ProcessCameraProvider.getInstance(this).get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture);
            } catch (Exception e) { Toast.makeText(this,"Unable to start camera: "+e.getMessage(),Toast.LENGTH_LONG).show(); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndAnalyze() {
        if (imageCapture == null) return;
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override public void onCaptureSuccess(androidx.camera.core.ImageProxy image) {
                Bitmap bitmap = image.toBitmap(); image.close();
                Matrix mirror = new Matrix(); mirror.preScale(-1,1); bitmap = Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),mirror,true);
                analyzeBitmap(bitmap);
            }
            @Override public void onError(ImageCaptureException exception) { Toast.makeText(MainActivity.this,"Capture failed",Toast.LENGTH_SHORT).show(); }
        });
    }

    private void analyzeGallery(Uri uri) {
        try (InputStream stream=getContentResolver().openInputStream(uri)) {
            BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
            Bitmap bitmap=BitmapFactory.decodeStream(stream,null,opts); if(bitmap==null)return;
            try (InputStream exifStream=getContentResolver().openInputStream(uri)) {
                if(exifStream!=null){ ExifInterface exif=new ExifInterface(exifStream); int orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL); Matrix m=new Matrix();
                    if(orientation==ExifInterface.ORIENTATION_ROTATE_90)m.postRotate(90); else if(orientation==ExifInterface.ORIENTATION_ROTATE_180)m.postRotate(180); else if(orientation==ExifInterface.ORIENTATION_ROTATE_270)m.postRotate(270);
                    bitmap=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);
                }
            }
            analyzeBitmap(bitmap);
        } catch(Exception e){Toast.makeText(this,"Could not open image",Toast.LENGTH_SHORT).show();}
    }

    private void analyzeBitmap(Bitmap bitmap) {
        if (bitmap.getWidth()>1200 || bitmap.getHeight()>1200) {
            float scale=Math.min(1200f/bitmap.getWidth(),1200f/bitmap.getHeight());
            bitmap=Bitmap.createScaledBitmap(bitmap,(int)(bitmap.getWidth()*scale),(int)(bitmap.getHeight()*scale),true);
        }
        Bitmap finalBitmap=bitmap;
        binding.scanButton.setEnabled(false);
        analyzer.analyze(bitmap,new FaceAnalyzerHelper.Callback(){
            @Override public void onReport(AnalysisReport report){binding.scanButton.setEnabled(true);openResult(finalBitmap,report);} 
            @Override public void onError(Exception error){binding.scanButton.setEnabled(true);Toast.makeText(MainActivity.this,"Analysis failed: "+error.getMessage(),Toast.LENGTH_LONG).show();}
        });
    }

    private void openResult(Bitmap bitmap, AnalysisReport report) {
        try {
            File file=new File(getCacheDir(),"scanned_face.jpg");
            java.io.FileOutputStream out=new java.io.FileOutputStream(file); bitmap.compress(Bitmap.CompressFormat.JPEG,92,out);out.close();
            Intent intent=new Intent(this,ResultActivity.class); intent.putExtra("image_path",file.getAbsolutePath());intent.putExtra("report",report);startActivity(intent);
        } catch(Exception e){Toast.makeText(this,"Could not save scan",Toast.LENGTH_SHORT).show();}
    }

    @Override protected void onDestroy(){super.onDestroy();if(cameraExecutor!=null)cameraExecutor.shutdown();}
}
