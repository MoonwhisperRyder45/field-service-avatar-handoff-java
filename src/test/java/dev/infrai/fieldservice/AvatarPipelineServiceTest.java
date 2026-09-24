package dev.infrai.fieldservice;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AvatarPipelineServiceTest {
    public static void main(String[] args) {
        followsUpAfterProcessingAnActiveDispatch();
        refusesToReopenCompletedWork();
        System.out.println("AvatarPipelineServiceTest passed");
    }

    private static void followsUpAfterProcessingAnActiveDispatch() {
        RecordingGateway gateway = new RecordingGateway();
        AvatarPipelineService service = new AvatarPipelineService(gateway);
        AvatarPipelineService.AvatarUpdate result = service.process(
                new AvatarPipelineService.WorkOrderAvatarRequest(
                        "WO-1042", "tech-7", Path.of("portrait.jpg"),
                        AvatarPipelineService.DispatchStatus.EN_ROUTE));

        check(result.dispatchStatus() == AvatarPipelineService.DispatchStatus.TECHNICIAN_FOLLOW_UP,
                "active dispatch should move to technician follow-up");
        check(result.avatarImage().equals("optimized-image"), "optimized image should reach the result");
        check(gateway.calls.equals(List.of("upload", "crop", "optimize", "attach:tech-7:optimized-image")),
                "pipeline should process before attaching the avatar");
    }

    private static void refusesToReopenCompletedWork() {
        RecordingGateway gateway = new RecordingGateway();
        AvatarPipelineService service = new AvatarPipelineService(gateway);
        try {
            service.process(new AvatarPipelineService.WorkOrderAvatarRequest(
                    "WO-1043", "tech-8", Path.of("portrait.jpg"),
                    AvatarPipelineService.DispatchStatus.COMPLETED));
            throw new AssertionError("completed work order should be rejected");
        } catch (IllegalArgumentException expected) {
            check(gateway.calls.isEmpty(), "completed work must not call image or auth endpoints");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingGateway implements InfraiAvatarGateway {
        private final List<String> calls = new ArrayList<>();

        public String upload(Path photo, String requestId) {
            calls.add("upload");
            return "uploaded-image";
        }

        public String squareCrop(String image, String requestId) {
            calls.add("crop");
            return "cropped-image";
        }

        public String optimize(String image, String requestId) {
            calls.add("optimize");
            return "optimized-image";
        }

        public void attachToUser(String userId, String image, String requestId) {
            calls.add("attach:" + userId + ":" + image);
        }
    }
}
