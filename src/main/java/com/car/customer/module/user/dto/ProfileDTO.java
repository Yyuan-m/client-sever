package com.car.customer.module.user.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ProfileDTO {

    // ---------- 基础资料 ----------
    private String avatar;
    private String nickname;
    private String phone;
    private String email;

    // ---------- 实名认证 / 驾驶证 ----------
    private String realName;
    private Integer gender;
    private LocalDate birthday;
    private String idCard;
    private String idCardFrontImg;
    private String idCardBackImg;
    private String driverLicenseNo;
    private String driverLicenseType;
    private String driverLicenseFrontImg;
    private String driverLicenseBackImg;
    private LocalDate driverLicenseExpireDate;

    // ---------- 地址 ----------
    private String province;
    private String city;
    private String address;
}
