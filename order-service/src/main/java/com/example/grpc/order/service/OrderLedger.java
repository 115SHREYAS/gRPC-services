package com.example.grpc.order.service;

import com.example.grpc.contract.ordering.v1.PlaceOrderResponse;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class OrderLedger {

    private final Map<String, PlaceOrderResponse> orders = new ConcurrentHashMap<>();

    public void save(PlaceOrderResponse response) {
        orders.put(response.getOrderId(), response);
    }

    public Optional<PlaceOrderResponse> find(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }
}
