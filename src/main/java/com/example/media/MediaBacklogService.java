package com.example.media;

import java.io.IOException;

public final class MediaBacklogService {
    private final InfraiClient client;
    private final String deliveryQueue;

    public MediaBacklogService(InfraiClient client, String deliveryQueue) {
        this.client = client;
        this.deliveryQueue = deliveryQueue;
    }

    public String process(String jobId, String filename, String file) throws IOException, InterruptedException {
        String upload = client.post("/v1/image/upload", "{\"file\":\"" + file + "\",\"filename\":\"" + filename + "\"}");
        String image = field(upload, "image");
        String resize = client.post("/v1/image/resize", "{\"image\":\"" + image + "\",\"width\":1280,\"height\":720,\"fit\":\"contain\",\"enlarge\":false,\"format\":\"webp\",\"store\":true}");
        String output = field(resize, "image");
        String payload = "{\"job_id\":\"" + jobId + "\",\"image\":\"" + output + "\",\"status\":\"ready\"}";
        client.post("/v1/queue/publish", "{\"queue\":\"" + deliveryQueue + "\",\"payload\":" + quote(payload) + "}");
        return "DELIVERED " + jobId;
    }

    static boolean mayDeliver(boolean resizeOk) { return resizeOk; }

    private static String field(String json, String name) {
        String marker = "\"" + name + "\":";
        int start = json.indexOf(marker);
        if (start < 0) throw new IllegalStateException("missing response field: " + name);
        int valueStart = start + marker.length();
        if (json.charAt(valueStart) == '"') valueStart++;
        int end = json.indexOf('"', valueStart);
        return end < 0 ? json.substring(valueStart) : json.substring(valueStart, end);
    }

    private static String quote(String value) { return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
}
