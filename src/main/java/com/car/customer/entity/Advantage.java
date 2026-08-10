package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("car_rental.customer_advantage")
public class Advantage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String icon;
    private String title;
    private String description;
    private Integer sortOrder;

    @TableLogic
    private Integer isDelete;
}
