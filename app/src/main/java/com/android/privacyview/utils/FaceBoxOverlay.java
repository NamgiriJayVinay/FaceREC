package com.android.privacyview.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.graphics.Path;

import com.google.mlkit.vision.face.Face;

import java.util.ArrayList;
import java.util.List;

public class FaceBoxOverlay extends View {
    private List<Face> faces = new ArrayList<>();
    private final Paint boxPaint;
    private int previewWidth = 640;
    private int previewHeight = 480;
    private Path clipPath;
    private FaceDetectionListener listener;
    private int imageWidth = 0;
    private int imageHeight = 0;
    private int facing = 1; // 1 for front camera, 0 for back camera
    private Paint textPaint;
    private List<Pair<Rect, String>> faceBoxesWithNames;

    public interface FaceDetectionListener {
        void onGoodFaceDetected(Face face, Rect boundingBox);
        void onFaceLost();
    }

    public void updateFaceBox(Rect bounds, String name) {
        if (faceBoxesWithNames == null) {
            faceBoxesWithNames = new ArrayList<>();
        }
        faceBoxesWithNames.add(new Pair<>(bounds, name));
        invalidate();
    }

    public FaceBoxOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3.0f);
        clipPath = new Path();
    }

    public void setFaceDetectionListener(FaceDetectionListener listener) {
        this.listener = listener;
    }

    public void setImageSourceInfo(int width, int height, int facing) {
        this.imageWidth = width;
        this.imageHeight = height;
        this.facing = facing;
    }

    public void setFaces(List<Face> faces) {
        this.faces = faces;
        checkFaceQuality();
        invalidate();
    }

    private void checkFaceQuality() {
        if (faces.size() == 1) {
            Face face = faces.get(0);
            Rect boundingBox = face.getBoundingBox();

            // Get normalized coordinates
            float centerX = (boundingBox.centerX() / (float) imageWidth);
            float width = (boundingBox.width() / (float) imageWidth);

            // Check if face is centered and of good size
            boolean isCentered = centerX > 0.3f && centerX < 0.7f;
            boolean isGoodSize = width > 0.25f && width < 0.85f;

            if (isCentered && isGoodSize && face.getHeadEulerAngleY() > -10 &&
                    face.getHeadEulerAngleY() < 10) {
                if (listener != null) {
                    listener.onGoodFaceDetected(face, boundingBox);
                }
                return;
            }
        }

        if (listener != null) {
            listener.onFaceLost();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (faceBoxesWithNames != null) {
            for (Pair<Rect, String> faceBox : faceBoxesWithNames) {
                // Draw box
                canvas.drawRect(faceBox.first, boxPaint);
                // Draw name
                canvas.drawText(faceBox.second,
                        faceBox.first.left,
                        faceBox.first.top - 10,
                        textPaint);
            }
        }
    }



}