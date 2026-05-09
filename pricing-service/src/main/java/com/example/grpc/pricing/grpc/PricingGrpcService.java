package com.example.grpc.pricing.grpc;

import com.example.grpc.contract.common.v1.Money;
import com.example.grpc.contract.pricing.v1.LineQuote;
import com.example.grpc.contract.pricing.v1.PricingRequest;
import com.example.grpc.contract.pricing.v1.PricingResponse;
import com.example.grpc.contract.pricing.v1.PricingServiceGrpc;
import io.grpc.stub.StreamObserver;
import java.util.UUID;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class PricingGrpcService extends PricingServiceGrpc.PricingServiceImplBase {

    @Override
    public void priceCart(PricingRequest request, StreamObserver<PricingResponse> responseObserver) {
        long subtotalUnits = 0L;
        PricingResponse.Builder response = PricingResponse.newBuilder()
                .setQuoteId("quote-" + UUID.randomUUID())
                .setPricingStrategy(resolveStrategy(request.getCustomer().getTier()));

        for (var item : request.getItemsList()) {
            long unitPrice = switch (item.getSku()) {
                case "LAPTOP-15" -> 1200;
                case "MOUSE-ERGONOMIC" -> 80;
                case "DOCK-USB-C" -> 160;
                default -> 50;
            };
            long lineUnits = unitPrice * item.getQuantity();
            subtotalUnits += lineUnits;
            response.addLines(LineQuote.newBuilder()
                    .setSku(item.getSku())
                    .setQuantity(item.getQuantity())
                    .setAppliedRule(resolveRule(request.getCustomer().getTier(), item.getQuantity()))
                    .setUnitPrice(money(request.getCurrency(), unitPrice))
                    .setLineTotal(money(request.getCurrency(), lineUnits))
                    .build());
        }

        long discountUnits = request.getCustomer().getTier().equalsIgnoreCase("GOLD")
                ? Math.round(subtotalUnits * 0.12)
                : Math.round(subtotalUnits * 0.05);

        response.setSubtotal(money(request.getCurrency(), subtotalUnits));
        response.setDiscount(money(request.getCurrency(), discountUnits));
        response.setFinalTotal(money(request.getCurrency(), subtotalUnits - discountUnits));

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private String resolveStrategy(String tier) {
        return tier.equalsIgnoreCase("GOLD") ? "tiered-loyalty-pricing" : "standard-commerce-pricing";
    }

    private String resolveRule(String tier, int quantity) {
        if (tier.equalsIgnoreCase("GOLD")) {
            return "gold-customer-discount";
        }
        return quantity >= 3 ? "multi-buy-discount" : "base-rate";
    }

    private Money money(String currency, long units) {
        return Money.newBuilder()
                .setCurrency(currency)
                .setUnits(units)
                .setNanos(0)
                .build();
    }
}
