package com.example.grpc.inventory.grpc;

import com.example.grpc.contract.inventory.v1.InventoryReservationRequest;
import com.example.grpc.contract.inventory.v1.InventoryReservationResponse;
import com.example.grpc.contract.inventory.v1.InventoryServiceGrpc;
import com.example.grpc.contract.inventory.v1.ReservationLine;
import com.example.grpc.contract.inventory.v1.WarehouseAvailabilityRequest;
import com.example.grpc.contract.inventory.v1.WarehouseAvailabilitySnapshot;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private static final Map<String, List<WarehouseRecord>> INVENTORY = Map.of(
            "LAPTOP-15", List.of(
                    new WarehouseRecord("BLR-1", 6, "same-day"),
                    new WarehouseRecord("DEL-2", 3, "next-day")),
            "MOUSE-ERGONOMIC", List.of(
                    new WarehouseRecord("BLR-1", 20, "same-day"),
                    new WarehouseRecord("MUM-3", 12, "same-day")),
            "DOCK-USB-C", List.of(
                    new WarehouseRecord("DEL-2", 9, "next-day"),
                    new WarehouseRecord("HYD-4", 4, "two-day")));

    @Override
    public void streamWarehouseAvailability(
            WarehouseAvailabilityRequest request,
            StreamObserver<WarehouseAvailabilitySnapshot> responseObserver) {
        for (var item : request.getItemsList()) {
            List<WarehouseRecord> records = INVENTORY.getOrDefault(item.getSku(),
                    List.of(new WarehouseRecord("BACKORDER", 0, "five-day")));
            for (WarehouseRecord record : records) {
                responseObserver.onNext(WarehouseAvailabilitySnapshot.newBuilder()
                        .setWarehouseCode(record.code())
                        .setSku(item.getSku())
                        .setAvailableQuantity(record.available())
                        .setSuggestedReserveQuantity(Math.min(item.getQuantity(), record.available()))
                        .setEta(record.eta())
                        .build());
            }
        }
        responseObserver.onCompleted();
    }

    @Override
    public void reserveInventory(
            InventoryReservationRequest request,
            StreamObserver<InventoryReservationResponse> responseObserver) {
        InventoryReservationResponse.Builder response = InventoryReservationResponse.newBuilder()
                .setReservationId("res-" + UUID.randomUUID())
                .setFulfilled(true)
                .setStatusMessage("inventory reserved across best-fit warehouses");

        for (var item : request.getItemsList()) {
            WarehouseRecord preferred = INVENTORY.getOrDefault(item.getSku(), List.of()).stream()
                    .findFirst()
                    .orElse(null);
            if (preferred == null || preferred.available() < item.getQuantity()) {
                responseObserver.onError(Status.FAILED_PRECONDITION
                        .withDescription("Insufficient stock for sku " + item.getSku())
                        .asRuntimeException());
                return;
            }
            response.addLines(ReservationLine.newBuilder()
                    .setSku(item.getSku())
                    .setRequestedQuantity(item.getQuantity())
                    .setReservedQuantity(item.getQuantity())
                    .setWarehouseCode(preferred.code())
                    .build());
        }

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    private record WarehouseRecord(String code, int available, String eta) {
    }
}
