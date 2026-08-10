package com.car.customer.module.coupon.controller;

import com.car.customer.common.result.Result;
import com.car.customer.entity.Coupon;
import com.car.customer.entity.MemberCoupon;
import com.car.customer.module.coupon.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 优惠券 C 端接口（v2 企业级）
 * 路径前缀 /api/coupon，对接前端 customer-client
 *
 * 接口列表：
 *   GET  /available         可领券列表（公开）
 *   GET  /{id}              券详情（公开）
 *   GET  /mine              我的券（鉴权，可按状态筛选）
 *   GET  /usable            下单可用券（鉴权）
 *   POST /receive/{couponId} 领取（鉴权）
 *   POST /lock              锁定（鉴权，下单预占）
 *   POST /cancel-lock       取消锁定（鉴权）
 *   POST /verify            核销（鉴权，订单支付完成时调用）
 *   POST /calculate         计算优惠金额（鉴权）
 *   GET  /claimed-ids       已领取ID列表（鉴权，首页按钮状态）
 */
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /** 可领券列表（公开访问，首页领券中心） */
    @GetMapping("/available")
    public Result<List<Coupon>> listAvailable() {
        return Result.ok(couponService.listAvailable());
    }

    /** 券详情（公开访问） */
    @GetMapping("/{id}")
    public Result<Coupon> detail(@PathVariable Long id) {
        return Result.ok(couponService.getCouponDetail(id));
    }

    /** 我的券（可按状态筛选：unused/locked/used/expired） */
    @GetMapping("/mine")
    public Result<List<MemberCoupon>> mine(@RequestParam(required = false) String status) {
        return Result.ok(couponService.listMine(status));
    }

    /** 下单可用券（一辆车下可用多张，但下单只能选一张，由前端限制） */
    @GetMapping("/usable")
    public Result<List<MemberCoupon>> usable(
            @RequestParam(required = false) Long carId,
            @RequestParam(required = false) BigDecimal amount) {
        return Result.ok(couponService.listUsable(carId, amount));
    }

    /** 领取优惠券，返回领取后的 member_coupon.id */
    @PostMapping("/receive/{couponId}")
    public Result<Long> receive(@PathVariable Long couponId, @RequestBody(required = false) Map<String, Object> body) {
        String source = body == null ? "manual" : (body.get("source") == null ? "manual" : body.get("source").toString());
        return Result.ok(couponService.receive(couponId, source));
    }

    /** 锁定优惠券（下单预占） */
    @PostMapping("/lock")
    public Result<Void> lock(@RequestBody Map<String, Object> body) {
        Long memberCouponId = Long.valueOf(body.get("memberCouponId").toString());
        couponService.lock(memberCouponId);
        return Result.ok();
    }

    /** 取消锁定 */
    @PostMapping("/cancel-lock")
    public Result<Void> cancelLock(@RequestBody Map<String, Object> body) {
        Long memberCouponId = Long.valueOf(body.get("memberCouponId").toString());
        couponService.cancelLock(memberCouponId);
        return Result.ok();
    }

    /** 核销（订单完成时调用，幂等） */
    @PostMapping("/verify")
    public Result<Void> verify(@RequestBody Map<String, Object> body) {
        Long memberCouponId = Long.valueOf(body.get("memberCouponId").toString());
        Long orderId = body.get("orderId") == null ? null : Long.valueOf(body.get("orderId").toString());
        couponService.verify(memberCouponId, orderId);
        return Result.ok();
    }

    /** 计算优惠金额（下单预览用，不实际核销） */
    @PostMapping("/calculate")
    public Result<BigDecimal> calculate(@RequestBody Map<String, Object> body) {
        Long couponId = Long.valueOf(body.get("couponId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        return Result.ok(couponService.calculateDiscount(couponId, amount));
    }

    /**
     * 当前用户已领取的优惠券ID列表（含已使用+未使用）
     * 用于首页判断"立即领取/已领取"按钮状态
     */
    @GetMapping("/claimed-ids")
    public Result<List<Long>> claimedIds() {
        return Result.ok(couponService.getMyClaimedCouponIds());
    }
}
