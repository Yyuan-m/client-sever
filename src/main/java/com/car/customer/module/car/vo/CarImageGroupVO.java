package com.car.customer.module.car.vo;

import lombok.Data;

import java.util.List;

/**
 * 车辆素材图片分组 VO
 * 按 category 字段分组后的图片列表，用于车辆详情页分类展示
 */
@Data
public class CarImageGroupVO {

    /** 图片分类名称：外观 / 内饰 / 宣传图 等 */
    private String category;

    /** 该分类下的图片 URL 列表（相对路径，前端通过 resolveAdminImage 解析） */
    private List<String> images;
}
