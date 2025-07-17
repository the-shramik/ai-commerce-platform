package com.telusko.springecomai.service;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;

@Service
public class AIImageGeneratorService {

    @Autowired
    private ImageModel imageModel;

    public byte[] generateImage(String prompt) {
        try {
            // Create image generation options using OpenAI (size, quality, number of images)
            OpenAiImageOptions options = OpenAiImageOptions.builder()
                    .N(1)
                    .width(1024)
                    .height(1024)
                    .quality("standard")
                    .responseFormat("url") // or "b64_json"
                    .model("dall-e-3")     // optional
                    .build();

            // Send prompt to the AI model and receive the image response
            ImageResponse response = imageModel.call(new ImagePrompt(prompt, options));

            // Extract the generated image URL from the response
            String imageUrl = response.getResult().getOutput().getUrl();

            // Download the image using the URL
            try (InputStream in = new URL(imageUrl).openStream()) {
                return in.readAllBytes();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
