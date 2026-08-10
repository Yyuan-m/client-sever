package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户优惠券表（C端领取实例）
 * 表位于 car_rental_customer 库，与 car_rental.coupon 跨库关联
 */
@Data
@TableName("member_coupon")
public class MemberCoupon {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID（关联 member.id） */
    private Long memberId;

    /** 优惠券ID（关联 car_rental.coupon.id） */
    private Long couponId;

    /** unused未使用/locked已锁定/used已使用/expired已过期 */
    private String status;

    /** 用户券码（核销用，全局唯一） */
    private String code;

    /** 领取时间 */
    private LocalDateTime claimTime;

    /** 该券实例过期时间 */
    private LocalDateTime expireTime;

    /** 核销时间 */
    private LocalDateTime useTime;

    /** 关联订单ID（核销时回写） */
    private Long orderId;

    /** 领取来源 manual/register/order/promotion/system */
    private String source;

    @TableLogic
    @TableField("is_delete")
    private Integer isDelete;

    @Version
    private Integer version;

    // ============ 非持久化扩展字段（查询时 JOIN coupon 填充） ============

    /** 券名称（来自 coupon 表） */
    @TableField(exist = false)
    private String couponName;

    /** 券类型（来自 coupon 表） */
    @TableField(exist = false)
    private String couponType;

    /** 券类型名称 */
    @TableField(exist = false)
    private String couponTypeName;

    /** 优惠值 */
    @TableField(exist = false)
    private BigDecimal couponValue;

    /** 最低消费 */
    @TableField(exist = false)
    private BigDecimal minAmount;

    /** 折扣封顶 */
    @TableField(exist = false)
    private BigDecimal discountCap;

    /** 适用范围 */
    @TableField(exist = false)
    private String applyScope;
}
