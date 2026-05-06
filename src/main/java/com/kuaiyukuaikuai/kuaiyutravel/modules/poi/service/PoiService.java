package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service;

import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.dto.Result;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 景点服务接口
 */
public interface PoiService extends IService<Poi> {

    /**
     * 根据ID查询景点
     * @param id 景点ID
     * @return 景点详情
     */
    Result queryPoiById(Long id);

    /**
     * 更新景点信息
     * @param poi 景点信息
     * @return 操作结果
     */
    Result update(Poi poi);

    /**
     * 根据类型查询景点
     * @param typeId 类型ID
     * @param current 当前页码
     * @param x 经度
     * @param y 纬度
     * @return 景点列表
     */
    Result queryPoiByType(Integer typeId, Integer current, Double x, Double y);

    /**
     * 使用布隆过滤器保存景点
     * @param poi 景点信息
     */
    void savePoiWithBloomFilter(Poi poi);

    /**
     * 批量保存景点并同步到布隆过滤器
     * @param poiList 景点列表
     */
    void savePoiBatchWithBloomFilter(List<Poi> poiList);
}