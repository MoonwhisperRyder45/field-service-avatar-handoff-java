package dev.infrai.fieldservice;

import java.nio.file.Path;

public interface InfraiAvatarGateway {
    String upload(Path photo, String requestId);

    String squareCrop(String image, String requestId);

    String optimize(String image, String requestId);

    void attachToUser(String userId, String image, String requestId);
}
