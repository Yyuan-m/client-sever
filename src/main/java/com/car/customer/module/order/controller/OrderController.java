package com.car.customer.module.order.controller;

import com.car.customer.common.result.PageResult;
import com.car.customer.common.result.Result;
import com.car.customer.entity.RentalOrder;
import com.car.customer.module.order.dto.CreateOrderDTO;
import com.car.customer.module.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/create")
    public Result<Map<String, Object>> create(@Valid @RequestBody CreateOrderDTO dto) {
        return Result.ok(orderService.createOrder(dto));
    }

    @GetMapping("/list")
    public Result<PageResult<RentalOrder>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(orderService.getOrderList(status, page, pageSize));
    }

    @GetMapping("/detail/{id}")
    public Result<RentalOrder> detail(@PathVariable Long id) {
        return Result.ok(orderService.getOrderDetail(id));
    }

    @PutMapping("/cancel/{id}")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return Result.ok();
    }

    @PutMapping("/pay/{id}")
    public Result<Void> pay(@PathVariable Long id) {
        orderService.payOrder(id);
        return Result.ok();
    }
}
