package com.car.customer.module.price.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 价格明细 VO（前后端统一价格计算结果）
 *
 * 计算规则：
 * 1. 遍历租期内每一天，节假日当天日单价 = dailyPrice × holidaySurcharge
 * 2. 小计 subtotal = Σ 每天日单价
 * 3. 按总天数确定 durationTier / durationFactor：
 *    - 1-6 天：daily（factor=1.0）
 *    - 7-29 天：weekly（factor=weeklyDiscount）
 *    - 30+ 天：monthly（factor=monthlyDiscount）
 * 4. rentAmount = subtotal × durationFactor
 * 4. totalAmount = rentAmount
 */
@Data
public class PriceDetailVO {

    /** 车辆ID（购物车汇总时为 null） */
    private Long carId;

    /** 车辆名称（购物车汇总时为 null） */
    private String carName;

    /** 日租金 */
    private BigDecimal dailyPrice;

    /** 周租折扣系数 */
    private BigDecimal weeklyDiscount;

    /** 月租折扣系数 */
    private BigDecimal monthlyDiscount;

    /** 节假日溢价系数 */
    private BigDecimal holidaySurcharge;

    /** 起租日期（YYYY-MM-DD） */
    private String startDate;

    /** 结束日期（YYYY-MM-DD，不含当日） */
    private String endDate;

    /** 总租赁天数 */
    private Integer days;

    /** 普通天数 */
    private Integer normalDays;

    /** 节假日天数 */
    private Integer holidayDays;

    /** 落在节假日范围内的日期列表（YYYY-MM-DD） */
    private List<String> holidayDates;

    /** 租期档位：daily / weekly / monthly */
    private String durationTier;

    /** 租期档位中文名 */
    private String durationTierName;

    /** 折扣系数（1.0 / weeklyDiscount / monthlyDiscount） */
    private BigDecimal durationFactor;

    /** 节假日溢价金额（节假日多收的部分） */
    private BigDecimal holidaySurchargeAmount;

    /** 折扣前小计 = Σ 每天日单价（含节假日溢价） */
    private BigDecimal subtotal;

    /** 折扣优惠金额 = subtotal - rentAmount */
    private BigDecimal discountAmount;

    /** 租金总额（折后） */
    private BigDecimal rentAmount;

    /** 应付总额（= 租金，押金概念已移除） */
    private BigDecimal totalAmount;
}
