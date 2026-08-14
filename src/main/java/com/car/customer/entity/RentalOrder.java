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

@Data
@TableName("car_rental.customer_order")
public class RentalOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long memberId;
    private Long carId;
    private String carName;
    private String carCover;
    private String status;
    private String statusName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer days;
    private BigDecimal dailyPrice;
    private BigDecimal rentAmount;
    private BigDecimal couponDiscount;
    private BigDecimal totalAmount;
    /**
     * 关联的用户优惠券实例ID（member_coupon.id，v3 支持多张可叠加券，逗号分隔存储）
     * 旧数据为单 ID（bigint 已迁移为 varchar），兼容读取
     */
    private String couponUserId;
    /** 优惠券名称（订单展示用，从 coupon 表 JOIN 得到，非持久化，多张用顿号分隔） */
    @TableField(exist = false)
    private String couponName;
    private String city;
    private String store;
    private String contactName;
    private String contactPhone;

    /**
     * 评价状态：unreviewed=待评价（订单完成时置为待评价），reviewed=已评价（首评后），final_reviewed=已追评（追评后，每单最多2次）
     * 仅在 status=completed 时有意义；pending/renting/cancelled 阶段为 null
     */
    private String reviewStatus;
    /** 评价状态中文名：待评价 / 已评价 / 已追评 */
    private String reviewStatusName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDelete;
}
