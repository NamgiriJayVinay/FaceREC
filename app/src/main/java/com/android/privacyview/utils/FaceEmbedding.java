package com.android.privacyview.utils;

public class FaceEmbedding {
    private float[] embedding;
    private String name;

    public FaceEmbedding(float[] embedding, String name) {
        this.embedding = embedding;
        this.name = name;
    }

    public float[] getEmbedding() {
        return embedding;
    }

    public String getName() {
        return name;
    }
}