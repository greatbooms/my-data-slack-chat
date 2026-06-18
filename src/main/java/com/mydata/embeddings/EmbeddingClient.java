package com.mydata.embeddings;

public interface EmbeddingClient {
    String model();

    int dimensions();

    float[] embed(String text);
}
