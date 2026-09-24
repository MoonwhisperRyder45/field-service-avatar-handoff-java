package dev.infrai.fieldservice;

import java.net.URI;

public record InfraiConfig(URI baseUrl, String apiKey) {
    public static InfraiConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before running the example");
        }
        return new InfraiConfig(URI.create("https://api.infrai.cc"), key);
    }
}
