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
    /** 关联的用户优惠券实例ID（member_coupon.id，下单使用优惠券时回写，对齐 v2 customer_order.coupon_user_id） */
    private Long couponUserId;
    /** 优惠券名称（订单展示用，从 coupon 表 JOIN 得到，非持久化） */
    @TableField(exist = false)
    private String couponName;
    private String city;
    private String store;
    private String contactName;
    private String contactPhone;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDelete;
}
