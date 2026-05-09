package com.example.grpc.order.grpc;

import com.example.grpc.contract.analytics.v1.AnalyticsServiceGrpc;
import com.example.grpc.contract.analytics.v1.LifecycleAck;
import com.example.grpc.contract.analytics.v1.LifecycleEvent;
import com.example.grpc.contract.analytics.v1.RecommendationAdvice;
import com.example.grpc.contract.analytics.v1.RecommendationSignal;
import com.example.grpc.contract.common.v1.CartItem;
import com.example.grpc.contract.inventory.v1.InventoryReservationRequest;
import com.example.grpc.contract.inventory.v1.InventoryReservationResponse;
import com.example.grpc.contract.inventory.v1.InventoryServiceGrpc;
import com.example.grpc.contract.inventory.v1.WarehouseAvailabilityRequest;
import com.example.grpc.contract.inventory.v1.WarehouseAvailabilitySnapshot;
import com.example.grpc.contract.ordering.v1.OrderCommandServiceGrpc;
import com.example.grpc.contract.ordering.v1.OrderJourneyEvent;
import com.example.grpc.contract.ordering.v1.OrderJourneyRequest;
import com.example.grpc.contract.ordering.v1.OrderState;
import com.example.grpc.contract.ordering.v1.PlaceOrderRequest;
import com.example.grpc.contract.ordering.v1.PlaceOrderResponse;
import com.example.grpc.contract.ordering.v1.QuoteRequest;
import com.example.grpc.contract.ordering.v1.QuoteResponse;
import com.example.grpc.contract.pricing.v1.PricingRequest;
import com.example.grpc.contract.pricing.v1.PricingResponse;
import com.example.grpc.contract.pricing.v1.PricingServiceGrpc;
import com.example.grpc.order.service.OrderLedger;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.devh.boot.grpc.client.inject.GrpcClient;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class OrderCommandGrpcService extends OrderCommandServiceGrpc.OrderCommandServiceImplBase {

    @GrpcClient("pricing-service")
    private PricingServiceGrpc.PricingServiceBlockingStub pricingStub;

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryBlockingStub;

    @GrpcClient("analytics-service")
    private AnalyticsServiceGrpc.AnalyticsServiceStub analyticsStub;

    private final GrpcStubSupport grpcStubSupport;
    private final OrderLedger orderLedger;

    public OrderCommandGrpcService(GrpcStubSupport grpcStubSupport, OrderLedger orderLedger) {
        this.grpcStubSupport = grpcStubSupport;
        this.orderLedger = orderLedger;
    }

    @Override
    public void createQuote(QuoteRequest request, StreamObserver<QuoteResponse> responseObserver) {
        try {
            PricingResponse pricing = priceCart(request.getCorrelationId(), request.getCustomer(), request.getItemsList(), request.getCurrency());
            List<WarehouseAvailabilitySnapshot> availability = fetchAvailability(request.getCorrelationId(), request.getItemsList());
            List<String> recommendations = requestRecommendations(
                    request.getCorrelationId(),
                    "quote-preview",
                    availability.size() > request.getItemsCount() ? "split" : "single",
                    pricing.getFinalTotal().getUnits(),
                    request.getCustomer().getTier(),
                    "quote-" + pricing.getQuoteId());

            responseObserver.onNext(QuoteResponse.newBuilder()
                    .setQuoteId(pricing.getQuoteId())
                    .setPricing(pricing)
                    .addAllAvailability(availability)
                    .addAllRecommendationMessages(recommendations)
                    .build());
            responseObserver.onCompleted();
        } catch (StatusRuntimeException ex) {
            responseObserver.onError(Status.fromThrowable(ex)
                    .augmentDescription("quote orchestration failed")
                    .asRuntimeException());
        }
    }

    @Override
    public void placeOrder(PlaceOrderRequest request, StreamObserver<PlaceOrderResponse> responseObserver) {
        try {
            String orderId = "ord-" + UUID.randomUUID();
            PricingResponse pricing = priceCart(request.getCorrelationId(), request.getCustomer(), request.getItemsList(), request.getCurrency());
            InventoryReservationResponse reservation = reserveInventory(request.getCorrelationId(), orderId, request.getItemsList());

            String lifecycleSummary = publishLifecycle(request.getCorrelationId(), orderId, reservation.getReservationId());
            List<String> notes = new ArrayList<>(requestRecommendations(
                    request.getCorrelationId(),
                    orderId,
                    "stock-profile",
                    reservation.getLinesCount() > 1 ? "split" : "single",
                    request.getCustomer().getTier()));
            notes.add(lifecycleSummary);

            PlaceOrderResponse response = PlaceOrderResponse.newBuilder()
                    .setOrderId(orderId)
                    .setQuoteId(pricing.getQuoteId())
                    .setReservationId(reservation.getReservationId())
                    .setState(OrderState.ORDER_STATE_COMPLETED)
                    .setPricing(pricing)
                    .setReservation(reservation)
                    .addAllAnalyticsNotes(notes)
                    .build();

            orderLedger.save(response);
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (StatusRuntimeException ex) {
            responseObserver.onError(Status.fromThrowable(ex)
                    .augmentDescription("place-order orchestration failed")
                    .asRuntimeException());
        }
    }

    @Override
    public void watchOrderJourney(OrderJourneyRequest request, StreamObserver<OrderJourneyEvent> responseObserver) {
        PlaceOrderResponse response = orderLedger.find(request.getOrderId())
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("order not found: " + request.getOrderId())
                        .asRuntimeException());

        List<OrderJourneyEvent> events = List.of(
                event(response.getOrderId(), 1, "QUOTED", "Pricing quote " + response.getQuoteId() + " accepted."),
                event(response.getOrderId(), 2, "RESERVED", "Inventory reservation " + response.getReservationId() + " confirmed."),
                event(response.getOrderId(), 3, "ANALYZED", "Analytics captured orchestration signals."),
                event(response.getOrderId(), 4, "COMPLETED", "Order is ready for fulfillment handoff."));

        for (OrderJourneyEvent event : events) {
            responseObserver.onNext(event);
        }
        responseObserver.onCompleted();
    }

    private PricingResponse priceCart(
            String correlationId,
            com.example.grpc.contract.common.v1.Customer customer,
            List<CartItem> items,
            String currency) {
        PricingRequest request = PricingRequest.newBuilder()
                .setCorrelationId(correlationId)
                .setCustomer(customer)
                .addAllItems(items)
                .setCurrency(currency)
                .build();

        return grpcStubSupport
                .attachHeaders(pricingStub, "order-service")
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .priceCart(request);
    }

    private List<WarehouseAvailabilitySnapshot> fetchAvailability(String correlationId, List<CartItem> items) {
        WarehouseAvailabilityRequest request = WarehouseAvailabilityRequest.newBuilder()
                .setCorrelationId(correlationId)
                .addAllItems(items)
                .build();

        Iterator<WarehouseAvailabilitySnapshot> iterator = grpcStubSupport
                .attachHeaders(inventoryBlockingStub, "order-service")
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .streamWarehouseAvailability(request);

        List<WarehouseAvailabilitySnapshot> snapshots = new ArrayList<>();
        iterator.forEachRemaining(snapshots::add);
        return snapshots;
    }

    private InventoryReservationResponse reserveInventory(String correlationId, String orderId, List<CartItem> items) {
        InventoryReservationRequest request = InventoryReservationRequest.newBuilder()
                .setCorrelationId(correlationId)
                .setOrderId(orderId)
                .addAllItems(items)
                .build();

        return grpcStubSupport
                .attachHeaders(inventoryBlockingStub, "order-service")
                .withDeadlineAfter(3, TimeUnit.SECONDS)
                .reserveInventory(request);
    }

    private String publishLifecycle(String correlationId, String orderId, String reservationId) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<LifecycleAck> ackRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        StreamObserver<LifecycleEvent> requestObserver = grpcStubSupport
                .attachHeaders(analyticsStub, "order-service")
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .ingestLifecycle(new StreamObserver<>() {
                    @Override
                    public void onNext(LifecycleAck value) {
                        ackRef.set(value);
                    }

                    @Override
                    public void onError(Throwable t) {
                        errorRef.set(t);
                        latch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                });

        requestObserver.onNext(lifecycleEvent(correlationId, orderId, "received", "REST order accepted at the edge."));
        requestObserver.onNext(lifecycleEvent(correlationId, orderId, "priced", "Pricing quote confirmed for orchestration."));
        requestObserver.onNext(lifecycleEvent(correlationId, orderId, "reserved", "Inventory reserved under " + reservationId + "."));
        requestObserver.onCompleted();

        await(latch);
        if (errorRef.get() != null) {
            throw Status.INTERNAL.withDescription("analytics lifecycle ingestion failed").withCause(errorRef.get()).asRuntimeException();
        }
        return ackRef.get() != null ? ackRef.get().getSummary() : "analytics acknowledgement unavailable";
    }

    private List<String> requestRecommendations(
            String correlationId,
            String orderId,
            String stockSignalType,
            Object stockSignalValue,
            String customerTier) {
        return requestRecommendations(correlationId, stockSignalType, String.valueOf(stockSignalValue), 0L, customerTier, orderId);
    }

    private List<String> requestRecommendations(
            String correlationId,
            String stockSignalType,
            String stockSignalValue,
            long cartTotal,
            String customerTier,
            String orderId) {
        CountDownLatch latch = new CountDownLatch(1);
        CopyOnWriteArrayList<String> notes = new CopyOnWriteArrayList<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        StreamObserver<RecommendationSignal> requestObserver = grpcStubSupport
                .attachHeaders(analyticsStub, "order-service")
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .liveRecommendations(new StreamObserver<>() {
                    @Override
                    public void onNext(RecommendationAdvice value) {
                        notes.add(value.getAdviceCode() + ": " + value.getMessage());
                    }

                    @Override
                    public void onError(Throwable t) {
                        errorRef.set(t);
                        latch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                });

        requestObserver.onNext(recommendationSignal(correlationId, orderId, stockSignalType, stockSignalValue));
        requestObserver.onNext(recommendationSignal(correlationId, orderId, "customer-tier", customerTier));
        if (cartTotal > 0) {
            requestObserver.onNext(recommendationSignal(correlationId, orderId, "cart-total", String.valueOf(cartTotal)));
        }
        requestObserver.onCompleted();

        await(latch);
        if (errorRef.get() != null) {
            notes.add("ANALYTICS_FALLBACK: live recommendation stream was unavailable.");
        }
        return notes;
    }

    private LifecycleEvent lifecycleEvent(String correlationId, String orderId, String stage, String details) {
        return LifecycleEvent.newBuilder()
                .setCorrelationId(correlationId)
                .setOrderId(orderId)
                .setStage(stage)
                .setDetails(details)
                .build();
    }

    private RecommendationSignal recommendationSignal(String correlationId, String orderId, String type, String value) {
        return RecommendationSignal.newBuilder()
                .setCorrelationId(correlationId)
                .setOrderId(orderId)
                .setSignalType(type)
                .setValue(value)
                .build();
    }

    private OrderJourneyEvent event(String orderId, int sequence, String stage, String message) {
        return OrderJourneyEvent.newBuilder()
                .setOrderId(orderId)
                .setStage(stage)
                .setMessage(message)
                .setSequence(sequence)
                .build();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw Status.INTERNAL.withDescription("interrupted while waiting for async gRPC flow").withCause(ex).asRuntimeException();
        }
    }
}
