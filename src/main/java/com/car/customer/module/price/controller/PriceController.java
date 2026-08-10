package com.car.customer.module.price.controller;

import com.car.customer.common.result.Result;
import com.car.customer.module.price.service.PriceService;
import com.car.customer.module.price.vo.PriceDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 价格计算接口（公开访问，确保未登录用户也能在车辆详情页看到准确价格）
 *
 * - POST /api/price/car      单辆车价格计算
 * - POST /api/price/cart     购物车批量价格计算
 */
@RestController
@RequestMapping("/api/price")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;

    /**
     * 单辆车价格计算
     * Body: { "carId": 1, "startDate": "2026-08-10", "endDate": "2026-08-15" }
     */
    @PostMapping("/car")
    public Result<PriceDetailVO> car(@RequestBody Map<String, Object> body) {
        Long carId = Long.valueOf(body.get("carId").toString());
        LocalDate startDate = LocalDate.parse(body.get("startDate").toString());
        LocalDate endDate = LocalDate.parse(body.get("endDate").toString());
        return Result.ok(priceService.calculate(carId, startDate, endDate));
    }

    /**
     * 购物车批量价格计算
     * Body: { "items": [ { "carId": 1, "startDate": "...", "endDate": "..." }, ... ] }
     */
    @PostMapping("/cart")
    public Result<List<PriceDetailVO>> cart(@RequestBody Map<String, Object> body) {
        Object itemsObj = body.get("items");
        if (itemsObj == null) {
            return Result.ok(List.of());
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) itemsObj;
        List<PriceService.CartItem> items = rawItems.stream().map(m -> {
            PriceService.CartItem item = new PriceService.CartItem();
            item.setCarId(Long.valueOf(m.get("carId").toString()));
            item.setStartDate(m.get("startDate").toString());
            item.setEndDate(m.get("endDate").toString());
            return item;
        }).toList();
        return Result.ok(priceService.calculateBatch(items));
    }
}
