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
    /** 已完成订单数（会员等级按此口径计算，区别于 totalOrders 的全部订单数） */
    private Integer completedOrders;
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

    // ---------- 实名认证状态 ----------
    /** unverified未认证/pending审核中/verified已认证/rejected已驳回 */
    private String verifyStatus;
    /** 最近一次认证驳回原因 */
    private String verifyRejectReason;
    /** 最近一次提交认证时间 */
    private LocalDateTime verifySubmitTime;
}
