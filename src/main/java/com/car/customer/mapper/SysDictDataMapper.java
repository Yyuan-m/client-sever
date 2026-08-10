package com.car.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.car.customer.entity.SysDictData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典数据 Mapper（跨库 car_rental.sys_dict_data）
 */
@Mapper
public interface SysDictDataMapper extends BaseMapper<SysDictData> {
}
