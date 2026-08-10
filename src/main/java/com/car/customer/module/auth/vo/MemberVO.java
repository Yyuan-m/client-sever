package com.car.customer.module.auth.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MemberVO {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private String level;
    private String levelName;
    private Integer creditScore;
    private Integer totalOrders;
    private BigDecimal totalSpent;

    // ---------- 详细资料 ----------
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
    private String province;
    private String city;
    private String address;
    private LocalDateTime lastLoginTime;
}
