package com.car.customer.module.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateOrderDTO {

    private String city;
    private String store;

    @NotBlank(message = "联系人不能为空")
    private String name;

    @NotBlank(message = "联系电话不能为空")
    private String phone;

    /** 用户优惠券实例ID（member_coupon.id，下单使用的券，对齐 v2 customer_order.coupon_user_id） */
    private Long couponUserId;

    @NotEmpty(message = "下单车辆不能为空")
    private List<CartItemDTO> items;

    @Data
    public static class CartItemDTO {
        private Long carId;
        private String carName;
        private String cover;
        private String startDate;
        private String endDate;
        private Integer days;
        private BigDecimal dailyPrice;
    }
}
