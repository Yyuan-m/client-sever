package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 会员实名认证记录：C端每次提交认证生成一条，后台据此人工审核
 */
@Data
@TableName("member_verify_record")
public class MemberVerifyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memberId;
    private String realName;
    private String idCard;
    private LocalDate birthDate;
    private String driverLicenseNo;
    private String driverLicenseType;
    private LocalDate driverLicenseExpireDate;
    private String idCardFrontImg;
    private String idCardBackImg;
    private String driverLicenseFrontImg;
    private String driverLicenseBackImg;

    /** 记录状态: pending待审核/approved已通过/rejected已驳回 */
    private String status;
    private String rejectReason;
    /** 审核人（后台管理员） */
    private String reviewer;
    private LocalDateTime reviewTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDelete;
}
