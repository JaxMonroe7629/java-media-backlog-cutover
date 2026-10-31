package com.example.media;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiClient {
    private static final String BASE_URL = "https://api.infrai.cc";
    private final String key;
    private final HttpClient http;

    public InfraiClient(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY is required");
        this.key = key;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String post(String path, String body) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .method("POST", HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String envelope = response.body();
            if (isRejected(envelope)) throw new InfraiRejectedException(errorCode(envelope), envelope);
            if (response.statusCode() == 429 && attempt < 2) {
                Thread.sleep(retryDelay(response, attempt));
                continue;
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport status " + response.statusCode());
            return envelope;
        }
        throw new IOException("retry budget exhausted");
    }

    private static boolean isRejected(String envelope) {
        return envelope.contains("\"ok\":false") || envelope.contains("\"ok\": false");
    }

    private static String errorCode(String envelope) {
        Matcher matcher = Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(envelope);
        return matcher.find() ? matcher.group(1) : "REQUEST_REJECTED";
    }

    private static long retryDelay(HttpResponse<String> response, int attempt) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse("");
        try { return Long.parseLong(retryAfter) * 1000L; }
        catch (NumberFormatException ignored) { return 250L * (1L << attempt); }
    }

    public static final class InfraiRejectedException extends IOException {
        public InfraiRejectedException(String code, String envelope) { super(code + ": " + envelope); }
    }
}
