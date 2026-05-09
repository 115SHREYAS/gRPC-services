package com.example.grpc.pricing.grpc;

import io.grpc.Context;

public final class GrpcTraceContext {

    public static final Context.Key<String> CORRELATION_ID = Context.key("correlation-id");
    public static final Context.Key<String> CALLER = Context.key("caller-service");

    private GrpcTraceContext() {
    }
}
