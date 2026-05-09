package com.example.grpc.gateway.rest;

import java.util.List;

public final class CheckoutApiModels {

    private CheckoutApiModels() {
    }

    public record CustomerRequest(String customerId, String email, String tier) {
    }

    public record CheckoutItemRequest(String sku, String title, int quantity) {
    }

    public record AddressRequest(
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String countryCode) {
    }

    public record QuoteRequest(CustomerRequest customer, List<CheckoutItemRequest> items, String currency) {
    }

    public record PlaceOrderRequest(
            CustomerRequest customer,
            List<CheckoutItemRequest> items,
            AddressRequest shippingAddress,
            String currency) {
    }
}
