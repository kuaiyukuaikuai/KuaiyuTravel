package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.PoiType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 0
* @description 针对表【tb_poi_type】的数据库操作Service
* @createDate 2026-04-17 11:08:15
*/
public interface PoiTypeService extends IService<PoiType> {

    Result queryTypeList();
}
