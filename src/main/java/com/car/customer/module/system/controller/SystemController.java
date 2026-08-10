package com.car.customer.module.system.controller;

import com.car.customer.common.result.Result;
import com.car.customer.entity.Advantage;
import com.car.customer.entity.Review;
import com.car.customer.entity.Store;
import com.car.customer.entity.SysDictData;
import com.car.customer.module.system.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.ok(systemService.getSystemConfig());
    }

    @GetMapping("/stores")
    public Result<List<Store>> stores() {
        return Result.ok(systemService.getStores());
    }

    @GetMapping("/advantages")
    public Result<List<Advantage>> advantages() {
        return Result.ok(systemService.getAdvantages());
    }

    @GetMapping("/reviews")
    public Result<List<Review>> reviews() {
        return Result.ok(systemService.getReviews());
    }

    /**
     * 按类型查询字典数据（公开接口，供 C 端获取车型/品牌等选项）
     * @param dictType 字典类型，如 vehicle_type / vehicle_brand
     */
    @GetMapping("/dict/{dictType}")
    public Result<List<SysDictData>> dictByType(@PathVariable String dictType) {
        return Result.ok(systemService.getDictByType(dictType));
    }
}
