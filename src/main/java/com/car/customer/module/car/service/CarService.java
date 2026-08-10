package com.car.customer.module.car.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.result.PageResult;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.entity.*;
import com.car.customer.mapper.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 车辆服务（所有数据来自 car_rental.car_info + car_rental.car_config，图片取自 car_info.images）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarService {

    private final CarMapper carMapper;
    private final CarConfigMapper carConfigMapper;
    private final ObjectMapper objectMapper;
    private final CouponMapper couponMapper;
    private final MemberCouponMapper memberCouponMapper;
    private final RentalOrderMapper rentalOrderMapper;

    /** 车辆整备天数：当前订单到期日后预留 N 天方可再次起租 */
    private static final int PREP_DAYS = 2;
    /** 视为"未完成"的订单状态：仍占用车辆时间线 */
    private static final List<String> OCCUPIED_STATUSES = List.of("pending", "renting");

    /**
     * 车辆列表分页查询（支持类型/关键字/价格区间/状态/排序）
     * 登录用户额外返回券后日租金 couponPrice
     */
    public PageResult<Car> getCarList(String type, String keyword, BigDecimal minPrice,
                                      BigDecimal maxPrice, String status, String sort,
                                      Integer page, Integer pageSize) {
        IPage<Car> p = carMapper.selectCarPage(new Page<>(page, pageSize),
                type, keyword, minPrice, maxPrice, status, sort);
        // 填充 tags 数组与图片列表
        p.getRecords().forEach(car -> {
            parseTags(car);
            parseImages(car);
        });
        // 登录用户注入券后价
        enrichWithCouponPrice(p.getRecords());
        return PageResult.of(p);
    }

    /**
     * 车辆详情（含图片列表 + 配置）
     * 所有车辆均来自 car_rental.car_info
     */
    public Car getCarDetail(Long id) {
        Car car = carMapper.selectAdminCarById(id);
        if (car == null) {
            throw new BusinessException("车辆不存在");
        }
        car.setFromAdminDb(true);
        parseTags(car);
        // 解析图片列表（来自 car_rental.car_info.images 字段）
        parseImages(car);
        // 查询配置（来自 car_rental.car_config）
        CarConfig config = carConfigMapper.selectOne(new LambdaQueryWrapper<CarConfig>()
                .eq(CarConfig::getCarId, id));
        car.setConfig(config != null ? config : emptyConfig());
        // 注入券后价
        enrichWithCouponPrice(List.of(car));
        // 注入最早可租日期（关联未完成订单的到期日 + 整备期）
        enrichAvailableDate(car);
        return car;
    }

    /**
     * 推荐车型（首页"热门车型推荐"模块，数据来源于 car_rental.car_info）
     */
    public List<Car> getHotCars() {
        List<Car> cars = carMapper.selectHotFromAdminDb();
        cars.forEach(car -> {
            car.setFromAdminDb(true);
            parseTags(car);
            parseImages(car);
        });
        enrichWithCouponPrice(cars);
        return cars;
    }

    /**
     * 切换车型推荐状态（后台管理配置，客户库 car 表已删除，此方法暂不可用）
     */
    public Car toggleRecommend(Long id, Integer isRecommend) {
        throw new BusinessException("车辆数据由 car_rental 库管理，不支持在客户库修改");
    }

    // ============================================================
    // 统一车辆查找（供 CartService / OrderService 使用）
    // ============================================================

    /**
     * 按 ID 查找车辆实体（所有车辆均来自 car_rental.car_info）
     */
    public Car getCarEntityById(Long id) {
        Car car = carMapper.selectAdminCarById(id);
        if (car != null) {
            car.setFromAdminDb(true);
            parseTags(car);
            parseImages(car);
        }
        return car;
    }

    // ============================================================
    // 券后价计算
    // ============================================================

    /**
     * 为车辆列表注入券后日租金 couponPrice
     * 仅登录用户、且拥有可用未使用优惠券时计算，取最优（最低）券后价
     *
     * v2 计算规则（与 CouponService.doCalculate 对齐）：
     *   - discount 折扣券：couponPrice = dailyPrice × value（0.88=88折），并受 discountCap 封顶（封顶作用于优惠额）
     *     若 minAmount != null 且 dailyPrice < minAmount，则不适用
     *   - deduction 满减券：仅当 dailyPrice ≥ minAmount 时适用，couponPrice = dailyPrice - value
     *   - duration 时长券：日租金维度无法准确计算，跳过
     *   - applyScope=specified 时需校验车辆是否在关联列表中
     *   - couponPrice ≥ 0 且 < dailyPrice 才设置，否则保持 null
     */
    private void enrichWithCouponPrice(List<Car> cars) {
        if (cars == null || cars.isEmpty()) return;
        if (!SecurityUtil.isLogged()) return;

        Long memberId = SecurityUtil.getCurrentMemberId();
        // 复用跨库 JOIN 查询，拿到带券模板信息的 member_coupon 列表
        List<MemberCoupon> memberCoupons = memberCouponMapper.selectMyCoupons(memberId);
        LocalDateTime now = LocalDateTime.now();
        // 仅保留 unused 且未过期的券
        List<MemberCoupon> usable = memberCoupons.stream()
                .filter(mc -> "unused".equals(mc.getStatus()))
                .filter(mc -> mc.getExpireTime() == null || !mc.getExpireTime().isBefore(now))
                .toList();
        if (usable.isEmpty()) return;

        // 收集 couponId → 指定车辆 ID 列表（避免重复查询）
        java.util.Map<Long, List<Long>> specifiedCarMap = new java.util.HashMap<>();

        for (Car car : cars) {
            BigDecimal dailyPrice = car.getDailyPrice();
            if (dailyPrice == null) continue;

            BigDecimal bestPrice = null;
            for (MemberCoupon mc : usable) {
                // 门槛校验
                if (mc.getMinAmount() != null && dailyPrice.compareTo(mc.getMinAmount()) < 0) continue;
                // 适用范围校验
                if ("specified".equals(mc.getApplyScope())) {
                    List<Long> carIds = specifiedCarMap.computeIfAbsent(mc.getCouponId(),
                            k -> couponMapper.selectCarIdsByCouponId(k));
                    if (!carIds.contains(car.getId())) continue;
                }
                BigDecimal couponPrice = calcDailyCouponPrice(dailyPrice, mc);
                if (couponPrice == null) continue;
                if (bestPrice == null || couponPrice.compareTo(bestPrice) < 0) {
                    bestPrice = couponPrice;
                }
            }
            if (bestPrice != null
                    && bestPrice.compareTo(BigDecimal.ZERO) >= 0
                    && bestPrice.compareTo(dailyPrice) < 0) {
                car.setCouponPrice(bestPrice.setScale(2, RoundingMode.HALF_UP));
            }
        }
    }

    /**
     * 根据优惠券类型计算日租金券后价（v2 模型）
     * @return 券后价；不适用时返回 null
     */
    private BigDecimal calcDailyCouponPrice(BigDecimal dailyPrice, MemberCoupon mc) {
        if (mc == null || dailyPrice == null || mc.getCouponType() == null) return null;
        String type = mc.getCouponType();
        BigDecimal value = mc.getCouponValue();
        if (value == null) return null;
        switch (type) {
            case "discount":
                // 折扣值 0.88 表示 88 折，券后价 = dailyPrice × 0.88
                BigDecimal discounted = dailyPrice.multiply(value);
                // 封顶作用于优惠额：优惠额 = dailyPrice - discounted，不超过 discountCap
                if (mc.getDiscountCap() != null) {
                    BigDecimal save = dailyPrice.subtract(discounted);
                    if (save.compareTo(mc.getDiscountCap()) > 0) {
                        // 优惠被封顶，券后价 = dailyPrice - discountCap
                        return dailyPrice.subtract(mc.getDiscountCap());
                    }
                }
                return discounted;
            case "deduction":
                // 满减券：券后价 = dailyPrice - value（minAmount 已在调用方校验）
                return dailyPrice.subtract(value);
            case "duration":
            default:
                return null;
        }
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 注入最早可租日期 availableDate
     * 查询该车所有未完成订单（pending/renting）的最大 end_date，加整备期 PREP_DAYS
     * 仅在存在未完成订单时设置；空闲车辆保持 null
     */
    private void enrichAvailableDate(Car car) {
        if (car == null || car.getId() == null) return;
        List<RentalOrder> occupied = rentalOrderMapper.selectList(new LambdaQueryWrapper<RentalOrder>()
                .eq(RentalOrder::getCarId, car.getId())
                .in(RentalOrder::getStatus, OCCUPIED_STATUSES));
        if (occupied.isEmpty()) return;
        LocalDate maxEnd = occupied.stream()
                .map(RentalOrder::getEndDate)
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
        if (maxEnd == null) return;
        // 整备期：到期日 + PREP_DAYS 后方可起租
        LocalDate available = maxEnd.plusDays(PREP_DAYS);
        // 若算出的可租日早于今天，则以下个自然日为最早可租日（保证前端可选）
        LocalDate today = LocalDate.now();
        if (available.isBefore(today)) {
            available = today;
        }
        car.setAvailableDate(available);
    }

    private CarConfig emptyConfig() {
        CarConfig config = new CarConfig();
        config.setPower("-");
        config.setTransmission("-");
        config.setFuel("-");
        config.setRangeKm("-");
        config.setInterior("-");
        config.setSafety("-");
        config.setEntertainment("-");
        return config;
    }

    private void parseTags(Car car) {
        if (car.getTags() == null || car.getTags().isBlank()) {
            car.setTagList(Collections.emptyList());
            return;
        }
        try {
            List<String> list = objectMapper.readValue(car.getTags(), new TypeReference<List<String>>() {});
            car.setTagList(list);
        } catch (Exception e) {
            log.warn("解析车辆标签失败: {}", car.getTags(), e);
            car.setTagList(Collections.emptyList());
        }
    }

    /**
     * 解析 car_info.images 字段为图片URL列表，并补充封面图 cover
     * 兼容三种存储格式：JSON数组字符串、逗号分隔字符串、单个URL
     */
    private void parseImages(Car car) {
        String raw = car.getImages();
        if (raw == null || raw.isBlank()) {
            car.setImageList(Collections.emptyList());
            return;
        }
        List<String> list;
        String trimmed = raw.trim();
        if (trimmed.startsWith("[")) {
            try {
                list = objectMapper.readValue(trimmed, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("解析车辆图片失败: {}", raw, e);
                list = Collections.emptyList();
            }
        } else if (trimmed.contains(",")) {
            list = Arrays.stream(trimmed.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } else {
            list = List.of(trimmed);
        }
        car.setImageList(list);
        if ((car.getCover() == null || car.getCover().isBlank()) && !list.isEmpty()) {
            car.setCover(list.get(0));
        }
    }
}
