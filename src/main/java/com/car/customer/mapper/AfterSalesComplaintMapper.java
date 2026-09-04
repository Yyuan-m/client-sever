package com.car.customer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.car.customer.entity.AfterSalesComplaint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 售后投诉 Mapper（跨库 car_rental.after_sales_complaint）
 */
@Mapper
public interface AfterSalesComplaintMapper extends BaseMapper<AfterSalesComplaint> {
}
