package com.car.customer.module.cart.dto;

import lombok.Data;

@Data
public class UpdateCartDTO {

    private String startDate;

    private String endDate;

    private Integer days;

    private Integer quantity;
}
