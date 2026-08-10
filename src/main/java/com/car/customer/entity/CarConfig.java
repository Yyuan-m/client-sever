package com.car.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("car_rental.car_config")
public class CarConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long carId;
    private String power;
    private String transmission;
    private String fuel;
    private String rangeKm;
    private String interior;
    private String safety;
    private String entertainment;
}
