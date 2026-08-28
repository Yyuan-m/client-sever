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

/**
 * 订单车辆明细（car_rental.customer_order_item）
 * 一个订单可包含多辆车，每个订单对应多条明细。
 * 主订单 customer_order 保留首车冗余字段（car_id/car_name/...）在此冗余一份，
 * 以兼容列表/首页/库存/评价等既有逻辑。
 */
@Data
@TableName("car_rental.customer_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联主订单ID customer_order.id */
    private Long orderId;

    private Long carId;
    private String carName;
    private String carCover;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer days;
    private BigDecimal dailyPrice;
    /** 该车租金小计(折扣前) */
    private BigDecimal rentAmount;
    /** 该车分摊优惠金额 */
    private BigDecimal discountAmount;
    /** 该车券后小计(应付) */
    private BigDecimal totalAmount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDelete;
}