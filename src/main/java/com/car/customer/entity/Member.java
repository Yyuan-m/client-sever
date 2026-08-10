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

@Data
@TableName("member")
public class Member {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String phone;
    private String password;
    private String nickname;
    private String realName;
    private Integer gender;
    private LocalDate birthday;
    private String email;
    private String avatar;
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
    private String level;
    private String levelName;
    private Integer creditScore;
    private Integer totalOrders;
    private BigDecimal totalSpent;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDelete;
}
