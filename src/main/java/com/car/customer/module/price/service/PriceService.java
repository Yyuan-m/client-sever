package com.car.customer.module.price.service;

import cn.hutool.core.date.ChineseDate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.car.customer.common.exception.BusinessException;
import com.car.customer.entity.Car;
import com.car.customer.entity.SystemConfig;
import com.car.customer.mapper.CarMapper;
import com.car.customer.mapper.SystemConfigMapper;
import com.car.customer.module.price.vo.PriceDetailVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 价格计算服务（前后端唯一价格计算入口，确保选车 → 购物车 → 结算 → 下单全流程价格一致）
 *
 * 价格构成：
 * 1. 节假日溢价：租期内的每一天，若该日为节假日（法定节假日 OR 周六日），日单价 = dailyPrice × holidaySurcharge
 * 2. 租期折扣：按总天数选择档位
 *    - 1-6 天：daily，factor=1.0
 *    - 7-29 天：weekly，factor=weeklyDiscount
 *    - 30+ 天：monthly，factor=monthlyDiscount
 * 3. rentAmount = subtotal × durationFactor
 * 4. totalAmount = rentAmount
 *
 * 节假日规则（来自 car_rental.sys_config 的 holiday_dates 配置）：
 * - 公历固定节日：MM-DD 格式（如 "01-01"、"10-01"）
 * - 农历节日：lunar:MM-DD 格式（如 "lunar:01-01" 表示农历正月初一，由 hutool ChineseDate 转为当年公历日期）
 * - 周六周日：后端自动识别，无需配置
 * 后端按当前年份动态生成完整节假日集合，年份变化时自动重新生成（跨年时自动刷新）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {

    private final CarMapper carMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");

    /** 原始节假日规则（来自数据库，公历 MM-DD 或 lunar:MM-DD） */
    private volatile List<String> holidayRules = Collections.emptyList();

    /** 各年份节假日集合缓存（含法定节假日 + 周六日，按年份分别缓存，跨年自动生成新年份） */
    private final ConcurrentHashMap<Integer, Set<LocalDate>> holidayCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadHolidayRules();
    }

    /**
     * 从 sys_config 加载节假日规则（月-日格式，不带年份）
     */
    private void loadHolidayRules() {
        try {
            SystemConfig config = systemConfigMapper.selectOne(
                    new LambdaQueryWrapper<SystemConfig>()
                            .eq(SystemConfig::getConfigKey, "holiday_dates"));
            if (config == null || config.getConfigValue() == null
                    || config.getConfigValue().isBlank()) {
                log.warn("未配置 holiday_dates，节假日溢价仅对周末生效");
                holidayRules = Collections.emptyList();
                return;
            }
            List<String> rules = objectMapper.readValue(
                    config.getConfigValue(), new TypeReference<List<String>>() {});
            holidayRules = rules != null ? rules : Collections.emptyList();
            log.info("已加载 {} 条节假日规则", holidayRules.size());
        } catch (Exception e) {
            log.error("加载节假日规则配置失败", e);
            holidayRules = Collections.emptyList();
        }
    }

    /**
     * 获取指定年份的节假日集合（法定节假日 + 周六日）
     * 法定节假日根据当前年份动态生成（公历拼接年份，农历用 hutool ChineseDate 转换）
     * 周六日由代码遍历当年所有周末自动识别
     */
    private Set<LocalDate> getHolidaySet(int year) {
        // 缓存命中直接返回
        Set<LocalDate> cached = holidayCache.get(year);
        if (cached != null) {
            return cached;
        }
        Set<LocalDate> set = new HashSet<>();

        // 1. 根据规则生成法定节假日（当年公历日期）
        for (String rule : holidayRules) {
            try {
                LocalDate date = resolveHolidayDate(rule, year);
                if (date != null) {
                    set.add(date);
                }
            } catch (Exception e) {
                log.warn("解析节假日规则失败: {} (year={})", rule, year, e);
            }
        }

        // 2. 自动加入当年的所有周六日
        LocalDate cursor = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);
        while (!cursor.isAfter(yearEnd)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                set.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }

        // 缓存并返回
        holidayCache.put(year, set);
        log.info("已生成 {} 年节假日集合，共 {} 天", year, set.size());
        return set;
    }

    /**
     * 将单条节假日规则解析为当年的公历日期
     * - "01-01" → 当年 1 月 1 日
     * - "lunar:01-01" → 当年农历正月初一对应的公历日期
     */
    private LocalDate resolveHolidayDate(String rule, int year) {
        if (rule == null || rule.isBlank()) return null;
        rule = rule.trim();
        if (rule.startsWith("lunar:")) {
            // 农历节日：lunar:MM-DD → ChineseDate(year, month, day).getGregorianDate()
            String md = rule.substring("lunar:".length());
            String[] parts = md.split("-");
            int lunarMonth = Integer.parseInt(parts[0]);
            int lunarDay = Integer.parseInt(parts[1]);
            ChineseDate chineseDate = new ChineseDate(year, lunarMonth, lunarDay);
            Date gregorianDate = chineseDate.getGregorianDate();
            return gregorianDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        // 公历固定节日：MM-DD → 当年公历日期
        // 注意：LocalDate.parse 需要 year，MM-dd 无法直接解析，用 split 方式
        String[] parts = rule.split("-");
        int month = Integer.parseInt(parts[0]);
        int day = Integer.parseInt(parts[1]);
        return LocalDate.of(year, month, day);
    }

    /**
     * 判断某天是否为节假日（法定节假日 OR 周六日）
     * 跨年时自动生成新年份的节假日集合（缓存按年份隔离）
     */
    private boolean isHoliday(LocalDate date) {
        return getHolidaySet(date.getYear()).contains(date);
    }

    /**
     * 计算单辆车在指定租期内的价格明细
     *
     * @param car       车辆实体（需含 dailyPrice/weeklyDiscount/monthlyDiscount/holidaySurcharge）
     * @param startDate 起租日期
     * @param endDate   结束日期（不含当日，与前端 days = endDate - startDate 一致）
     */
    public PriceDetailVO calculate(Car car, LocalDate startDate, LocalDate endDate) {
        if (car == null) {
            throw new BusinessException("车辆信息不能为空");
        }
        if (car.getDailyPrice() == null) {
            throw new BusinessException("车辆日租金未配置");
        }
        if (startDate == null || endDate == null) {
            throw new BusinessException("租期不能为空");
        }
        if (!endDate.isAfter(startDate)) {
            throw new BusinessException("结束日期必须晚于开始日期");
        }
        // 校验租期上限（车辆级 maxRentDays，null 表示不限；前后端双校验，防止绕过前端提交超长租期）
        long rentDaysCheck = ChronoUnit.DAYS.between(startDate, endDate);
        Integer maxRentDays = car.getMaxRentDays();
        if (maxRentDays != null && maxRentDays > 0 && rentDaysCheck > maxRentDays) {
            throw new BusinessException("车辆「" + car.getName() + "」单次最多租 " + maxRentDays + " 天");
        }

        // 兜底系数（数据库默认 1.000）
        BigDecimal dailyPrice = car.getDailyPrice();
        BigDecimal weeklyDiscount = car.getWeeklyDiscount() != null ? car.getWeeklyDiscount() : BigDecimal.ONE;
        BigDecimal monthlyDiscount = car.getMonthlyDiscount() != null ? car.getMonthlyDiscount() : BigDecimal.ONE;
        BigDecimal holidaySurcharge = car.getHolidaySurcharge() != null ? car.getHolidaySurcharge() : BigDecimal.ONE;

        // 1. 遍历租期内每一天
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
        int normalDays = 0;
        int holidayDays = 0;
        List<String> holidayDates = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal holidayExtra = BigDecimal.ZERO; // 节假日多收的部分

        // 注意：endDate 不含当日，遍历 [startDate, endDate)
        for (LocalDate d = startDate; d.isBefore(endDate); d = d.plusDays(1)) {
            boolean isHoliday = isHoliday(d);
            if (isHoliday) {
                holidayDays++;
                holidayDates.add(d.format(DATE_FMT));
                // 节假日日单价 = dailyPrice × holidaySurcharge
                BigDecimal dayPrice = dailyPrice.multiply(holidaySurcharge);
                subtotal = subtotal.add(dayPrice);
                // 多收部分 = dailyPrice × (holidaySurcharge - 1)
                holidayExtra = holidayExtra.add(dailyPrice.multiply(holidaySurcharge.subtract(BigDecimal.ONE)));
            } else {
                normalDays++;
                subtotal = subtotal.add(dailyPrice);
            }
        }

        // 2. 按总天数确定档位
        String durationTier;
        String durationTierName;
        BigDecimal durationFactor;
        int days = (int) totalDays;
        if (days >= 30) {
            durationTier = "monthly";
            durationTierName = "月租";
            durationFactor = monthlyDiscount;
        } else if (days >= 7) {
            durationTier = "weekly";
            durationTierName = "周租";
            durationFactor = weeklyDiscount;
        } else {
            durationTier = "daily";
            durationTierName = "日租";
            durationFactor = BigDecimal.ONE;
        }

        // 3. 计算租金、应付
        BigDecimal rentAmount = subtotal.multiply(durationFactor).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = rentAmount;
        BigDecimal discountAmount = subtotal.subtract(rentAmount).setScale(2, RoundingMode.HALF_UP);

        // 4. 组装 VO
        PriceDetailVO vo = new PriceDetailVO();
        vo.setCarId(car.getId());
        vo.setCarName(car.getName());
        vo.setDailyPrice(dailyPrice);
        vo.setWeeklyDiscount(weeklyDiscount);
        vo.setMonthlyDiscount(monthlyDiscount);
        vo.setHolidaySurcharge(holidaySurcharge);
        vo.setStartDate(startDate.format(DATE_FMT));
        vo.setEndDate(endDate.format(DATE_FMT));
        vo.setDays(days);
        vo.setNormalDays(normalDays);
        vo.setHolidayDays(holidayDays);
        vo.setHolidayDates(holidayDates);
        vo.setDurationTier(durationTier);
        vo.setDurationTierName(durationTierName);
        vo.setDurationFactor(durationFactor);
        vo.setHolidaySurchargeAmount(holidayExtra.setScale(2, RoundingMode.HALF_UP));
        vo.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        vo.setDiscountAmount(discountAmount);
        vo.setRentAmount(rentAmount);
        vo.setTotalAmount(totalAmount);
        return vo;
    }

    /**
     * 按 carId + 租期计算价格明细（对外接口使用）
     */
    public PriceDetailVO calculate(Long carId, LocalDate startDate, LocalDate endDate) {
        Car car = carMapper.selectAdminCarById(carId);
        if (car == null) {
            throw new BusinessException("车辆不存在");
        }
        return calculate(car, startDate, endDate);
    }

    /**
     * 批量计算多辆车的价格明细（购物车结算页使用）
     * @param items 购物车项列表，每项含 carId / startDate / endDate
     */
    public List<PriceDetailVO> calculateBatch(List<CartItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<PriceDetailVO> result = new ArrayList<>(items.size());
        for (CartItem item : items) {
            Car car = carMapper.selectAdminCarById(item.getCarId());
            if (car == null) {
                throw new BusinessException("车辆不存在: " + item.getCarId());
            }
            LocalDate start = LocalDate.parse(item.getStartDate(), DATE_FMT);
            LocalDate end = LocalDate.parse(item.getEndDate(), DATE_FMT);
            result.add(calculate(car, start, end));
        }
        return result;
    }

    /**
     * 购物车结算项（用于 calculateBatch 入参）
     */
    @lombok.Data
    public static class CartItem {
        private Long carId;
        private String startDate;
        private String endDate;
    }
}
