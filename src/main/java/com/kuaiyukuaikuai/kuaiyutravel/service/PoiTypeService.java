package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.PoiType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 景点类型服务接口
 */
public interface PoiTypeService extends IService<PoiType> {

    /**
     * 查询类型列表
     * @return 类型列表
     */
    Result queryTypeList();
}