package com.example.grpc.order.grpc;

import io.grpc.Context;
import io.grpc.Metadata;

public final class GrpcTraceContext {

    public static final Metadata.Key<String> CORRELATION_ID =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> CALLER_SERVICE =
            Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> CORRELATION_CONTEXT = Context.key("correlation-id");
    public static final Context.Key<String> CALLER_CONTEXT = Context.key("caller-service");

    private GrpcTraceContext() {
    }
}
