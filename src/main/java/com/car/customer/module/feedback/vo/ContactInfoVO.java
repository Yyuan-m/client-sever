package com.car.customer.module.feedback.vo;

import lombok.Data;

/**
 * 预约/留言的联系人完整信息（仅本人可查，用于前端"查看详情"展开完整姓名/手机号）
 */
@Data
public class ContactInfoVO {

    private Long id;

    /** 联系人完整姓名（未脱敏） */
    private String name;

    /** 联系手机号（未脱敏） */
    private String phone;
}
