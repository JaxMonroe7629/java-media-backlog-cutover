package com.example.media;

public final class MediaBacklogServiceTest {
    public static void main(String[] args) {
        if (!MediaBacklogService.mayDeliver(true)) throw new AssertionError("ready resize should deliver");
        if (MediaBacklogService.mayDeliver(false)) throw new AssertionError("rejected resize must not deliver");
        System.out.println("business decision test passed");
    }
}
