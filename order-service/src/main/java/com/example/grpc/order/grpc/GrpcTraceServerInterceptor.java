package com.example.grpc.order.grpc;

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

    private static final Logger log = LoggerFactory.getLogger(GrpcTraceServerInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String correlationId = headers.get(GrpcTraceContext.CORRELATION_ID);
        String caller = headers.get(GrpcTraceContext.CALLER_SERVICE);
        log.info("order-service gRPC method={} correlationId={} caller={}",
                call.getMethodDescriptor().getFullMethodName(),
                correlationId,
                caller);
        Context context = Context.current()
                .withValue(GrpcTraceContext.CORRELATION_CONTEXT, correlationId)
                .withValue(GrpcTraceContext.CALLER_CONTEXT, caller);
        return Contexts.interceptCall(context, call, headers, next);
    }
}
