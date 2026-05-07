package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.PoiType;
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