package dev.infrai.fieldservice;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiRestClient implements InfraiAvatarGateway {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern DATA_ID = Pattern.compile(
            "\\\"data\\\"\\s*:\\s*\\{.*?\\\"id\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"",
            Pattern.DOTALL);
    private static final Pattern ERROR_CODE = Pattern.compile(
            "\\\"error\\\"\\s*:\\s*\\{.*?\\\"code\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"",
            Pattern.DOTALL);
    private static final Pattern ERROR_MESSAGE = Pattern.compile(
            "\\\"error\\\"\\s*:\\s*\\{.*?\\\"message\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"",
            Pattern.DOTALL);

    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiRestClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiRestClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public String upload(Path photo, String requestId) {
        String boundary = "infrai-" + UUID.randomUUID();
        String filename = photo.getFileName().toString();
        try {
            byte[] prefix = ("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"filename\"\r\n\r\n"
                    + filename + "\r\n--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                    + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.concat(
                    HttpRequest.BodyPublishers.ofByteArray(prefix),
                    HttpRequest.BodyPublishers.ofFile(photo),
                    HttpRequest.BodyPublishers.ofByteArray(suffix));
            return sendForId("POST", "/v1/image/upload", body,
                    "multipart/form-data; boundary=" + boundary, requestId);
        } catch (IOException error) {
            throw new IllegalArgumentException("Cannot read technician photo: " + photo, error);
        }
    }

    @Override
    public String squareCrop(String image, String requestId) {
        return sendJsonForId("POST", "/v1/image/smart_crop",
                "{\"image\":\"" + json(image) + "\",\"aspect\":\"1:1\"}", requestId);
    }

    @Override
    public String optimize(String image, String requestId) {
        return sendJsonForId("POST", "/v1/image/resize",
                "{\"image\":\"" + json(image) + "\",\"width\":512,\"height\":512,"
                        + "\"fit\":\"cover\",\"enlarge\":false,\"format\":\"webp\",\"store\":true}",
                requestId);
    }

    @Override
    public void attachToUser(String userId, String image, String requestId) {
        send("PATCH", "/v1/auth/user/update/" + path(userId),
                HttpRequest.BodyPublishers.ofString(
                        "{\"user_id\":\"" + json(userId) + "\",\"metadata\":{\"image\":\""
                                + json(image) + "\"}}"),
                "application/json", requestId);
    }

    private String sendJsonForId(String method, String path, String json, String requestId) {
        return sendForId(method, path, HttpRequest.BodyPublishers.ofString(json),
                "application/json", requestId);
    }

    private String sendForId(
            String method, String path, HttpRequest.BodyPublisher body, String contentType, String requestId) {
        Envelope envelope = send(method, path, body, contentType, requestId);
        Matcher id = DATA_ID.matcher(envelope.body());
        if (!id.find()) {
            throw new IllegalStateException("Successful image response did not contain data.id");
        }
        return unescape(id.group(1));
    }

    private Envelope send(
            String method, String path, HttpRequest.BodyPublisher body, String contentType, String requestId) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", contentType)
                    .header("Idempotency-Key", requestId)
                    .method(method, body)
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Envelope envelope = decodeEnvelope(response.body(), response.statusCode());
                if (response.statusCode() == 429 && attempt < 3) {
                    long delay = response.headers().firstValue("Retry-After")
                            .map(InfraiRestClient::retryAfterMillis)
                            .orElse(250L * (1L << attempt));
                    Thread.sleep(delay);
                    continue;
                }
                if (response.statusCode() >= 500) {
                    throw new IOException("Infrai transport response " + response.statusCode());
                }
                if (!envelope.ok()) {
                    throw new InfraiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
                }
                return envelope;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", error);
            } catch (IOException error) {
                throw new IllegalStateException("Could not reach Infrai", error);
            }
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private static Envelope decodeEnvelope(String body, int statusCode) throws IOException {
        Matcher ok = OK.matcher(body);
        if (!ok.find()) {
            if (statusCode >= 500) {
                throw new IOException("Transport response did not contain an envelope");
            }
            throw new IllegalStateException("Response did not contain an Infrai envelope");
        }
        boolean success = Boolean.parseBoolean(ok.group(1));
        String code = capture(ERROR_CODE, body, "request_rejected");
        String message = capture(ERROR_MESSAGE, body, "Request rejected");
        return new Envelope(success, body, code, message);
    }

    private static String capture(Pattern pattern, String body, String fallback) {
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? unescape(matcher.group(1)) : fallback;
    }

    private static long retryAfterMillis(String value) {
        try {
            return Math.max(0L, Long.parseLong(value.trim()) * 1_000L);
        } catch (NumberFormatException ignored) {
            return 1_000L;
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String path(String value) {
        return URI.create("https://local/" + value.replace(" ", "%20")).getRawPath().substring(1);
    }

    private record Envelope(boolean ok, String body, String errorCode, String errorMessage) {}
}
