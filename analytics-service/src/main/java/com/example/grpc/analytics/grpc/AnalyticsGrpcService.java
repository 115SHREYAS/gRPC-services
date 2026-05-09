package com.example.grpc.analytics.grpc;

import com.example.grpc.analytics.service.InMemoryAnalyticsStore;
import com.example.grpc.contract.analytics.v1.AnalyticsServiceGrpc;
import com.example.grpc.contract.analytics.v1.LifecycleAck;
import com.example.grpc.contract.analytics.v1.LifecycleEvent;
import com.example.grpc.contract.analytics.v1.RecommendationAdvice;
import com.example.grpc.contract.analytics.v1.RecommendationSignal;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class AnalyticsGrpcService extends AnalyticsServiceGrpc.AnalyticsServiceImplBase {

    private final InMemoryAnalyticsStore analyticsStore;

    public AnalyticsGrpcService(InMemoryAnalyticsStore analyticsStore) {
        this.analyticsStore = analyticsStore;
    }

    @Override
    public StreamObserver<LifecycleEvent> ingestLifecycle(StreamObserver<LifecycleAck> responseObserver) {
        return new StreamObserver<>() {
            private final List<LifecycleEvent> events = new ArrayList<>();

            @Override
            public void onNext(LifecycleEvent value) {
                events.add(value);
            }

            @Override
            public void onError(Throwable t) {
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                int acceptedCount = analyticsStore.recordLifecycleEvents(events);
                responseObserver.onNext(LifecycleAck.newBuilder()
                        .setIngestionId("ingest-" + UUID.randomUUID())
                        .setAcceptedCount(acceptedCount)
                        .setSummary("captured " + acceptedCount + " lifecycle events")
                        .build());
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<RecommendationSignal> liveRecommendations(
            StreamObserver<RecommendationAdvice> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(RecommendationSignal value) {
                responseObserver.onNext(analyticsStore.advise(value));
            }

            @Override
            public void onError(Throwable t) {
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
