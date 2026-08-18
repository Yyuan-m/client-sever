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
    /** 单次每车最大租期（前后端双校验，加强保障） */
    private static final int MAX_RENT_DAYS = 20;

    /**
     * 创建订单（购物车每个车辆生成独立订单）
     * 优惠券联动（v3 支持多张可叠加券）：下单时批量锁定券（unused → locked），应用到首个订单；失败回滚
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

        // 0. 解析使用的券ID列表（兼容旧字段 couponUserId）
        List<Long> couponUserIds = new java.util.ArrayList<>();
        if (dto.getCouponUserIds() != null && !dto.getCouponUserIds().isEmpty()) {
            couponUserIds.addAll(dto.getCouponUserIds());
        } else if (dto.getCouponUserId() != null) {
            couponUserIds.add(dto.getCouponUserId());
        }
        boolean couponLocked = false;

        // 1. 优惠券预校验 + 批量锁定（unused → locked）
        if (!couponUserIds.isEmpty()) {
            // 取首辆车作为门槛/适用范围校验依据（多车下单场景简化：用首车校验）
            CreateOrderDTO.CartItemDTO firstItem = dto.getItems().get(0);
            couponService.validateUsableBatch(couponUserIds, memberId, firstItem.getCarId(), BigDecimal.ZERO);
            // 批量锁定优惠券（带乐观锁，并发安全，任一失败回滚已锁定的）
            couponService.lockForOrderBatch(couponUserIds, memberId);
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
                // 校验租期上限（前后端双校验）
                if (item.getDays() != null && item.getDays() > MAX_RENT_DAYS) {
                    throw new BusinessException("车辆「" + car.getName() + "」单次最多租 " + MAX_RENT_DAYS + " 天");
                }
                // 校验最小起租天数（车辆级 minRentDays 与券后价 couponMinDays 取最大值，前后端双校验）
                Integer minDays = carService.resolveMinRentDays(car);
                if (minDays != null && item.getDays() != null && item.getDays() < minDays) {
                    throw new BusinessException("车辆「" + car.getName() + "」需至少租 " + minDays + " 天起");
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

            // 2. 优惠券抵扣计算（v3 批量叠加，应用到首个订单）
            BigDecimal couponDiscount = BigDecimal.ZERO;
            if (!couponUserIds.isEmpty() && firstOrder != null) {
                couponDiscount = couponService.calculateDiscountForOrderBatch(couponUserIds, rentAmountTotal);
                if (couponDiscount.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal actualDiscount = couponDiscount.min(firstOrder.getTotalAmount());
                    firstOrder.setCouponDiscount(actualDiscount);
                    firstOrder.setTotalAmount(firstOrder.getTotalAmount().subtract(actualDiscount));
                    // v3：多张券 ID 逗号分隔存储
                    firstOrder.setCouponUserId(joinIds(couponUserIds));
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
            // 下单失败：回滚优惠券锁定（批量）
            if (couponLocked) {
                try {
                    couponService.cancelLockForOrderBatch(couponUserIds, memberId);
                } catch (Exception ex) {
                    log.error("回滚优惠券锁定失败 couponUserIds={}", couponUserIds, ex);
                }
            }
            throw e;
        }
    }

    /** 将券ID列表拼接为逗号分隔字符串 */
    private String joinIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(ids.get(i));
        }
        return sb.toString();
    }

    /** 将 couponUserId 字段（逗号分隔字符串）解析为 Long 列表 */
    private List<Long> parseIds(String couponUserIdStr) {
        if (couponUserIdStr == null || couponUserIdStr.isBlank()) return java.util.Collections.emptyList();
        List<Long> ids = new java.util.ArrayList<>();
        for (String s : couponUserIdStr.split(",")) {
            try { ids.add(Long.valueOf(s.trim())); } catch (NumberFormatException e) { /* 忽略 */ }
        }
        return ids;
    }

    public PageResult<RentalOrder> getOrderList(String status, Integer page, Integer pageSize) {
        autoCancelExpiredOrders();
        autoCompleteOrders();
        Long memberId = SecurityUtil.getCurrentMemberId();
        LambdaQueryWrapper<RentalOrder> wrapper = new LambdaQueryWrapper<RentalOrder>()
                .eq(RentalOrder::getMemberId, memberId);
        if (status != null && !"all".equals(status)) {
            wrapper.eq(RentalOrder::getStatus, status);
        }
        wrapper.orderByDesc(RentalOrder::getCreateTime);
        IPage<RentalOrder> p = orderMapper.selectPage(new Page<>(page, pageSize), wrapper);
        // 填充优惠券名称 + 规范化车辆封面 URL（兼容历史数据）
        p.getRecords().forEach(o -> { fillCouponName(o); normalizeOrderImage(o); });
        return PageResult.of(p);
    }

    public RentalOrder getOrderDetail(Long id) {
        autoCancelExpiredOrders();
        autoCompleteOrders();
        Long memberId = SecurityUtil.getCurrentMemberId();
        RentalOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权查看该订单");
        }
        fillCouponName(order);
        normalizeOrderImage(order);
        return order;
    }

    /**
     * 我的进行中订单（首页"我的订单"模块用）
     * 返回租赁中 + 待评价订单：status=renting 或 reviewStatus=unreviewed，按创建时间倒序，最多 limit 条
     */
    public List<RentalOrder> getMyActiveOrders(Integer limit) {
        autoCancelExpiredOrders();
        autoCompleteOrders();
        Long memberId = SecurityUtil.getCurrentMemberId();
        LambdaQueryWrapper<RentalOrder> wrapper = new LambdaQueryWrapper<RentalOrder>()
                .eq(RentalOrder::getMemberId, memberId)
                .and(w -> w.eq(RentalOrder::getStatus, "renting")
                        .or().eq(RentalOrder::getReviewStatus, "unreviewed"))
                .orderByDesc(RentalOrder::getCreateTime)
                .last("LIMIT " + Math.min(limit != null && limit > 0 ? limit : 6, 20));
        List<RentalOrder> list = orderMapper.selectList(wrapper);
        list.forEach(this::normalizeOrderImage);
        return list;
    }

    /**
     * 可评价订单列表（个人中心"去评价"入口用）
     * 返回 reviewStatus=unreviewed（可首评）或 reviewed（可追评）的已完成订单，按创建时间倒序
     */
    public List<RentalOrder> getReviewableOrders() {
        autoCancelExpiredOrders();
        autoCompleteOrders();
        Long memberId = SecurityUtil.getCurrentMemberId();
        LambdaQueryWrapper<RentalOrder> wrapper = new LambdaQueryWrapper<RentalOrder>()
                .eq(RentalOrder::getMemberId, memberId)
                .eq(RentalOrder::getStatus, "completed")
                .in(RentalOrder::getReviewStatus, "unreviewed", "reviewed")
                .orderByDesc(RentalOrder::getCreateTime);
        List<RentalOrder> list = orderMapper.selectList(wrapper);
        list.forEach(this::normalizeOrderImage);
        return list;
    }

    /**
     * 取消订单：pending → cancelled，并回滚优惠券（locked → unused，v3 批量）
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
        // 回滚优惠券锁定（v3 批量，couponUserId 为逗号分隔字符串）
        List<Long> ids = parseIds(order.getCouponUserId());
        if (!ids.isEmpty()) {
            couponService.cancelLockForOrderBatch(ids, memberId);
        }
    }

    /**
     * 支付订单：pending → renting，并核销优惠券（locked → used，v3 批量）
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
        // 核销优惠券（v3 批量，locked → used，幂等）
        List<Long> ids = parseIds(order.getCouponUserId());
        if (!ids.isEmpty()) {
            couponService.verifyForOrderBatch(ids, memberId, order.getId());
        }
    }

    /**
     * 确认还车（手动完成订单）：renting → completed，并初始化评价状态为待评价
     * 用户在订单详情页主动点击"确认还车"触发；车辆 car_info.status 不在此维护（保持现状，由后台管理）
     */
    @Transactional
    public void completeOrder(Long id) {
        autoCompleteOrders();
        Long memberId = SecurityUtil.getCurrentMemberId();
        RentalOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!memberId.equals(order.getMemberId())) {
            throw new BusinessException(403, "无权操作该订单");
        }
        if (!"renting".equals(order.getStatus())) {
            throw new BusinessException("仅租赁中订单可确认还车");
        }
        order.setStatus("completed");
        order.setStatusName("已完成");
        order.setReviewStatus("unreviewed");
        order.setReviewStatusName("待评价");
        orderMapper.updateById(order);
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
            // 回滚优惠券锁定（v3 批量）
            List<Long> ids = parseIds(order.getCouponUserId());
            if (!ids.isEmpty()) {
                try {
                    couponService.cancelLockForOrderBatch(ids, order.getMemberId());
                } catch (Exception e) {
                    log.error("自动取消订单回滚优惠券失败 orderId={}, couponUserId={}",
                            order.getId(), order.getCouponUserId(), e);
                }
            }
            log.info("订单超时自动取消: orderNo={}, createTime={}", order.getOrderNo(), order.getCreateTime());
        }
    }

    /**
     * 自动完成到期订单：租赁中且 end_date 已过（早于今天）→ 已完成 + 待评价
     * 懒完成策略：在查询列表/详情/确认还车前触发，与 autoCancelExpiredOrders 对称，无需定时任务
     * end_date 当天仍视为租赁中（用户可用满当天），次日才自动完成
     */
    @Transactional
    public void autoCompleteOrders() {
        LocalDate today = LocalDate.now();
        List<RentalOrder> expired = orderMapper.selectList(new LambdaQueryWrapper<RentalOrder>()
                .eq(RentalOrder::getStatus, "renting")
                .lt(RentalOrder::getEndDate, today));
        if (expired.isEmpty()) {
            return;
        }
        for (RentalOrder order : expired) {
            order.setStatus("completed");
            order.setStatusName("已完成");
            order.setReviewStatus("unreviewed");
            order.setReviewStatusName("待评价");
            orderMapper.updateById(order);
            log.info("订单到期自动完成: orderNo={}, endDate={}", order.getOrderNo(), order.getEndDate());
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
     * 规范化订单的车辆封面 URL
     * 历史数据 car_cover 存储的是完整 IP 地址（如 http://192.168.5.185:8088/uploads/xxx.jpg），
     * 前端 resolveAdminImage 对 http:// 开头的路径直接返回会导致跨域 ORB 拦截。
     * 此方法提取其中的相对路径部分（/uploads/xxx.jpg），保证返回数据与当前"数据库只存路径"约定一致。
     */
    private void normalizeOrderImage(RentalOrder order) {
        if (order == null) return;
        String cover = order.getCarCover();
        if (cover == null || cover.isBlank()) return;
        // 已是相对路径直接返回
        if (!cover.startsWith("http://") && !cover.startsWith("https://")) return;
        // 提取 /uploads 及之后的路径部分
        int idx = cover.indexOf("/uploads");
        if (idx >= 0) {
            order.setCarCover(cover.substring(idx));
        }
    }

    /**
     * 填充订单的优惠券名称（前端展示用，v3 支持多张券，顿号分隔）
     */
    private void fillCouponName(RentalOrder order) {
        List<Long> ids = parseIds(order.getCouponUserId());
        if (ids.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            MemberCoupon mc = memberCouponMapper.selectById(ids.get(i));
            if (mc != null && mc.getCouponId() != null) {
                com.car.customer.entity.Coupon coupon = couponMapper.selectById(mc.getCouponId());
                if (coupon != null) {
                    if (sb.length() > 0) sb.append("、");
                    sb.append(coupon.getName());
                }
            }
        }
        if (sb.length() > 0) {
            order.setCouponName(sb.toString());
        }
    }
}
