package dev.swirlit.indezy.controller;

import dev.swirlit.indezy.dto.PurchaseOrderDtos;
import dev.swirlit.indezy.service.AccessGuard;
import dev.swirlit.indezy.service.PurchaseOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** A mission's purchase orders and amendments, with what its activity reports used of them. Another workspace's get 404. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Purchase orders", description = "Purchase orders and amendments per mission, with their consumption")
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrders;
    private final AccessGuard accessGuard;

    @Operation(summary = "A mission's orders, each with its amendments and usage")
    @GetMapping("/projects/{projectId}/purchase-orders")
    public List<PurchaseOrderDtos.Order> list(@PathVariable Long projectId) {
        return purchaseOrders.list(workspace(), projectId);
    }

    @Operation(summary = "Write an order, or an amendment to one (amendsId)")
    @PostMapping("/projects/{projectId}/purchase-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderDtos.Order create(@PathVariable Long projectId, @Valid @RequestBody PurchaseOrderDtos.Write write) {
        return purchaseOrders.create(workspace(), projectId, write);
    }

    @Operation(summary = "Change an order or an amendment")
    @PutMapping("/purchase-orders/{id}")
    public PurchaseOrderDtos.Order update(@PathVariable Long id, @Valid @RequestBody PurchaseOrderDtos.Write write) {
        return purchaseOrders.update(workspace(), id, write);
    }

    @Operation(summary = "Delete an amendment, or an order with its amendments")
    @DeleteMapping("/purchase-orders/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        purchaseOrders.delete(workspace(), id);
    }

    private Long workspace() {
        return accessGuard.requireWorkspace(null);
    }
}
