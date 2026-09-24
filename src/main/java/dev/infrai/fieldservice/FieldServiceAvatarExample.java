package dev.infrai.fieldservice;

import java.nio.file.Path;

public final class FieldServiceAvatarExample {
    private FieldServiceAvatarExample() {}

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: scripts/run.sh <work-order-id> <user-id> <photo-path>");
            System.exit(2);
        }

        InfraiConfig config = InfraiConfig.fromEnvironment();
        AvatarPipelineService service = new AvatarPipelineService(new InfraiRestClient(config));
        try {
            AvatarPipelineService.AvatarUpdate update = service.process(
                    new AvatarPipelineService.WorkOrderAvatarRequest(
                            args[0], args[1], Path.of(args[2]),
                            AvatarPipelineService.DispatchStatus.EN_ROUTE));
            System.out.printf(
                    "Work order %s: avatar %s attached to user %s; dispatch status=%s%n",
                    update.workOrderId(), update.avatarImage(), update.userId(), update.dispatchStatus());
        } catch (InfraiException error) {
            System.err.printf("Request rejected (%s, HTTP %d): %s%n",
                    error.code(), error.statusCode(), error.getMessage());
            System.exit(error.statusCode() >= 400 && error.statusCode() < 500 ? 2 : 1);
        }
    }
}
