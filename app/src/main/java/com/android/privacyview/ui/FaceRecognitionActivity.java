package com.android.privacyview.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// Camera X imports
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

// ML Kit imports
import com.android.privacyview.utils.FaceBoxOverlay2;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

// Your custom imports (adjust package name as needed)
import com.android.privacyview.utils.FaceRecognitionHelper;
import com.android.privacyview.utils.FaceBoxOverlay;
import com.android.privacyview.R;

// Utility imports
import com.google.common.util.concurrent.ListenableFuture;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Optional imports for additional features
import android.util.Size;
import android.util.Log;
import java.util.ArrayList;
import android.graphics.ImageFormat;
import android.graphics.YuvImage;
import java.io.IOException;

public class FaceRecognitionActivity extends AppCompatActivity {
    private PreviewView previewView;
    private FaceBoxOverlay recognitionOverlay;
    private FaceBoxOverlay2 recognitionOverlay2;

    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private FaceRecognitionHelper faceRecognitionHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_recognition);

        previewView = findViewById(R.id.recognition_preview_view);
        recognitionOverlay = findViewById(R.id.recognition_face_overlay);

        faceRecognitionHelper = new FaceRecognitionHelper(this);
        faceRecognitionHelper.loadFaceEmbeddings();

        setupCamera();
    }

    private void setupCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build();
        faceDetector = FaceDetection.getClient(options);

        ProcessCameraProvider.getInstance(this).addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = ProcessCameraProvider.getInstance(this).get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            @OptIn(markerClass = ExperimentalGetImage.class) @SuppressWarnings("ConstantConditions")
            Image image = imageProxy.getImage();
            if (image == null) {
                imageProxy.close();
                return;
            }

            InputImage inputImage = InputImage.fromMediaImage(image,
                    imageProxy.getImageInfo().getRotationDegrees());

            faceDetector.process(inputImage)
                    .addOnSuccessListener(faces -> {
                        for (Face face : faces) {
                            Rect bounds = face.getBoundingBox();
                            // Convert image to bitmap and crop face
                            Bitmap faceBitmap = cropFaceFromImage(image, bounds);
                            if (faceBitmap != null) {
                                // Generate embedding
                                float[] embedding = faceRecognitionHelper.generateEmbedding(faceBitmap);
                                // Get recognized name
                                String recognizedName = faceRecognitionHelper.recognizeFace(embedding);
                                // Update overlay with name
//                                recognitionOverlay.updateFaceBox(bounds, recognizedName);
                                Toast.makeText(this,"Found "+recognizedName,Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        });

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
            );

            preview.setSurfaceProvider(previewView.getSurfaceProvider());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap cropFaceFromImage(Image image, Rect bounds) {
        // Implementation similar to your existing image conversion code
        // but cropping to the face bounds
        try {
            Bitmap fullBitmap = imageToBitmap(image);
            return Bitmap.createBitmap(fullBitmap,
                    bounds.left, bounds.top,
                    bounds.width(), bounds.height());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    private Bitmap imageToBitmap(Image image) {
        if (image == null) return null;

        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}