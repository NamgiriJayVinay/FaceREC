package com.android.privacyview.ui;

import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.privacyview.R;
import com.android.privacyview.services.FaceClassifier;
import com.android.privacyview.utils.FaceBoxOverlay;

import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.mikhaellopez.circularprogressbar.CircularProgressBar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class FaceDetection extends AppCompatActivity implements FaceBoxOverlay.FaceDetectionListener {
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final int MAX_CAPTURES = 6;

    private PreviewView previewView;
    private FaceBoxOverlay faceBoxOverlay;
    private ExecutorService cameraExecutor;
    private FaceDetector faceDetector;
    private ImageCapture imageCapture;
    private AtomicInteger captureCount = new AtomicInteger(0);
    private long lastCaptureTime = 0;
    private static final long CAPTURE_DELAY_MS = 1000; // 1 second delay between captures


    private Bitmap lastProcessedBitmap;
    private final Object bitmapLock = new Object();

    private static final Size PREVIEW_SIZE = new Size(640, 480);



    private LinearLayout nameRegistrationLayout;
    private EditText nameEditText;
    private Button registerButton,addAnotherButton,doneButton;
    private TextView capturedFacesCountText;

    private CardView previewContainer;

    private List<String> capturedFacePaths = new ArrayList<>();

    // Add these to your existing variable declarations
    private CardView captureProgressCardView;
    private CircularProgressBar circularProgressBar;
    private TextView progressTextView,positionHints;
    private FaceClassifier faceClassifier;

    String name;

    LinearLayout postRegister;
    private Interpreter tflite;

    public static HashMap<String, FaceClassifier.Recognition> registered = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.android.privacyview.R.layout.live_face);



        previewView = findViewById(com.android.privacyview.R.id.preview_view);
        previewContainer = findViewById(com.android.privacyview.R.id.preview_container);
        faceBoxOverlay = findViewById(com.android.privacyview.R.id.face_box_overlay);
        faceBoxOverlay.setFaceDetectionListener(this);
        nameRegistrationLayout = findViewById(com.android.privacyview.R.id.nameRegistrationLayout);
        nameRegistrationLayout.setVisibility(View.INVISIBLE);
        nameEditText = findViewById(com.android.privacyview.R.id.nameEditText);
        positionHints = findViewById(com.android.privacyview.R.id.position_hints);
        positionHints.setVisibility(View.VISIBLE);



        registerButton = findViewById(com.android.privacyview.R.id.registerButton);

        postRegister = findViewById(com.android.privacyview.R.id.postRegister);

        addAnotherButton =findViewById(com.android.privacyview.R.id.addAnotherButton);
        doneButton = findViewById(com.android.privacyview.R.id.doneButton);
        capturedFacesCountText = findViewById(com.android.privacyview.R.id.capturedFacesCountText);

        // New view initializations
        circularProgressBar = findViewById(com.android.privacyview.R.id.circularProgressBar);
        //====params for circualr progress===
        // or with gradient
        circularProgressBar.setProgressBarColorStart(Color.GRAY);
        circularProgressBar.setProgressBarColorEnd(Color.GREEN);
        circularProgressBar.setProgressBarColorDirection(CircularProgressBar.GradientDirection.TOP_TO_BOTTOM);
        // Set Width
        circularProgressBar.setProgressBarWidth(17f); // in DP
        circularProgressBar.setRoundBorder(true);
        //==============
        progressTextView = findViewById(R.id.progressTextView);
        requestPermissions();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build();

        faceDetector = com.google.mlkit.vision.face.FaceDetection.getClient(options);
        cameraExecutor = Executors.newSingleThreadExecutor();


        try {
            tflite = new Interpreter(loadModelFile("facenet_model.tflite"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void requestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        if (!hasPermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            startCamera();
        }


        registerButton.setOnClickListener(v -> registerCapturedFaces());
    }

    private void registerCapturedFaces() {
        name = nameEditText.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create folder in Documents
        File documentsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS);

        File embeddingsFolder = new File(documentsDir, name + "_Embeddings");
        File facesFolder = new File(documentsDir, name + "_Faces");


        if (!facesFolder.exists()) facesFolder.mkdirs();



        // Move captured faces to the new folder
        for (String sourcePath : capturedFacePaths) {
            File sourceFile = new File(sourcePath);
            File destFile = new File(facesFolder, sourceFile.getName());


            try {
                FileInputStream in = new FileInputStream(sourceFile);
                FileOutputStream out = new FileOutputStream(destFile);

                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }

                in.close();
                out.close();

                // Optional: Delete original file
                sourceFile.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }




        // Clear the list and reset UI
        capturedFacePaths.clear();
        Toast.makeText(this, "Faces registered for " + name, Toast.LENGTH_LONG).show();

        // Reset for potential new capture

        //TODO : add another person or done buttons and then take below actions
        postRegister.setVisibility(View.VISIBLE);
        registerButton.setVisibility(View.GONE);
        addAnotherButton.setOnClickListener(v -> {
            nameEditText.setText("");
            registerButton.setVisibility(View.VISIBLE);
            showCameraPreview();
            postRegister.setVisibility(View.INVISIBLE);
        });

        doneButton.setOnClickListener(v->{
            this.finishAffinity();
        });


    }

    private float[] generateFaceEmbedding(Bitmap bitmap) {
        float[][][][] input = preprocessImage(bitmap);
        float[][] output = new float[1][128]; // Adjust to [1][512] if using a 512-dimensional model

        tflite.run(input, output);

        return output[0]; // Return the embedding vector
    }

    private float[][][][] preprocessImage(Bitmap bitmap) {
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, 160, 160, true);
        float[][][][] input = new float[1][160][160][3];
        for (int i = 0; i < 160; i++) {
            for (int j = 0; j < 160; j++) {
                int pixel = scaledBitmap.getPixel(i, j);
                input[0][i][j][0] = ((pixel >> 16) & 0xFF) / 255.0f; // R
                input[0][i][j][1] = ((pixel >> 8) & 0xFF) / 255.0f;  // G
                input[0][i][j][2] = (pixel & 0xFF) / 255.0f;         // B
            }
        }
        Log.i("CODEFLOW","Preprocess done!");

        return input;
    }


    private MappedByteBuffer loadModelFile(String modelFileName) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelFileName);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        Toast.makeText(this,"Loaded Tflite model",Toast.LENGTH_SHORT).show();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }


    private void saveEmbeddingsToJson(File facesFolder, Map<String, float[]> embeddingsMap) {
        JSONObject jsonObject = new JSONObject();
        try {
            for (Map.Entry<String, float[]> entry : embeddingsMap.entrySet()) {
                JSONArray embeddingArray = new JSONArray();
                for (float value : entry.getValue()) {
                    embeddingArray.put(value);
                }
                jsonObject.put(entry.getKey(), embeddingArray);
            }

            File embeddingsFile = new File(facesFolder, "face_embeddings.json");
            FileWriter writer = new FileWriter(embeddingsFile);
            writer.write(jsonObject.toString());
            writer.close();
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }



    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onGoodFaceDetected(Face face, Rect boundingBox) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCaptureTime >= CAPTURE_DELAY_MS &&
                captureCount.get() < MAX_CAPTURES && lastProcessedBitmap != null) {
            synchronized (bitmapLock) {
                if (lastProcessedBitmap != null) {
                    String savedImagePath = cropAndSaveFace(lastProcessedBitmap, face, boundingBox);
                    if (savedImagePath != null) {
                        capturedFacePaths.add(savedImagePath);
                        // Update progress
                        updateCaptureProgress(capturedFacePaths.size());
                    }
                }
            }
            lastCaptureTime = currentTime;
        }
    }
    public void updateCaptureProgress(int capturedCount) {
        // Calculate progress percentage
        int progressPercentage = (capturedCount * 100) / MAX_CAPTURES;

        // Update circular progress bar
        circularProgressBar.setProgress(progressPercentage);

        // Update progress text
        progressTextView.setText(String.format("%d%%", progressPercentage));

        // If all faces captured, prepare for registration
        if (capturedCount >= MAX_CAPTURES) {
            showNameRegistration();
        }
    }

    public String cropAndSaveFace(Bitmap originalBitmap, Face face, Rect boundingBox) {
        try {
            // Similar to previous implementation, but return the file path
            // Add padding to the bounding box
            int padding = Math.min(boundingBox.width(), boundingBox.height()) / 8;
            Rect paddedBox = new Rect(
                    boundingBox.left - padding,
                    boundingBox.top - padding,
                    boundingBox.right + padding,
                    boundingBox.bottom + padding
            );

            // Ensure the padded box is within image bounds
            paddedBox.left = Math.max(0, paddedBox.left);
            paddedBox.top = Math.max(0, paddedBox.top);
            paddedBox.right = Math.min(originalBitmap.getWidth(), paddedBox.right);
            paddedBox.bottom = Math.min(originalBitmap.getHeight(), paddedBox.bottom);

            // Create the cropped bitmap
            Bitmap croppedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    paddedBox.left,
                    paddedBox.top,
                    paddedBox.width(),
                    paddedBox.height()
            );

            // Save the cropped face and return its path
            return saveFaceImage(croppedBitmap);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String saveFaceImage(Bitmap faceBitmap) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String filename = "FACE_" + timestamp + ".jpg";

        // Using External Storage for easier file management
        File picturesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES);
        File tempFacesDir = new File(picturesDir, "TempFaces");

        if (!tempFacesDir.exists()) {
            tempFacesDir.mkdirs();
        }

        //cropped face
        File imageFile = new File(tempFacesDir, filename);

        // face recognition logic---
        faceBitmap = Bitmap.createScaledBitmap(faceBitmap,160,160,false);
        FaceClassifier.Recognition recognition = faceClassifier.recognizeImage(faceBitmap,true);



        //------


        try (FileOutputStream out = new FileOutputStream(imageFile)) {
            faceBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

            int count = captureCount.incrementAndGet();
            runOnUiThread(() -> {
                capturedFacesCountText.setText("Faces Captured: " + count);

                // When max captures reached, switch to name registration
                if (count >= MAX_CAPTURES) {
                    showNameRegistration();
                    // after this we get the name as name is global
                    saveFaceEmbeddings(recognition);
                }
            });

            return imageFile.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void saveFaceEmbeddings(FaceClassifier.Recognition recognition) {
        faceClassifier.register(name,recognition);
    }

    @Override
    public void onFaceLost() {
        // Face is no longer detected or not in good position
    }

    private void captureFace(RectF faceBounds) {
        if (imageCapture == null) return;

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String filename = "FACE_" + timestamp + ".jpg";

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/FaceCaptures");
        }

        ImageCapture.OutputFileOptions outputFileOptions =
                new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build();

        imageCapture.takePicture(outputFileOptions, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        int count = captureCount.incrementAndGet();
                        runOnUiThread(() -> {
                            Toast.makeText(FaceDetection.this,
                                    "Captured image " + count + " of " + MAX_CAPTURES,
                                    Toast.LENGTH_SHORT).show();

                            if (count >= MAX_CAPTURES) {
                                Toast.makeText(FaceDetection.this,
                                        "All captures completed!",
                                        Toast.LENGTH_LONG).show();

                                // registration success here
                            }
                        });
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() -> Toast.makeText(FaceDetection.this,
                                "Error capturing image: " + exception.getMessage(),
                                Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void startCamera() {
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

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetResolution(new Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, imageProxy -> {
            try {
                // Get the image format
                @OptIn(markerClass = ExperimentalGetImage.class) @SuppressWarnings("ConstantConditions")
                Image image = imageProxy.getImage();
                if (image == null) {
                    imageProxy.close();
                    return;
                }

                // Convert Image to Bitmap
                Bitmap bitmap = imageToBitmap(image);

                // Handle rotation
                int rotation = imageProxy.getImageInfo().getRotationDegrees();
                if (rotation != 0) {
                    Matrix matrix = new Matrix();
                    matrix.postRotate(rotation);
                    bitmap = Bitmap.createBitmap(
                            bitmap,
                            0,
                            0,
                            bitmap.getWidth(),
                            bitmap.getHeight(),
                            matrix,
                            true
                    );
                }
                // we will get every face image bitmap here :

                synchronized (bitmapLock) {
                    lastProcessedBitmap = bitmap;
                }

                // Create InputImage from Image object directly
                InputImage inputImage = InputImage.fromMediaImage(image, rotation);

                Bitmap finalBitmap = bitmap;
                faceDetector.process(inputImage)
                        .addOnSuccessListener(faces -> {
                            faceBoxOverlay.setImageSourceInfo(
                                    finalBitmap.getWidth(),
                                    finalBitmap.getHeight(),
                                    CameraSelector.LENS_FACING_FRONT
                            );
                            faceBoxOverlay.setFaces(faces);
                        })
                        .addOnCompleteListener(task -> imageProxy.close());

            } catch (Exception e) {
                imageProxy.close();
                e.printStackTrace();
            }
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
                    imageCapture,
                    imageAnalysis
            );

            preview.setSurfaceProvider(previewView.getSurfaceProvider());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showNameRegistration() {
        // Hide camera preview
        previewContainer.setVisibility(View.GONE);

        // Show name registration layout
        nameRegistrationLayout.setVisibility(View.VISIBLE);

        // Hide capture progress
        progressTextView.setVisibility(View.GONE);

        positionHints.setVisibility(View.GONE);
    }

    private void showCameraPreview() {

        // Reset progress
        circularProgressBar.setProgress(0);
        progressTextView.setText("0%");

        // Hide name registration layout
        nameRegistrationLayout.setVisibility(View.GONE);

        // Show camera preview
        previewContainer.setVisibility(View.VISIBLE);

        progressTextView.setVisibility(View.VISIBLE);

        positionHints.setVisibility(View.VISIBLE);


        // Reset capture count
        captureCount.set(0);
        capturedFacesCountText.setText("Faces Captured: 0");
        capturedFacePaths.clear();
    }

    // Add this helper method to convert Image to Bitmap
    private Bitmap imageToBitmap(Image image) {
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
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Permissions required to use the app",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
