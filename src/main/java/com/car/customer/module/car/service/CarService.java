package com.car.customer.module.car.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.common.result.PageResult;
import com.car.customer.common.util.SecurityUtil;
import com.car.customer.entity.*;
import com.car.customer.mapper.*;
import com.car.customer.module.car.vo.CarImageGroupVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 车辆服务（所有数据来自 car_rental.car_info + car_rental.car_config，图片取自 car_info.images）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarService {

    private final CarMapper carMapper;
    private final CarConfigMapper carConfigMapper;
    private final CarImageMapper carImageMapper;
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
        // 注入被租状态/原因/最早可租日期（基于实际订单）
        enrichAvailability(p.getRecords());
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
        // 注入被租状态/原因/最早可租日期（基于实际订单）
        enrichAvailability(List.of(car));
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
        enrichAvailability(cars);
        return cars;
    }

    /**
     * 查询车辆所有素材图片并按 category 分组（用于车辆详情页分类展示）
     * 数据来自 car_rental.car_image 表，通过 vehicle_id 关联车辆
     * @param carId 车辆 ID（car_rental.car_info.id）
     * @return 按 category 分组的图片列表；无数据时返回空列表
     */
    public List<CarImageGroupVO> getCarImagesGroupedByCategory(Long carId) {
        List<CarImage> images = carImageMapper.selectByVehicleId(carId);
        if (images == null || images.isEmpty()) {
            return Collections.emptyList();
        }
        // 按 category 分组，保持首次出现顺序（LinkedHashMap）
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (CarImage img : images) {
            String category = img.getCategory() != null ? img.getCategory() : "其他";
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(img.getUrl());
        }
        return grouped.entrySet().stream().map(e -> {
            CarImageGroupVO vo = new CarImageGroupVO();
            vo.setCategory(e.getKey());
            vo.setImages(e.getValue());
            return vo;
        }).collect(Collectors.toList());
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
     * 获取车辆最小起租天数（car_info.min_rent_days 字段）
     * 用于加购/下单时的最小租期校验（前后端双校验）
     * @return 起租天数（≥1）；字段为 null 时兜底为 1
     */
    public Integer resolveMinRentDays(Car car) {
        if (car == null) return 1;
        return car.getMinRentDays() != null ? Math.max(1, car.getMinRentDays()) : 1;
    }

    /**
     * 获取车辆最大租期天数（car_info.max_rent_days 字段）
     * 用于加购/下单/价格计算时的最大租期校验（前后端双校验）
     * @return 最大租期天数；字段为 null 或 <=0 时返回 null（表示不限租期）
     */
    public Integer resolveMaxRentDays(Car car) {
        if (car == null) return null;
        Integer max = car.getMaxRentDays();
        return max != null && max > 0 ? max : null;
    }


    /**
     * 为车辆列表注入券后日租金 couponPrice
     * 仅登录用户、且拥有可用未使用优惠券时计算，取最优（最低）券后价
     *
     * v3 计算规则（修复 v2 把订单总额门槛对着日租金校验的 bug）：
     *   - discount 折扣券：couponPrice = dailyPrice × value（0.88=88折），并受 discountCap 封顶（封顶作用于优惠额）
     *     门槛 minAmount 是"订单总额门槛"，列表页未知租期，按"在该车最大租期内可达"判定：
     *     即 ceil(minAmount / dailyPrice) ≤ maxRentDays 才视为可用（用户在合规租期内能凑到门槛）
     *   - deduction 满减券：minAmount 同上判定；券后日单价按"最低满足天数"折算：
     *     minDays = max(1, ceil(minAmount / dailyPrice))，couponPrice = dailyPrice - value / minDays
     *     （把固定满减额摊到最低满足天数上，得到"最低每日起价"）
     *     若 value ≥ dailyPrice × minDays（券后为负或0）则不展示
     *   - duration 时长券：列表不折算日单价（避免"2天免1天=半价"误导），仅在 car.couponBadge 挂"免X天"提示
     *   - applyScope=specified 时需校验车辆是否在关联列表中
     *   - couponPrice ≥ 0 且 < dailyPrice 才设置；多张券取最低价；同时回填 couponMinDays
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
            if (dailyPrice == null || dailyPrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal bestPrice = null;
            Integer bestMinDays = null;
            String bestBadge = null;
            for (MemberCoupon mc : usable) {
                // 适用范围校验
                if ("specified".equals(mc.getApplyScope())) {
                    List<Long> carIds = specifiedCarMap.computeIfAbsent(mc.getCouponId(),
                            k -> couponMapper.selectCarIdsByCouponId(k));
                    if (!carIds.contains(car.getId())) continue;
                }
                // 计算该券对该车的券后日单价（返回 [券后价, 起步天数, 徽标]）
                CouponDailyResult r = calcDailyCouponPrice(dailyPrice, car.getMaxRentDays(), mc);
                if (r == null) continue;
                // 取券后价最低者；同价时优先折扣券（minDays 更小）
                if (r.price != null
                        && r.price.compareTo(BigDecimal.ZERO) >= 0
                        && r.price.compareTo(dailyPrice) < 0) {
                    if (bestPrice == null || r.price.compareTo(bestPrice) < 0) {
                        bestPrice = r.price;
                        bestMinDays = r.minDays;
                    }
                }
                // 时长券徽标：取面值最大的那张作为提示
                if (r.badge != null) {
                    if (bestBadge == null || r.badgeDays > parseBadgeDays(bestBadge)) {
                        bestBadge = r.badge;
                    }
                }
            }
            if (bestPrice != null) {
                car.setCouponPrice(bestPrice.setScale(2, RoundingMode.HALF_UP));
                car.setCouponMinDays(bestMinDays);
            }
            if (bestBadge != null) {
                car.setCouponBadge(bestBadge);
            }
        }
    }

    /** 从"免X天"徽标文本提取天数，用于比较面值 */
    private int parseBadgeDays(String badge) {
        if (badge == null) return 0;
        try {
            // 形如 "免1天" / "免3天"
            String num = badge.replaceAll("[^0-9]", "");
            return num.isEmpty() ? 0 : Integer.parseInt(num);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 单券对单车的日租金折算结果 */
    private static class CouponDailyResult {
        BigDecimal price;   // 券后日单价（时长券为 null）
        Integer minDays;     // 起步天数（满减券为最低满足天数，折扣券为 1）
        String badge;        // 时长券徽标（金额型券为 null）
        int badgeDays;       // 徽标对应天数（用于比较面值）
    }

    /**
     * 根据优惠券类型计算日租金券后价（v3 模型）
     * @param dailyPrice   车辆日租金
     * @param maxRentDays  车辆最大租期（null=不限）；满减/折扣券门槛需在该租期内可达才展示券后价
     * @return CouponDailyResult；不适用时返回 null
     */
    private CouponDailyResult calcDailyCouponPrice(BigDecimal dailyPrice, Integer maxRentDays, MemberCoupon mc) {
        if (mc == null || dailyPrice == null || mc.getCouponType() == null) return null;
        String type = mc.getCouponType();
        BigDecimal value = mc.getCouponValue();
        if (value == null) return null;
        // 车辆最大租期：null 或 <=0 视为不限
        int maxDays = maxRentDays != null && maxRentDays > 0 ? maxRentDays : Integer.MAX_VALUE;
        CouponDailyResult r = new CouponDailyResult();
        switch (type) {
            case "discount":
                // 折扣值 0.88 表示 88 折，券后价 = dailyPrice × 0.88
                BigDecimal discounted = dailyPrice.multiply(value);
                // 封顶作用于优惠额：优惠额 = dailyPrice - discounted，不超过 discountCap
                if (mc.getDiscountCap() != null) {
                    BigDecimal save = dailyPrice.subtract(discounted);
                    if (save.compareTo(mc.getDiscountCap()) > 0) {
                        // 优惠被封顶，券后价 = dailyPrice - discountCap
                        discounted = dailyPrice.subtract(mc.getDiscountCap());
                    }
                }
                // 门槛：按该车最大租期内可达判定（不限则任意租期均可）
                if (mc.getMinAmount() != null && mc.getMinAmount().compareTo(BigDecimal.ZERO) > 0) {
                    int needDays = (int) Math.ceil(mc.getMinAmount().doubleValue() / dailyPrice.doubleValue());
                    if (needDays > maxDays) return null;
                }
                r.price = discounted;
                r.minDays = 1;
                return r;
            case "deduction":
                // 满减券：minDays = ceil(minAmount / dailyPrice)，券后日单价 = dailyPrice - value/minDays
                int minDays = 1;
                if (mc.getMinAmount() != null && mc.getMinAmount().compareTo(BigDecimal.ZERO) > 0) {
                    minDays = (int) Math.ceil(mc.getMinAmount().doubleValue() / dailyPrice.doubleValue());
                    if (minDays < 1) minDays = 1;
                    // 车辆最大租期内凑不到门槛则列表不展示券后价
                    if (minDays > maxDays) return null;
                }
                // 每天摊销的优惠额
                BigDecimal perDayDiscount = value.divide(BigDecimal.valueOf(minDays), 2, RoundingMode.HALF_UP);
                BigDecimal couponPrice = dailyPrice.subtract(perDayDiscount);
                // 券后价必须为正且低于原价
                if (couponPrice.compareTo(BigDecimal.ZERO) <= 0
                        || couponPrice.compareTo(dailyPrice) >= 0) {
                    return null;
                }
                r.price = couponPrice;
                r.minDays = minDays;
                return r;
            case "duration":
                // 时长券：列表不折算日单价，仅挂徽标
                int freeDays = value.intValue();
                if (freeDays <= 0) return null;
                r.badge = "免" + freeDays + "天";
                r.badgeDays = freeDays;
                return r;
            default:
                return null;
        }
    }

    // ============================================================
    // 工具方法
    // ============================================================

    /**
     * 批量注入被租状态/原因/最早可租日期
     * 以实际订单为准（pending/renting），覆盖 car_info.status 可能不准确的问题：
     *   - 有占用订单 → status='rented' / statusName='已出租' / rentReason / availableDate
     *   - 无订单但 car_info.status 为维修类 → rentReason='车辆维修保养中'
     *   - 其余 → 维持可租，rentReason=null
     * 一次查询所有相关订单，避免 N+1
     */
    private void enrichAvailability(List<Car> cars) {
        if (cars == null || cars.isEmpty()) return;

        // 1) 维修中车辆：直接设置原因
        cars.stream()
                .filter(c -> "maintenance".equals(c.getStatus()))
                .forEach(c -> c.setRentReason("车辆维修保养中"));

        // 2) 一次性查询所有车辆的占用订单（pending/renting）
        List<Long> carIds = cars.stream()
                .map(Car::getId)
                .filter(Objects::nonNull)
                .toList();
        if (carIds.isEmpty()) return;
        List<RentalOrder> allOccupied = rentalOrderMapper.selectList(new LambdaQueryWrapper<RentalOrder>()
                .in(RentalOrder::getCarId, carIds)
                .in(RentalOrder::getStatus, OCCUPIED_STATUSES));
        // 按车分组
        Map<Long, List<RentalOrder>> byCar = allOccupied.stream()
                .collect(Collectors.groupingBy(RentalOrder::getCarId));

        LocalDate today = LocalDate.now();
        for (Car car : cars) {
            List<RentalOrder> occupied = byCar.get(car.getId());
            if (occupied == null || occupied.isEmpty()) continue;

            // 有占用订单：强制标记为已出租（覆盖 car_info.status 可能不准的情况）
            car.setStatus("rented");
            car.setStatusName("已出租");

            // 原因：优先"租赁中"（renting），其次"已被预约"（pending）
            boolean hasRenting = occupied.stream().anyMatch(o -> "renting".equals(o.getStatus()));
            car.setRentReason(hasRenting ? "车辆租赁中" : "已被预约");

            // 最早可租日：最大到期日 + 整备期
            LocalDate maxEnd = occupied.stream()
                    .map(RentalOrder::getEndDate)
                    .filter(Objects::nonNull)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            if (maxEnd == null) continue;
            LocalDate available = maxEnd.plusDays(PREP_DAYS);
            if (available.isBefore(today)) {
                available = today;
            }
            car.setAvailableDate(available);
        }
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
