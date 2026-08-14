package com.car.customer.module.review.controller;

import com.car.customer.common.result.Result;
import com.car.customer.entity.Review;
import com.car.customer.module.review.dto.ReviewSubmitDTO;
import com.car.customer.module.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评价接口（需登录）
 * 每单最多2次：首评（订单完成后）+ 追评（首评后）
 */
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * 提交评价（首评/追评，由订单 reviewStatus 自动判定轮次）
     */
    @PostMapping("/submit")
    public Result<Review> submit(@Valid @RequestBody ReviewSubmitDTO dto) {
        return Result.ok(reviewService.submitReview(dto));
    }

    /**
     * 查询订单评价列表（首评 + 追评，按轮次升序）
     */
    @GetMapping("/order/{orderId}")
    public Result<List<Review>> orderReviews(@PathVariable Long orderId) {
        return Result.ok(reviewService.getOrderReviews(orderId));
    }

    /**
     * 查询订单可评价轮次
     * 返回 { canReviewRound: 1=可首评, 2=可追评, null=不可评价 }
     * 使用 HashMap 而非 Map.of 以支持 null 值（Map.of 不允许 null value）
     */
    @GetMapping("/can-review/{orderId}")
    public Result<Map<String, Object>> canReview(@PathVariable Long orderId) {
        Integer round = reviewService.getCanReviewRound(orderId);
        Map<String, Object> result = new HashMap<>();
        result.put("canReviewRound", round);
        return Result.ok(result);
    }
}
