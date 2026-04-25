package com.kuaiyukuaikuai.kuaiyutravel.service;

import com.kuaiyukuaikuai.kuaiyutravel.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.baomidou.mybatisplus.extension.service.IService;

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
}