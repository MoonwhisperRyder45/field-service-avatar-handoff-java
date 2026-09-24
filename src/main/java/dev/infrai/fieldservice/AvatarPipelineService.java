package dev.infrai.fieldservice;

import java.nio.file.Path;
import java.util.Objects;

public final class AvatarPipelineService {
    public enum DispatchStatus { DISPATCHED, EN_ROUTE, TECHNICIAN_FOLLOW_UP, COMPLETED }

    public record WorkOrderAvatarRequest(
            String workOrderId,
            String userId,
            Path technicianPhoto,
            DispatchStatus dispatchStatus) {
        public WorkOrderAvatarRequest {
            Objects.requireNonNull(workOrderId);
            Objects.requireNonNull(userId);
            Objects.requireNonNull(technicianPhoto);
            Objects.requireNonNull(dispatchStatus);
        }
    }

    public record AvatarUpdate(
            String workOrderId,
            String userId,
            String avatarImage,
            DispatchStatus dispatchStatus) {}

    private final InfraiAvatarGateway gateway;

    public AvatarPipelineService(InfraiAvatarGateway gateway) {
        this.gateway = Objects.requireNonNull(gateway);
    }

    public AvatarUpdate process(WorkOrderAvatarRequest request) {
        if (request.dispatchStatus() != DispatchStatus.DISPATCHED
                && request.dispatchStatus() != DispatchStatus.EN_ROUTE) {
            throw new IllegalArgumentException(
                    "Avatar updates require a dispatched or en-route work order");
        }

        String requestId = "avatar-" + request.workOrderId();
        String uploaded = gateway.upload(request.technicianPhoto(), requestId + "-upload");
        String cropped = gateway.squareCrop(uploaded, requestId + "-crop");
        String optimized = gateway.optimize(cropped, requestId + "-optimize");
        gateway.attachToUser(request.userId(), optimized, requestId + "-user");

        return new AvatarUpdate(
                request.workOrderId(),
                request.userId(),
                optimized,
                DispatchStatus.TECHNICIAN_FOLLOW_UP);
    }
}
