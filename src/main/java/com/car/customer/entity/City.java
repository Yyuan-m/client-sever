package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 取车城市（对应 car_rental.customer_city，与 customer_store 通过 city_id 关联）
 */
@Data
@TableName("car_rental.customer_city")
public class City {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Integer sort;
    private Integer status;

    @TableLogic
    private Integer isDelete;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
