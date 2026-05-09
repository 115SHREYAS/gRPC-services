package com.example.grpc.gateway.rest;

import com.example.grpc.contract.common.v1.Address;
import com.example.grpc.contract.common.v1.CartItem;
import com.example.grpc.contract.common.v1.Customer;
import com.example.grpc.contract.ordering.v1.OrderCommandServiceGrpc;
import com.example.grpc.contract.ordering.v1.OrderJourneyRequest;
import com.example.grpc.contract.ordering.v1.PlaceOrderRequest;
import com.example.grpc.contract.ordering.v1.PlaceOrderResponse;
import com.example.grpc.contract.ordering.v1.QuoteRequest;
import com.example.grpc.contract.ordering.v1.QuoteResponse;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
public class CheckoutController {

    @GrpcClient("order-service")
    private OrderCommandServiceGrpc.OrderCommandServiceBlockingStub orderStub;

    @PostMapping("/quote")
    public ResponseEntity<Map<String, Object>> quote(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody CheckoutApiModels.QuoteRequest request) {
        QuoteResponse response = GrpcStubHeaders.attach(orderStub, correlationId)
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .createQuote(QuoteRequest.newBuilder()
                        .setCorrelationId(correlationId == null ? "" : correlationId)
                        .setCustomer(customer(request.customer()))
                        .addAllItems(items(request.items()))
                        .setCurrency(request.currency())
                        .build());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("quoteId", response.getQuoteId());
        body.put("pricingStrategy", response.getPricing().getPricingStrategy());
        body.put("subtotal", response.getPricing().getSubtotal().getUnits());
        body.put("discount", response.getPricing().getDiscount().getUnits());
        body.put("finalTotal", response.getPricing().getFinalTotal().getUnits());
        body.put("availability", response.getAvailabilityList().stream()
                .map(snapshot -> Map.of(
                        "warehouse", snapshot.getWarehouseCode(),
                        "sku", snapshot.getSku(),
                        "available", snapshot.getAvailableQuantity(),
                        "suggestedReserve", snapshot.getSuggestedReserveQuantity(),
                        "eta", snapshot.getEta()))
                .toList());
        body.put("recommendations", response.getRecommendationMessagesList());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> placeOrder(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody CheckoutApiModels.PlaceOrderRequest request) {
        PlaceOrderResponse response = GrpcStubHeaders.attach(orderStub, correlationId)
                .withDeadlineAfter(6, TimeUnit.SECONDS)
                .placeOrder(PlaceOrderRequest.newBuilder()
                        .setCorrelationId(correlationId == null ? "" : correlationId)
                        .setCustomer(customer(request.customer()))
                        .addAllItems(items(request.items()))
                        .setShippingAddress(address(request.shippingAddress()))
                        .setCurrency(request.currency())
                        .build());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", response.getOrderId());
        body.put("quoteId", response.getQuoteId());
        body.put("reservationId", response.getReservationId());
        body.put("state", response.getState().name());
        body.put("finalTotal", response.getPricing().getFinalTotal().getUnits());
        body.put("reservedLines", response.getReservation().getLinesList().stream()
                .map(line -> Map.of(
                        "sku", line.getSku(),
                        "requested", line.getRequestedQuantity(),
                        "reserved", line.getReservedQuantity(),
                        "warehouse", line.getWarehouseCode()))
                .toList());
        body.put("analyticsNotes", response.getAnalyticsNotesList());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/orders/{orderId}/journey")
    public ResponseEntity<List<Map<String, Object>>> watchJourney(
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @PathVariable String orderId) {
        Iterator<com.example.grpc.contract.ordering.v1.OrderJourneyEvent> iterator = GrpcStubHeaders.attach(orderStub, correlationId)
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .watchOrderJourney(OrderJourneyRequest.newBuilder()
                        .setCorrelationId(correlationId == null ? "" : correlationId)
                        .setOrderId(orderId)
                        .build());

        List<Map<String, Object>> events = new ArrayList<>();
        iterator.forEachRemaining(event -> events.add(Map.of(
                "sequence", event.getSequence(),
                "stage", event.getStage(),
                "message", event.getMessage())));
        return ResponseEntity.ok(events);
    }

    private Customer customer(CheckoutApiModels.CustomerRequest customer) {
        return Customer.newBuilder()
                .setCustomerId(customer.customerId())
                .setEmail(customer.email())
                .setTier(customer.tier())
                .build();
    }

    private List<CartItem> items(List<CheckoutApiModels.CheckoutItemRequest> items) {
        return items.stream()
                .map(item -> CartItem.newBuilder()
                        .setSku(item.sku())
                        .setTitle(item.title())
                        .setQuantity(item.quantity())
                        .build())
                .toList();
    }

    private Address address(CheckoutApiModels.AddressRequest address) {
        return Address.newBuilder()
                .setLine1(address.line1())
                .setLine2(address.line2() == null ? "" : address.line2())
                .setCity(address.city())
                .setState(address.state())
                .setPostalCode(address.postalCode())
                .setCountryCode(address.countryCode())
                .build();
    }
}
