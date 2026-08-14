package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 车辆实体（数据来源于 car_rental.car_info 表）
 * typeName / statusName / cover / year / rating / rentalCount 在 car_info 中不存在，
 * 由 Mapper 的 @Select 查询或 Service 层补充。
 */
@Data
@TableName("car_rental.car_info")
public class Car {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String brand;
    private String series;
    private String type;

    /** car_info 无此字段，由 SQL CASE WHEN 或 Service 补充 */
    @TableField(exist = false)
    private String typeName;

    private BigDecimal dailyPrice;

    /** 周租折扣系数（7-29 天适用，如 0.92 表示 92% 价格） */
    private BigDecimal weeklyDiscount;

    /** 月租折扣系数（30 天及以上适用，如 0.85 表示 85% 价格） */
    private BigDecimal monthlyDiscount;

    /** 节假日溢价系数（节假日期间的日租金上浮，如 1.3 表示加价 30%） */
    private BigDecimal holidaySurcharge;

    private String status;

    /** car_info 无此字段，由 SQL 子查询或 Service 补充 */
    @TableField(exist = false)
    private String statusName;

    /** car_info 无此字段，由 Service 从 images 字段解析取首图补充 */
    @TableField(exist = false)
    private String cover;

    private String tags;
    private Integer isHot;
    private Integer isRecommend;
    private Integer seats;
    private String displacement;
    private String color;

    /** car_info 无 year 字段，由 YEAR(registration_date) 提取 */
    @TableField(exist = false)
    private Integer year;

    private Integer mileage;

    /** car_info 无此字段，固定 5.0 */
    @TableField(exist = false)
    private BigDecimal rating;

    /** car_info 无此字段，固定 0 */
    @TableField(exist = false)
    private Integer rentalCount;

    private String description;

    /** car_info 使用 created_at，映射到 createTime */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** car_info 使用 updated_at，映射到 updateTime */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDelete;

    /** car_info.images 字段：图片URL列表（JSON数组字符串），由 Service 解析为 imageList */
    private String images;

    // ---------- 非数据库字段 ----------
    /** 解析后的图片URL列表（API 输出，前端展示用） */
    @TableField(exist = false)
    private List<String> imageList;

    @TableField(exist = false)
    private CarConfig config;

    @TableField(exist = false)
    private List<String> tagList;

    /** 券后日租金（登录且拥有可用优惠券时计算，否则 null） */
    @TableField(exist = false)
    private BigDecimal couponPrice;

    /** 券后价对应的起步天数（满减券按最低满足天数折算时填充，前端展示"X天起"用） */
    @TableField(exist = false)
    private Integer couponMinDays;

    /** 券标签（如"免1天"，用于列表/卡片展示时长券提示；金额型券不填） */
    @TableField(exist = false)
    private String couponBadge;

    /** 最早可租日期：已出租/已预约时由 Service 关联 rental_order 计算得到（到期日 + 2 天整备期），否则 null */
    @TableField(exist = false)
    private LocalDate availableDate;

    /** 标记是否来自后台管理库（现在所有车辆均来自 car_rental.car_info，固定 true） */
    @TableField(exist = false)
    private Boolean fromAdminDb;
}
