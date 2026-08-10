package com.car.customer.module.order.service;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.result.PageResult;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.entity.Car;
import com.car.customer.entity.MemberCoupon;
import com.car.customer.entity.RentalOrder;
import com.car.customer.mapper.CouponMapper;
import com.car.customer.mapper.MemberCouponMapper;
import com.car.customer.mapper.RentalOrderMapper;
import com.car.customer.module.car.service.CarService;
import com.car.customer.module.coupon.service.CouponService;
import com.car.customer.module.order.dto.CreateOrderDTO;
import com.car.customer.module.price.service.PriceService;
import com.car.customer.module.price.vo.PriceDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RentalOrderMapper orderMapper;
    private final CarService carService;
    private final MemberCouponMapper memberCouponMapper;
    private final CouponMapper couponMapper;
    private final CouponService couponService;
    private final PriceService priceService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 待支付订单超时时间（分钟），超时自动取消 */
    private static final int PAY_TIMEOUT_MINUTES = 5;

    /**
     * 创建订单（购物车每个车辆生成独立订单）
     * 优惠券联动：下单时锁定券（unused → locked），应用到首个订单；失败回滚
     * 返回 {id: 首个订单id, orderNo: 首个订单号, count: 订单总数, couponDiscount: 优惠金额}
     */
    @Transactional
    public Map<String, Object> createOrder(CreateOrderDTO dto) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        Long firstId = null;
        String firstOrderNo = null;
        RentalOrder firstOrder = null;
        BigDecimal rentAmountTotal = BigDecimal.ZERO;
        int count = 0;
        Long couponUserId = dto.getCouponUserId();
        boolean couponLocked = false;

        // 1. 优惠券预校验 + 锁定（unused → locked）
        if (couponUserId != null) {
            // 取首辆车作为门槛/适用范围校验依据（多车下单场景简化：用首车校验）
            CreateOrderDTO.CartItemDTO firstItem = dto.getItems().get(0);
            couponService.validateUsable(couponUserId, memberId, firstItem.getCarId(), BigDecimal.ZERO);
            // 锁定优惠券（带乐观锁，并发安全）
            couponService.lockForOrder(couponUserId, memberId);
            couponLocked = true;
        }

        try {
            for (CreateOrderDTO.CartItemDTO item : dto.getItems()) {
                // 校验车辆状态
                Car car = carService.getCarEntityById(item.getCarId());
                if (car == null) {
                    throw new BusinessException("车辆不存在: " + item.getCarId());
                }
                if (!"available".equals(car.getStatus())) {
                    throw new BusinessException("车辆「" + car.getName() + "」当前不可租");
                }

                RentalOrder order = new RentalOrder();
                order.setOrderNo(generateOrderNo());
                order.setMemberId(memberId);
                order.setCarId(item.getCarId());
                order.setCarName(car.getName());
                order.setCarCover(car.getCover());
                order.setStatus("pending");
                order.setStatusName("待支付");
                LocalDate startDate = LocalDate.parse(item.getStartDate(), DATE_FMT);
                LocalDate endDate = LocalDate.parse(item.getEndDate(), DATE_FMT);
                order.setStartDate(startDate);
                order.setEndDate(endDate);
                order.setDays(item.getDays());
                order.setDailyPrice(car.getDailyPrice());

                // 使用 PriceService 统一计算价格（与前端展示完全一致）
                PriceDetailVO price = priceService.calculate(car, startDate, endDate);
                BigDecimal rentAmount = price.getRentAmount();
                order.setRentAmount(rentAmount);
                order.setCouponDiscount(BigDecimal.ZERO);
                order.setTotalAmount(rentAmount);
                order.setCity(dto.getCity());
                order.setStore(dto.getStore());
                order.setContactName(dto.getName());
                order.setContactPhone(dto.getPhone());

                orderMapper.insert(order);
                count++;
                rentAmountTotal = rentAmountTotal.add(rentAmount);
                if (firstId == null) {
                    firstId = order.getId();
                    firstOrderNo = order.getOrderNo();
                    firstOrder = order;
                }
            }

            // 2. 优惠券抵扣计算（应用到首个订单）
            BigDecimal couponDiscount = BigDecimal.ZERO;
            if (couponUserId != null && firstOrder != null) {
                // 查 member_coupon 拿 couponId
                MemberCoupon mc = memberCouponMapper.selectById(couponUserId);
                if (mc != null) {
                    couponDiscount = couponService.calculateDiscountForOrder(mc.getCouponId(), rentAmountTotal);
                }
                if (couponDiscount.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal actualDiscount = couponDiscount.min(firstOrder.getTotalAmount());
                    firstOrder.setCouponDiscount(actualDiscount);
                    firstOrder.setTotalAmount(firstOrder.getTotalAmount().subtract(actualDiscount));
                    firstOrder.setCouponUserId(couponUserId);
                    orderMapper.updateById(firstOrder);
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", firstId);
            result.put("orderNo", firstOrderNo);
            result.put("count", count);
            result.put("couponDiscount", couponDiscount);
            return result;
        } catch (RuntimeException e) {
            // 下单失败：回滚优惠券锁定
            if (couponLocked) {
                try {
                    couponService.cancelLockForOrder(couponUserId, memberId);
                } catch (Exception ex) {
                    log.error("回滚优惠券锁定失败 couponUserId={}", couponUserId, ex);
                }
            }
            throw e;
        }
    }

    public PageResult<RentalOrder> getOrderList(String status, Integer page, Integer pageSize) {
        autoCancelExpiredOrders();
        Long memberId = SecurityUtil.getCurrentMemberId();
        LambdaQueryWrapper<RentalOrder> wrapper = new LambdaQueryWrapper<RentalOrder>()
                .eq(RentalOrder::getMemberId, memberId);
        if (status != null && !"all".equals(status)) {
            wrapper.eq(RentalOrder::getStatus, status);
        }
        wrapper.orderByDesc(RentalOrder::getCreateTime);
        IPage<RentalOrder> p = orderMapper.selectPage(new Page<>(page, pageSize), wrapper);
        // 填充优惠券名称
        p.getRecords().forEach(this::fillCouponName);
        return PageResult.of(p);
    }

    public RentalOrder getOrderDetail(Long id) {
        autoCancelExpiredOrders();
        Long memberId = SecurityUtil.getCurrentMemberId();
        RentalOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权查看该订单");
        }
        fillCouponName(order);
        return order;
    }

    /**
     * 取消订单：pending → cancelled，并回滚优惠券（locked → unused）
     */
    @Transactional
    public void cancelOrder(Long id) {
        Long memberId = SecurityUtil.getCurrentMemberId();
        RentalOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权操作该订单");
        }
        if (!"pending".equals(order.getStatus())) {
            throw new BusinessException("仅待支付订单可取消");
        }
        order.setStatus("cancelled");
        order.setStatusName("已取消");
        orderMapper.updateById(order);
        // 回滚优惠券锁定
        if (order.getCouponUserId() != null) {
            couponService.cancelLockForOrder(order.getCouponUserId(), memberId);
        }
    }

    /**
     * 支付订单：pending → renting，并核销优惠券（locked → used）
     */
    @Transactional
    public void payOrder(Long id) {
        autoCancelExpiredOrders();
        Long memberId = SecurityUtil.getCurrentMemberId();
        RentalOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权操作该订单");
        }
        String status = order.getStatus();
        if ("cancelled".equals(status)) {
            throw new BusinessException("订单已超时自动取消，无法支付");
        }
        if ("renting".equals(status) || "completed".equals(status)) {
            throw new BusinessException("订单已支付，无需重复支付");
        }
        if (!"pending".equals(status)) {
            throw new BusinessException("当前订单状态无法支付");
        }
        order.setStatus("renting");
        order.setStatusName("租赁中");
        orderMapper.updateById(order);
        // 核销优惠券（locked → used，幂等）
        if (order.getCouponUserId() != null) {
            couponService.verifyForOrder(order.getCouponUserId(), memberId, order.getId());
        }
    }

    /**
     * 自动取消超时未支付的订单（待支付超过 PAY_TIMEOUT_MINUTES 分钟）
     * 懒取消策略：在查询列表/详情/支付前触发，无需定时任务
     * 同时回滚优惠券锁定
     */
    @Transactional
    public void autoCancelExpiredOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(PAY_TIMEOUT_MINUTES);
        List<RentalOrder> expired = orderMapper.selectList(new LambdaQueryWrapper<RentalOrder>()
                .eq(RentalOrder::getStatus, "pending")
                .lt(RentalOrder::getCreateTime, deadline));
        if (expired.isEmpty()) {
            return;
        }
        for (RentalOrder order : expired) {
            order.setStatus("cancelled");
            order.setStatusName("已取消");
            orderMapper.updateById(order);
            // 回滚优惠券锁定
            if (order.getCouponUserId() != null) {
                try {
                    couponService.cancelLockForOrder(order.getCouponUserId(), order.getMemberId());
                } catch (Exception e) {
                    log.error("自动取消订单回滚优惠券失败 orderId={}, couponUserId={}",
                            order.getId(), order.getCouponUserId(), e);
                }
            }
            log.info("订单超时自动取消: orderNo={}, createTime={}", order.getOrderNo(), order.getCreateTime());
        }
    }

    /**
     * 生成订单号: LC + yyyyMMddHHmmss + 3位随机数
     */
    private String generateOrderNo() {
        return "LC" + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + RandomUtil.randomNumbers(3);
    }

    /**
     * 填充订单的优惠券名称（前端展示用）
     */
    private void fillCouponName(RentalOrder order) {
        if (order.getCouponUserId() == null) return;
        MemberCoupon mc = memberCouponMapper.selectById(order.getCouponUserId());
        if (mc != null && mc.getCouponId() != null) {
            com.car.customer.entity.Coupon coupon = couponMapper.selectById(mc.getCouponId());
            if (coupon != null) {
                order.setCouponName(coupon.getName());
            }
        }
    }
}
