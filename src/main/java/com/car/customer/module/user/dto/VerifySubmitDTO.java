package com.car.customer.module.user.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 提交实名认证 DTO（触发人工审核流程）
 */
@Data
public class VerifySubmitDTO {

    private String realName;
    private Integer gender;
    private String idCard;
    private LocalDate birthDate;
    private String driverLicenseNo;
    private String driverLicenseType;
    private LocalDate driverLicenseExpireDate;
    private String idCardFrontImg;
    private String idCardBackImg;
    private String driverLicenseFrontImg;
    private String driverLicenseBackImg;
}
