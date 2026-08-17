package com.car.customer.module.carousel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.car.customer.entity.Carousel;
import com.car.customer.mapper.CarouselMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CarouselService {

    private final CarouselMapper carouselMapper;

    /**
     * 获取首页启用的轮播图列表
     * 过滤条件：
     * 1) status = 1（启用）
     * 2) start_time 为空 或 start_time <= 当前时间（已到上架时间）
     * 3) end_time 为空 或 end_time >= 当前时间（未到下架时间）
     * 排序：sort_order 升序
     */
    public List<Carousel> getActiveCarousel() {
        LocalDateTime now = LocalDateTime.now();
        return carouselMapper.selectList(new LambdaQueryWrapper<Carousel>()
                .eq(Carousel::getStatus, 1)
                .and(w -> w.isNull(Carousel::getStartTime).or().le(Carousel::getStartTime, now))
                .and(w -> w.isNull(Carousel::getEndTime).or().ge(Carousel::getEndTime, now))
                .orderByAsc(Carousel::getSortOrder));
    }
}
