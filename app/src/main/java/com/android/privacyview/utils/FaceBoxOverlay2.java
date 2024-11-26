package com.android.privacyview.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;

import com.google.mlkit.vision.face.Face;

import java.util.ArrayList;
import java.util.List;


public class FaceBoxOverlay2 extends View {
    private static final float BOX_STROKE_WIDTH = 5.0f;
    private static final float TEXT_SIZE = 40.0f;
    private static final int TEXT_PADDING = 10;

    private Paint boxPaint;
    private Paint textPaint;
    private Paint backgroundPaint;

    private Rect faceBox;
    private String personName;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private int previewWidth;
    private int previewHeight;
    private boolean isFrontCamera = true;

    public FaceBoxOverlay2(Context context) {
        super(context);
        init();
    }

    public FaceBoxOverlay2(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FaceBoxOverlay2(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // Initialize box paint
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(BOX_STROKE_WIDTH);

        // Initialize text paint
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(TEXT_SIZE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setAntiAlias(true);

        // Initialize background paint for text
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.argb(128, 0, 0, 0));
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    public void setPreviewSize(int width, int height) {
        previewWidth = width;
        previewHeight = height;
        calculateScale();
    }

    private void calculateScale() {
        if (previewWidth > 0 && previewHeight > 0) {
            scaleX = (float) getWidth() / previewWidth;
            scaleY = (float) getHeight() / previewHeight;
        }
    }

    public void setIsFrontCamera(boolean isFront) {
        isFrontCamera = isFront;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateScale();
    }

    public void updateFaceBox(Rect box, String name) {
        if (box == null) {
            faceBox = null;
            personName = null;
        } else {
            faceBox = new Rect(box);
            personName = name;
        }
        postInvalidate();
    }

    public void clearFaceBox() {
        faceBox = null;
        personName = null;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (faceBox == null || personName == null) {
            return;
        }

        // Scale the face box to match the preview size
        Rect scaledBox = new Rect(
                (int) (faceBox.left * scaleX),
                (int) (faceBox.top * scaleY),
                (int) (faceBox.right * scaleX),
                (int) (faceBox.bottom * scaleY)
        );

        // If using front camera, mirror the box horizontally
        if (isFrontCamera) {
            int width = getWidth();
            int left = scaledBox.left;
            scaledBox.left = width - scaledBox.right;
            scaledBox.right = width - left;
        }

        // Draw the face box
        canvas.drawRect(scaledBox, boxPaint);

        // Draw the name
        if (personName != null && !personName.isEmpty()) {
            // Measure text for background
            Rect textBounds = new Rect();
            textPaint.getTextBounds(personName, 0, personName.length(), textBounds);

            // Calculate text position
            float textX = scaledBox.left;
            float textY = scaledBox.top - TEXT_PADDING;

            // Draw text background
            float backgroundLeft = textX - TEXT_PADDING;
            float backgroundRight = textX + textBounds.width() + TEXT_PADDING;
            float backgroundTop = textY - textBounds.height() - TEXT_PADDING;
            float backgroundBottom = textY + TEXT_PADDING;

            canvas.drawRect(
                    backgroundLeft,
                    backgroundTop,
                    backgroundRight,
                    backgroundBottom,
                    backgroundPaint
            );

            // Draw the name text
            canvas.drawText(personName, textX, textY, textPaint);
        }
    }
}

