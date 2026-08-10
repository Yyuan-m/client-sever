package com.car.customer.module.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.entity.Coupon;
import com.car.customer.entity.MemberCoupon;
import com.car.customer.mapper.CouponMapper;
import com.car.customer.mapper.MemberCouponMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 优惠券服务（v2 企业级）
 * 涉及 car_rental.coupon（券模板）+ car_rental.coupon_car（券车关联）+ member_coupon（领取实例）跨库操作
 *
 * 状态流转：
 *   领取：coupon.received_count+1（原子），写入 member_coupon(unused)
 *   下单：unused → locked（带乐观锁）
 *   取消：locked → unused（带乐观锁）
 *   核销：locked → used（带乐观锁，回写 order_id，coupon.used_count+1）
 *   过期：unused → expired（懒标记，查询时触发）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponMapper couponMapper;
    private final MemberCouponMapper memberCouponMapper;

    /**
     * 可领券列表（已投放 + 有效期内 + 有库存）
     * 未登录用户也可访问（首页领券中心公开展示）
     */
    public List<Coupon> listAvailable() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Coupon> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Coupon::getStatus, "published")
                .eq(Coupon::getPublished, 1)
                .le(Coupon::getValidStartTime, now)
                .gt(Coupon::getValidEndTime, now)
                .orderByDesc(Coupon::getCreatedAt);
        List<Coupon> list = couponMapper.selectList(wrapper);
        // 过滤库存不足的，并填充剩余库存
        List<Coupon> result = list.stream()
                .filter(c -> c.getTotalCount() == -1
                        || c.getReceivedCount() == null
                        || c.getReceivedCount() < c.getTotalCount())
                .collect(Collectors.toList());
        result.forEach(this::fillRemainCount);
        // 登录用户标记已领取状态（通过 claimedIds 复用）
        return result;
    }

    /**
     * 券详情（含关联车辆）
     */
    public Coupon getCouponDetail(Long id) {
        Coupon coupon = couponMapper.selectById(id);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        coupon.setCarIds(couponMapper.selectCarIdsByCouponId(id));
        coupon.setCarNames(couponMapper.selectCarNamesByCouponId(id));
        fillRemainCount(coupon);
        return coupon;
    }

    /**
     * 我的券（可按状态筛选，自动标记过期）
     * 状态：unused/locked/used/expired
     */
    public List<MemberCoupon> listMine(String status) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        List<MemberCoupon> all = memberCouponMapper.selectMyCoupons(memberId);
        LocalDateTime now = LocalDateTime.now();
        // 懒标记过期：unused 且 expire_time < now → expired（仅内存标记，不写库，避免高频 update）
        for (MemberCoupon mc : all) {
            if ("unused".equals(mc.getStatus())
                    && mc.getExpireTime() != null
                    && mc.getExpireTime().isBefore(now)) {
                mc.setStatus("expired");
            }
        }
        if (status == null || status.isBlank()) {
            return all;
        }
        List<MemberCoupon> result = new ArrayList<>();
        for (MemberCoupon mc : all) {
            if (status.equals(mc.getStatus())) {
                result.add(mc);
            }
        }
        return result;
    }

    /**
     * 下单可用券（某用户在某车某金额下可用的未使用券）
     * 校验：状态 unused + 未过期 + 满足门槛 + 指定车辆券需校验 carId
     */
    public List<MemberCoupon> listUsable(Long carId, BigDecimal amount) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        List<MemberCoupon> mine = memberCouponMapper.selectMyCoupons(memberId);
        LocalDateTime now = LocalDateTime.now();
        List<MemberCoupon> usable = new ArrayList<>();
        for (MemberCoupon mc : mine) {
            if (!"unused".equals(mc.getStatus())) continue;
            if (mc.getExpireTime() != null && mc.getExpireTime().isBefore(now)) continue;
            if (mc.getMinAmount() != null && amount != null && amount.compareTo(mc.getMinAmount()) < 0) continue;
            // 指定车辆券需校验 carId
            if ("specified".equals(mc.getApplyScope())) {
                List<Long> carIds = couponMapper.selectCarIdsByCouponId(mc.getCouponId());
                if (carId == null || !carIds.contains(carId)) continue;
            }
            usable.add(mc);
        }
        return usable;
    }

    /**
     * 领取优惠券（原子扣库存 + 写 member_coupon）
     * @return 领取后的 member_coupon.id
     */
    @Transactional
    public Long receive(Long couponId, String source) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        if (!"published".equals(coupon.getStatus()) || coupon.getPublished() == null || coupon.getPublished() != 1) {
            throw new BusinessException("优惠券未投放，不可领取");
        }
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidStartTime() != null && coupon.getValidStartTime().isAfter(now)) {
            throw new BusinessException("优惠券尚未生效");
        }
        if (coupon.getValidEndTime() != null && coupon.getValidEndTime().isBefore(now)) {
            throw new BusinessException("优惠券已过期");
        }
        // 库存校验
        if (coupon.getTotalCount() != -1 && coupon.getReceivedCount() != null
                && coupon.getReceivedCount() >= coupon.getTotalCount()) {
            throw new BusinessException("优惠券已领完");
        }
        // 每人限领
        int perUserLimit = coupon.getPerUserLimit() == null ? 1 : coupon.getPerUserLimit();
        int received = memberCouponMapper.countReceivedByUser(memberId, couponId);
        if (received >= perUserLimit) {
            throw new BusinessException("已超过每人限领数量（" + perUserLimit + " 张）");
        }
        // 原子扣减库存
        int affected = couponMapper.incrReceivedCount(couponId);
        if (affected == 0) {
            throw new BusinessException("优惠券已被抢空");
        }
        // 写 member_coupon
        try {
            MemberCoupon mc = new MemberCoupon();
            mc.setMemberId(memberId);
            mc.setCouponId(couponId);
            mc.setStatus("unused");
            mc.setCode(generateCode());
            mc.setClaimTime(now);
            mc.setExpireTime(coupon.getValidEndTime());
            mc.setSource(source == null ? "manual" : source);
            mc.setVersion(0);
            memberCouponMapper.insert(mc);
            return mc.getId();
        } catch (Exception e) {
            // 回滚库存
            couponMapper.decrReceivedCount(couponId);
            log.error("领取优惠券失败，已回滚库存 couponId={}, memberId={}", couponId, memberId, e);
            throw new BusinessException("领取失败，请重试");
        }
    }

    /**
     * 锁定优惠券（下单预占，unused → locked）
     * 用于下单流程：用户提交订单时预占优惠券，避免并发下单导致重复使用
     */
    @Transactional
    public void lock(Long memberCouponId) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        MemberCoupon mc = getAndCheckOwnership(memberCouponId, memberId);
        if (!"unused".equals(mc.getStatus())) {
            throw new BusinessException("仅未使用的券可锁定，当前状态: " + mc.getStatus());
        }
        if (mc.getExpireTime() != null && mc.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("优惠券已过期");
        }
        int affected = memberCouponMapper.updateStatusWithVersion(memberCouponId, "unused", "locked", mc.getVersion());
        if (affected == 0) {
            throw new BusinessException("锁定失败，券状态已变更或已被使用");
        }
    }

    /**
     * 取消锁定（订单创建失败/取消时，locked → unused）
     */
    @Transactional
    public void cancelLock(Long memberCouponId) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        MemberCoupon mc = getAndCheckOwnership(memberCouponId, memberId);
        if (!"locked".equals(mc.getStatus())) {
            throw new BusinessException("仅锁定中的券可取消锁定，当前状态: " + mc.getStatus());
        }
        int affected = memberCouponMapper.updateStatusWithVersion(memberCouponId, "locked", "unused", mc.getVersion());
        if (affected == 0) {
            throw new BusinessException("取消锁定失败，券状态已变更");
        }
    }

    /**
     * 核销（订单完成/支付时，locked → used，回写 order_id，coupon.used_count++）
     * 幂等：已核销直接返回
     */
    @Transactional
    public void verify(Long memberCouponId, Long orderId) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        MemberCoupon mc = getAndCheckOwnership(memberCouponId, memberId);
        if ("used".equals(mc.getStatus())) {
            // 幂等：已核销直接返回
            return;
        }
        if (!"locked".equals(mc.getStatus())) {
            throw new BusinessException("仅锁定中的券可核销，当前状态: " + mc.getStatus());
        }
        // 核销 member_coupon（带乐观锁）
        int affected = memberCouponMapper.verifyWithOrder(memberCouponId, orderId, mc.getVersion());
        if (affected == 0) {
            throw new BusinessException("核销失败，券状态已变更");
        }
        // coupon.used_count++（原子）
        couponMapper.incrUsedCount(mc.getCouponId());
    }

    /**
     * 计算优惠金额（下单预览用，不实际核销）
     * discount 折扣券：discount = amount * (1 - value)，封顶 discount_cap
     * deduction 满减券：discount = value（满足 min_amount 门槛）
     * duration 时长券：不直接抵扣金额，返回 0（由订单层处理加天数）
     */
    public BigDecimal calculateDiscount(Long couponId, BigDecimal amount) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        return doCalculate(coupon, amount);
    }

    /**
     * 当前用户已领取的优惠券ID列表（含已使用+未使用）
     * 用于首页判断"立即领取/已领取"按钮状态
     */
    public List<Long> getMyClaimedCouponIds() {
        Long memberId = SecurityUtil.getCurrentMemberId();
        List<MemberCoupon> memberCoupons = memberCouponMapper.selectList(new LambdaQueryWrapper<MemberCoupon>()
                .eq(MemberCoupon::getMemberId, memberId));
        if (memberCoupons.isEmpty()) {
            return Collections.emptyList();
        }
        return memberCoupons.stream().map(MemberCoupon::getCouponId).toList();
    }

    // ============================================================
    // 供 OrderService 调用的内部方法（不校验登录态，由调用方保证）
    // ============================================================

    /**
     * 锁定优惠券（供 OrderService 下单时调用，已校验登录态）
     */
    @Transactional
    public void lockForOrder(Long memberCouponId, Long memberId) {
        MemberCoupon mc = getAndCheckOwnership(memberCouponId, memberId);
        if (!"unused".equals(mc.getStatus())) {
            throw new BusinessException("仅未使用的券可锁定，当前状态: " + mc.getStatus());
        }
        if (mc.getExpireTime() != null && mc.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("优惠券已过期");
        }
        int affected = memberCouponMapper.updateStatusWithVersion(memberCouponId, "unused", "locked", mc.getVersion());
        if (affected == 0) {
            throw new BusinessException("优惠券锁定失败，可能已被使用");
        }
    }

    /**
     * 取消锁定（供 OrderService 取消订单时调用）
     */
    @Transactional
    public void cancelLockForOrder(Long memberCouponId, Long memberId) {
        if (memberCouponId == null) return;
        MemberCoupon mc = memberCouponMapper.selectById(memberCouponId);
        if (mc == null || !mc.getMemberId().equals(memberId)) return;
        if (!"locked".equals(mc.getStatus())) return;
        memberCouponMapper.updateStatusWithVersion(memberCouponId, "locked", "unused", mc.getVersion());
    }

    /**
     * 核销（供 OrderService 支付完成时调用）
     */
    @Transactional
    public void verifyForOrder(Long memberCouponId, Long memberId, Long orderId) {
        if (memberCouponId == null) return;
        MemberCoupon mc = memberCouponMapper.selectById(memberCouponId);
        if (mc == null || !mc.getMemberId().equals(memberId)) return;
        if ("used".equals(mc.getStatus())) return;
        if (!"locked".equals(mc.getStatus())) return;
        int affected = memberCouponMapper.verifyWithOrder(memberCouponId, orderId, mc.getVersion());
        if (affected > 0) {
            couponMapper.incrUsedCount(mc.getCouponId());
        }
    }

    /**
     * 计算优惠金额（供 OrderService 下单时调用，传入 couponId）
     */
    public BigDecimal calculateDiscountForOrder(Long couponId, BigDecimal amount) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) return BigDecimal.ZERO;
        return doCalculate(coupon, amount);
    }

    /**
     * 校验优惠券在下单场景下是否可用（供 OrderService 下单前校验）
     */
    public void validateUsable(Long memberCouponId, Long memberId, Long carId, BigDecimal amount) {
        MemberCoupon mc = memberCouponMapper.selectById(memberCouponId);
        if (mc == null || !mc.getMemberId().equals(memberId)) {
            throw new BusinessException("优惠券不存在或无权使用");
        }
        if (!"unused".equals(mc.getStatus())) {
            throw new BusinessException("优惠券状态不可用: " + mc.getStatus());
        }
        if (mc.getExpireTime() != null && mc.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("优惠券已过期");
        }
        if (mc.getMinAmount() != null && amount != null && amount.compareTo(mc.getMinAmount()) < 0) {
            throw new BusinessException("未达到优惠券使用门槛");
        }
        if ("specified".equals(mc.getApplyScope())) {
            List<Long> carIds = couponMapper.selectCarIdsByCouponId(mc.getCouponId());
            if (carId == null || !carIds.contains(carId)) {
                throw new BusinessException("该优惠券不适用于当前车辆");
            }
        }
    }

    // ============================================================
    // 私有工具方法
    // ============================================================

    /**
     * 优惠金额计算（核心算法）
     * discount 折扣券：discount = amount * (1 - value)，封顶 discount_cap
     * deduction 满减券：discount = value（满足 min_amount 门槛）
     * duration 时长券：不直接抵扣金额，返回 0（由订单层处理加天数）
     */
    public static BigDecimal doCalculate(Coupon coupon, BigDecimal amount) {
        if (coupon == null || amount == null) return BigDecimal.ZERO;
        if (coupon.getMinAmount() != null && amount.compareTo(coupon.getMinAmount()) < 0) return BigDecimal.ZERO;
        BigDecimal discount;
        switch (coupon.getType()) {
            case "discount":
                // 折扣值 0.88 表示88折，优惠 = 原价 * (1 - 0.88)
                discount = amount.multiply(BigDecimal.ONE.subtract(coupon.getValue()))
                        .setScale(2, RoundingMode.HALF_UP);
                if (coupon.getDiscountCap() != null && discount.compareTo(coupon.getDiscountCap()) > 0) {
                    discount = coupon.getDiscountCap();
                }
                break;
            case "deduction":
                discount = coupon.getValue();
                break;
            case "duration":
                // 时长券不抵扣金额
                discount = BigDecimal.ZERO;
                break;
            default:
                discount = BigDecimal.ZERO;
        }
        // 优惠不能超过原价
        if (discount.compareTo(amount) > 0) discount = amount;
        return discount;
    }

    private MemberCoupon getAndCheckOwnership(Long memberCouponId, Long memberId) {
        MemberCoupon mc = memberCouponMapper.selectById(memberCouponId);
        if (mc == null) {
            throw new BusinessException("用户券不存在");
        }
        if (!mc.getMemberId().equals(memberId)) {
            throw new BusinessException("无权操作他人优惠券");
        }
        return mc;
    }

    private void fillRemainCount(Coupon c) {
        if (c.getTotalCount() == null) return;
        if (c.getTotalCount() == -1) {
            c.setRemainCount(Integer.MAX_VALUE);
            return;
        }
        int received = c.getReceivedCount() == null ? 0 : c.getReceivedCount();
        c.setRemainCount(Math.max(0, c.getTotalCount() - received));
    }

    private String generateCode() {
        return "MC" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
