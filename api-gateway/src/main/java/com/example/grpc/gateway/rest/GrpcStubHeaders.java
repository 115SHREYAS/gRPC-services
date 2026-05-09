package com.example.grpc.gateway.rest;

import com.example.grpc.gateway.rest.CheckoutApiModels.CustomerRequest;
import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import java.util.UUID;

public final class GrpcStubHeaders {

    private static final Metadata.Key<String> CORRELATION_ID =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> CALLER_SERVICE =
            Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER);

    private GrpcStubHeaders() {
    }

    public static <T extends AbstractStub<T>> T attach(T stub, String correlationId) {
        Metadata metadata = new Metadata();
        metadata.put(CORRELATION_ID, correlationId == null || correlationId.isBlank()
                ? "corr-" + UUID.randomUUID()
                : correlationId);
        metadata.put(CALLER_SERVICE, "api-gateway");
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
    }
}
