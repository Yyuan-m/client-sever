package com.car.customer.module.order.controller;

import com.car.customer.common.result.PageResult;
import com.car.customer.common.result.Result;
import com.car.customer.entity.RentalOrder;
import com.car.customer.module.order.dto.CreateOrderDTO;
import com.car.customer.module.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    /**
     * 确认还车：renting → completed，并置评价状态为待评价
     * 用户在订单详情页主动点击"确认还车"触发（到期也会自动完成，此为提前还车入口）
     */
    @PutMapping("/complete/{id}")
    public Result<Void> complete(@PathVariable Long id) {
        orderService.completeOrder(id);
        return Result.ok();
    }

    /**
     * 我的进行中订单（首页"我的订单"模块用）
     * 返回租赁中 + 待评价订单（status=renting 或 reviewStatus=unreviewed），按创建时间倒序，最多 limit 条
     */
    @GetMapping("/active")
    public Result<List<RentalOrder>> active(@RequestParam(defaultValue = "6") Integer limit) {
        return Result.ok(orderService.getMyActiveOrders(limit));
    }

    /**
     * 可评价订单列表（个人中心"去评价"入口用）
     * 返回 reviewStatus=unreviewed（可首评）或 reviewed（可追评）的已完成订单
     */
    @GetMapping("/reviewable")
    public Result<List<RentalOrder>> reviewable() {
        return Result.ok(orderService.getReviewableOrders());
    }
}
