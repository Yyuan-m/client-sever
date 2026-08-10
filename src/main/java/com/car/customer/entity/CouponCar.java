package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 优惠券-车辆关联表（一张券可关联多辆车，一对多）
 * 跨库映射：表位于 car_rental 库
 */
@Data
@TableName("car_rental.coupon_car")
public class CouponCar {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long couponId;

    private Long carId;

    private LocalDateTime createdAt;
}
