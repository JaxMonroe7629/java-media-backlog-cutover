package com.example.media;

public final class MediaBacklogMain {
    public static void main(String[] args) throws Exception {
        String key = System.getenv("INFRAI_API_KEY");
        String sourceQueue = env("INFRAI_QUEUE", "media-image-source");
        String deliveryQueue = env("INFRAI_DELIVERY_QUEUE", "creator-delivery");
        InfraiClient client = new InfraiClient(key);
        String response = client.post("/v1/queue/consume", "{\"queue\":\"" + sourceQueue + "\",\"max_messages\":10,\"visibility_timeout\":60}");
        System.out.println("Queue response received: " + response);
        System.out.println("Configured delivery queue: " + deliveryQueue);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
