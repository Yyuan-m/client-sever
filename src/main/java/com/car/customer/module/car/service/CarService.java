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
    private final OrderItemMapper orderItemMapper;

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
     *   - 今天落在任一占用区间（租期 + 整备期）内 → status='rented' / statusName='已出租' / rentReason / availableDate
     *   - 仅有未来预约（今天空闲）→ 维持可租状态，用户可租今天起至预约日前的时段；
     *     精确不可选区间由 /availability 接口返回，详情页日历禁用
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

        // 2) 一次性查询所有被占用车辆（pending/renting）的订单明细
        // 一个订单可含多辆车，占用判断基于 customer_order_item 明细表，
        // 通过主订单状态过滤（同一订单首车冗余在主订单，明细表中同含首车）
        List<Long> carIds = cars.stream()
                .map(Car::getId)
                .filter(Objects::nonNull)
                .toList();
        if (carIds.isEmpty()) return;
        List<OrderItem> allOccupied = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getCarId, carIds)
                .in(OrderItem::getOrderId, occupiedOrderIds()));
        // 按车分组
        Map<Long, List<OrderItem>> byCar = allOccupied.stream()
                .collect(Collectors.groupingBy(OrderItem::getCarId));

        LocalDate today = LocalDate.now();
        for (Car car : cars) {
            List<OrderItem> occupied = byCar.get(car.getId());
            if (occupied == null || occupied.isEmpty()) continue;

            // 仅当"今天"落在任一占用区间（租期 + 整备期）内，才视为已出租；
            // 仅有未来预约时车辆今天仍可租（可租今天至预约日前、以及预约结束+整备之后的时段）
            boolean rentingToday = false;    // 今天在租期内（renting 订单）
            boolean reservedToday = false;   // 今天在预约占用期内（pending 订单）
            LocalDate maxEnd = null;
            for (OrderItem it : occupied) {
                if (it.getStartDate() == null || it.getEndDate() == null) continue;
                LocalDate occEnd = it.getEndDate().plusDays(PREP_DAYS);
                boolean todayIn = !today.isBefore(it.getStartDate()) && !today.isAfter(occEnd);
                if (todayIn) {
                    RentalOrder occ = rentalOrderMapper.selectById(it.getOrderId());
                    if (occ != null && "renting".equals(occ.getStatus())) {
                        rentingToday = true;
                    } else {
                        reservedToday = true;
                    }
                }
                if (maxEnd == null || it.getEndDate().isAfter(maxEnd)) {
                    maxEnd = it.getEndDate();
                }
            }
            if (!rentingToday && !reservedToday) {
                // 今天空闲：仅有未来预约，恢复为可租（覆盖 car_info.status 可能被提前置为 rented 的情况；
                // 维修中除外，维修状态由后台维护）；精确禁用区间由 /availability 接口返回
                if (!"maintenance".equals(car.getStatus()) && "rented".equals(car.getStatus())) {
                    car.setStatus("available");
                    car.setStatusName("可租");
                    car.setRentReason(null);
                }
                continue;
            }

            // 今天被占用：强制标记为已出租（覆盖 car_info.status 可能不准的情况）
            car.setStatus("rented");
            car.setStatusName("已出租");
            car.setRentReason(rentingToday ? "车辆租赁中" : "已被预约");

            // 最早可租日：最大还车日 + 整备天数 + 1（如 09-07 还车、整备 2 天 → 09-10 起可租）
            if (maxEnd == null) continue;
            LocalDate available = maxEnd.plusDays(PREP_DAYS + 1L);
            if (available.isBefore(today)) {
                available = today;
            }
            car.setAvailableDate(available);
        }
    }

    /**
     * 查询当前处于"占用中"状态（pending/renting）的订单 ID 集合
     * 用于明细表占用判断，避免直接在主订单上按首车判断导致遗漏订单内其他车辆
     */
    private java.util.Set<Long> occupiedOrderIds() {
        java.util.Set<Long> ids = new java.util.HashSet<>();
        List<RentalOrder> orders = rentalOrderMapper.selectList(new LambdaQueryWrapper<RentalOrder>()
                .in(RentalOrder::getStatus, OCCUPIED_STATUSES));
        orders.forEach(o -> ids.add(o.getId()));
        return ids;
    }

    // ============================================================
    // 车辆可用性（供购物车改期/选期禁用已租出与整备期）
    // ============================================================

    /**
     * 查询某车所有占用订单明细（pending/renting 的订单，含该车）
     */
    private List<OrderItem> occupiedItemsOf(Long carId) {
        if (carId == null) return Collections.emptyList();
        return orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getCarId, carId)
                .in(OrderItem::getOrderId, occupiedOrderIds()));
    }

    /**
     * 车辆可用期查询（详情页日历禁用/购物车改期用）
     * @return { carId, availableDate, unavailableRanges:[{startDate,endDate}] }
     * unavailableRanges 为闭区间 [startDate, endDate]：租期 [start, end] 再往后加 PREP 天整备期
     * （如租 09-01 至 09-07、整备 2 天 → 09-01~09-09 均不可选）
     * availableDate 为"今天起第一个空闲日"：今天空闲即今天；今天被占用则为占用区间结束的次日。
     * 未来预约不再整体禁租：今天至预约开始前、预约结束后+整备之后的时段均可租（由区间精确禁用）
     */
    public Map<String, Object> getAvailability(Long carId) {
        Car car = carMapper.selectAdminCarById(carId);
        if (car == null) {
            throw new BusinessException("车辆不存在");
        }
        List<OrderItem> occupied = occupiedItemsOf(carId);
        List<Map<String, Object>> ranges = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (OrderItem it : occupied) {
            if (it.getStartDate() == null || it.getEndDate() == null) continue;
            // 不可选区间末尾 = 订单还车日 + 整备天数（含）
            LocalDate rangeEnd = it.getEndDate().plusDays(PREP_DAYS);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("startDate", it.getStartDate().toString());
            r.put("endDate", rangeEnd.toString());
            ranges.add(r);
        }
        // 最早可起租日：从今天起找到第一个不在任何不可选区间内的日期
        LocalDate availableDate = today;
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Map<String, Object> r : ranges) {
                LocalDate s = LocalDate.parse((String) r.get("startDate"));
                LocalDate e = LocalDate.parse((String) r.get("endDate"));
                if (!availableDate.isBefore(s) && !availableDate.isAfter(e)) {
                    availableDate = e.plusDays(1);
                    moved = true;
                }
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("carId", carId);
        result.put("availableDate", availableDate.toString());
        result.put("unavailableRanges", ranges);
        return result;
    }

    /**
     * 校验租期是否与占用订单（出租+整备期）冲突，冲突时抛业务异常
     * 供加购/购物车改期/下单使用（前后端双校验，防止绕过前端提交被占租期）
     * @param carId     车辆ID
     * @param startDate 起租日期
     * @param endDate   还车日期（与前端展示口径一致：start 至 end 为所选租期）
     */
    public void validateRentAvailability(Long carId, LocalDate startDate, LocalDate endDate) {
        if (carId == null || startDate == null || endDate == null || !endDate.isAfter(startDate)) {
            return;
        }
        for (OrderItem it : occupiedItemsOf(carId)) {
            if (it.getStartDate() == null || it.getEndDate() == null) continue;
            // 不可用闭区间 [occStart, occEnd]：租期 + 整备期
            LocalDate occStart = it.getStartDate();
            LocalDate occEnd = it.getEndDate().plusDays(PREP_DAYS);
            // 所选租期 [start, end] 与不可用区间重叠即冲突
            if (!startDate.isAfter(occEnd) && !endDate.isBefore(occStart)) {
                throw new BusinessException("该车在所选日期内已被租出或在整备中，请更换租期");
            }
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
