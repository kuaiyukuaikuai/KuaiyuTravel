package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author 0
* @description 针对表【tb_poi】的数据库操作Service
* @createDate 2026-04-17 11:08:15
*/
public interface PoiService extends IService<Poi> {

    Result queryPoiById(Long id);

    Result update(Poi poi);

    Result queryPoiByType(Integer typeId, Integer current, Double x, Double y);
}

