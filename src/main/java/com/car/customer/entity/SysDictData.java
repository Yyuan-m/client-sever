package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 字典数据实体（跨库 car_rental.sys_dict_data）
 * 供 C 端获取车辆类型、品牌等字典数据
 */
@Data
@TableName("car_rental.sys_dict_data")
public class SysDictData {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String dictType;

    private String dictValue;

    private String dictLabel;

    private Integer sortOrder;

    private Integer status;

    private String remark;
}
