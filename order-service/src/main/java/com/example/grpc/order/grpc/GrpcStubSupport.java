package com.example.grpc.order.grpc;

import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class GrpcStubSupport {

    public <T extends AbstractStub<T>> T attachHeaders(T stub, String callerService) {
        io.grpc.Metadata headers = new io.grpc.Metadata();
        headers.put(GrpcTraceContext.CORRELATION_ID, correlationId());
        headers.put(GrpcTraceContext.CALLER_SERVICE, callerService);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    public String correlationId() {
        String existing = GrpcTraceContext.CORRELATION_CONTEXT.get();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        return "corr-" + UUID.randomUUID();
    }
}
