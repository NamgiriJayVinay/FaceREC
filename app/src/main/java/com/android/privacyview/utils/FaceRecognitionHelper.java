package com.android.privacyview.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.FaceDetector;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import java.io.File;

import org.tensorflow.lite.Interpreter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FaceRecognitionHelper {
    private static final int EMBEDDING_SIZE = 128; // Size of face embedding vector
    private static final float RECOGNITION_THRESHOLD = 0.7f; // Similarity threshold
    private Interpreter tfLite;
    private static final String MODEL_FILE = "facenet_model.tflite";
    private static final int INPUT_SIZE = 160; // The input size the model expects
    private Context context;
    private Interpreter interpreter;
    private Map<String, float[]> faceEmbeddings;

    private static final String SHARED_PREFS_NAME = "FaceEmbeddings";
    private static final String EMBEDDING_PREFIX = "face_embedding_";
    private static final String NAME_LIST_KEY = "registered_faces";


    private Gson gson;

    public FaceRecognitionHelper(Context context) {
        this.context = context;
        faceEmbeddings = new HashMap<>();
        this.gson = new Gson();
        initializeInterpreter();
        loadFaceEmbeddings();
    }

    public void initializeInterpreter() {
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4); // Adjust based on your needs

            // Load model from assets
            ByteBuffer model = loadModelFile();
            interpreter = new Interpreter(model, options);
        } catch (IOException e) {
            Log.e("FaceRecognitionHelper", "Error initializing TFLite interpreter", e);
        }
    }

    public boolean addNewFace(Bitmap faceBitmap, String personName) {
        try {
            // Generate embedding for the new face
            float[] newEmbedding = generateEmbedding(faceBitmap);
            if (newEmbedding == null) {
                Log.e("FaceRecognitionHelper", "Failed to generate embedding for new face");
                return false;
            }

            // Check if this face is too similar to any existing face
            for (Map.Entry<String, float[]> entry : faceEmbeddings.entrySet()) {
                float distance = calculateDistance(newEmbedding, entry.getValue());
                if (distance < RECOGNITION_THRESHOLD) {
                    Log.w("FaceRecognitionHelper", "Face too similar to existing face: " + entry.getKey());
                    return false;
                }else{
                    Log.w("FaceRecognitionHelper", "Face is embedding " + entry.getKey());

                }
            }

            // Add the new embedding to the map
            faceEmbeddings.put(personName, newEmbedding);
            Log.w("FaceRecognitionHelper", "Face is embedded as " + personName+" with embedidngs as : "+newEmbedding);


            // Save to persistent storage
            return saveFaceEmbeddings();

        } catch (Exception e) {
            Log.e("FaceRecognitionHelper", "Error adding new face", e);
            return false;
        }
    }

    private boolean saveFaceEmbeddings() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // Save the list of names
            Set<String> nameSet = new HashSet<>(faceEmbeddings.keySet());
            editor.putStringSet(NAME_LIST_KEY, nameSet);

            // Save each embedding
            for (Map.Entry<String, float[]> entry : faceEmbeddings.entrySet()) {
                String name = entry.getKey();
                float[] embedding = entry.getValue();
                String embeddingJson = gson.toJson(embedding);
                editor.putString(EMBEDDING_PREFIX + name, embeddingJson);
            }

            return editor.commit();
        } catch (Exception e) {
            Log.e("FaceRecognitionHelper", "Error saving face embeddings", e);
            return false;
        }
    }

    private ByteBuffer loadModelFile() throws IOException {
        String modelPath = MODEL_FILE;
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        Log.w("CODEFLOW","Loaded facenet model success");
        ByteBuffer buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        inputStream.close();
        return buffer;
    }

    // Generate embedding for a face image
    public float[] generateEmbedding(Bitmap faceBitmap) {
        if (interpreter == null) {
            Log.e("FaceRecognitionHelper", "TFLite interpreter is null");
            return null;
        }

        // Preprocess the bitmap to match model input requirements
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);


        // Convert bitmap to float array
        ByteBuffer imgData = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4); // 4 bytes per float
        imgData.order(ByteOrder.nativeOrder());



        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        scaledBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        // Normalize pixel values to [-1, 1]
        for (int pixel : pixels) {
            float r = ((pixel >> 16) & 0xFF) / 127.5f - 1;
            float g = ((pixel >> 8) & 0xFF) / 127.5f - 1;
            float b = (pixel & 0xFF) / 127.5f - 1;

            imgData.putFloat(r);
            imgData.putFloat(g);
            imgData.putFloat(b);
        }

        // Output array for the model
        float[][] embeddings = new float[1][128]; // Adjust size based on your model's output

        try {
            // Run inference
            Object[] inputArray = {imgData.rewind()};
            Map<Integer, Object> outputMap = new HashMap<>();
            outputMap.put(0, embeddings);
            Log.w("CODEFLOW","embeddings obtained are : "+embeddings);

            interpreter.runForMultipleInputsOutputs(inputArray, outputMap);

            return embeddings[0];
        } catch (Exception e) {
            Log.e("FaceRecognitionHelper", "Error running model inference", e);
            return null;
        } finally {
            if (scaledBitmap != faceBitmap) {
                scaledBitmap.recycle();
            }
        }
    }

    public void runInference(float[][][][] preprocessedImage,float[][] embeddings){
        interpreter.run(preprocessedImage, embeddings);
        Log.w("CODEFLOW","Inference success");

    }

    // Preprocess the face bitmap: resize and normalize
    private Bitmap preprocessBitmap(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        return resizedBitmap;
    }

    // Convert the bitmap to a ByteBuffer for the TFLite model
    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3); // Float size * width * height * channels
        buffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : intValues) {
            // Extract RGB values, normalize to [-1, 1]
            float r = ((pixel >> 16) & 0xFF) / 255.0f;
            float g = ((pixel >> 8) & 0xFF) / 255.0f;
            float b = (pixel & 0xFF) / 255.0f;

            buffer.putFloat((r - 0.5f) * 2);
            buffer.putFloat((g - 0.5f) * 2);
            buffer.putFloat((b - 0.5f) * 2);
        }
        return buffer;
    }


    // Recognize a face by comparing with stored embeddings
    public String recognizeFace(float[] newEmbedding) {
        if (newEmbedding == null) return "Unknown";

        String closestMatch = "Unknown";
        float minDistance = Float.MAX_VALUE;

        for (Map.Entry<String, float[]> entry : faceEmbeddings.entrySet()) {
            float distance = calculateDistance(newEmbedding, entry.getValue());
            if (distance < minDistance) {
                minDistance = distance;
                closestMatch = entry.getKey();
            }
        }

        return minDistance < RECOGNITION_THRESHOLD ? closestMatch : "Unknown";
    }

    private float calculateDistance(float[] emb1, float[] emb2) {
        float sum = 0;
        for (int i = 0; i < emb1.length; i++) {
            float diff = emb1[i] - emb2[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }


    public void loadFaceEmbeddings() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
            Set<String> nameSet = prefs.getStringSet(NAME_LIST_KEY, new HashSet<>());

            faceEmbeddings.clear();
            for (String name : nameSet) {
                String embeddingJson = prefs.getString(EMBEDDING_PREFIX + name, null);
                if (embeddingJson != null) {
                    float[] embedding = gson.fromJson(embeddingJson, float[].class);
                    faceEmbeddings.put(name, embedding);
                }
            }

            Log.d("FaceRecognitionHelper", "Loaded " + faceEmbeddings.size() + " face embeddings");
        } catch (Exception e) {
            Log.e("FaceRecognitionHelper", "Error loading face embeddings", e);
        }
    }
    // Load embeddings from storage


    public float[][][][] preprocessImage(Bitmap faceBitmap, int height, int width) {
        // CODEFLOW: Step - Resize and normalize image for FaceNet input.
        try {
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(faceBitmap, width, height, true);
            int batchSize = 1; // Single image batch.
            float[][][][] normalizedImage = new float[batchSize][width][height][3];

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int pixel = resizedBitmap.getPixel(x, y);
                    // Normalize RGB values to [-1, 1].
                    normalizedImage[0][y][x][0] = (Color.red(pixel) / 127.5f) - 1.0f;
                    normalizedImage[0][y][x][1] = (Color.green(pixel) / 127.5f) - 1.0f;
                    normalizedImage[0][y][x][2] = (Color.blue(pixel) / 127.5f) - 1.0f;
                }
                ;
            }
            Log.w("CODEFLOW", "preprocess done " + faceBitmap);

            return normalizedImage;


        } catch (Exception e) {
            e.printStackTrace();

        }
        return null;
    }
}