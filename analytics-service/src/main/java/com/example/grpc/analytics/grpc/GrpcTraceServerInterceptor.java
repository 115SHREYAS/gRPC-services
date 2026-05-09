package com.example.grpc.analytics.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@GrpcGlobalServerInterceptor
public class GrpcTraceServerInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> CORRELATION_ID =
            Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> CALLER_SERVICE =
            Metadata.Key.of("x-caller-service", Metadata.ASCII_STRING_MARSHALLER);

    private static final Logger log = LoggerFactory.getLogger(GrpcTraceServerInterceptor.class);
    public static final Context.Key<String> CORRELATION_CONTEXT = Context.key("correlation-id");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String correlationId = headers.get(CORRELATION_ID);
        log.info("analytics-service gRPC method={} correlationId={} caller={}",
                call.getMethodDescriptor().getFullMethodName(),
                correlationId,
                headers.get(CALLER_SERVICE));
        Context context = Context.current().withValue(CORRELATION_CONTEXT, correlationId);
        return Contexts.interceptCall(context, call, headers, next);
    }
}
