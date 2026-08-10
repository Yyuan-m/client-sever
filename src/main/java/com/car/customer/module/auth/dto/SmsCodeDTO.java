package com.car.customer.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SmsCodeDTO {

    @NotBlank(message = "手机号不能为空")
    private String phone;
}
