package com.example.grpc.analytics.service;

import com.example.grpc.contract.analytics.v1.LifecycleEvent;
import com.example.grpc.contract.analytics.v1.RecommendationAdvice;
import com.example.grpc.contract.analytics.v1.RecommendationSignal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;

@Service
public class InMemoryAnalyticsStore {

    private final Map<String, List<LifecycleEvent>> lifecycleByOrder = new ConcurrentHashMap<>();

    public int recordLifecycleEvents(List<LifecycleEvent> events) {
        for (LifecycleEvent event : events) {
            lifecycleByOrder
                    .computeIfAbsent(event.getOrderId(), ignored -> new CopyOnWriteArrayList<>())
                    .add(event);
        }
        return events.size();
    }

    public List<LifecycleEvent> findEvents(String orderId) {
        return new ArrayList<>(lifecycleByOrder.getOrDefault(orderId, List.of()));
    }

    public RecommendationAdvice advise(RecommendationSignal signal) {
        String adviceCode = "NORMAL_FLOW";
        String message = "Continue with the default orchestration path.";
        String severity = "INFO";

        if ("cart-total".equals(signal.getSignalType())) {
            double total = Double.parseDouble(signal.getValue());
            if (total > 1500) {
                adviceCode = "VIP_REVIEW";
                message = "High-value order detected. Notify concierge support.";
                severity = "MEDIUM";
            }
        }

        if ("stock-profile".equals(signal.getSignalType()) && signal.getValue().contains("split")) {
            adviceCode = "SPLIT_SHIPMENT";
            message = "Inventory spans multiple warehouses. Prepare split shipment messaging.";
            severity = "HIGH";
        }

        if ("customer-tier".equals(signal.getSignalType()) && "GOLD".equalsIgnoreCase(signal.getValue())) {
            adviceCode = "LOYALTY_TOUCHPOINT";
            message = "Offer premium delivery messaging for GOLD customer.";
            severity = "INFO";
        }

        return RecommendationAdvice.newBuilder()
                .setOrderId(signal.getOrderId())
                .setAdviceCode(adviceCode)
                .setMessage(message)
                .setSeverity(severity)
                .build();
    }
}
