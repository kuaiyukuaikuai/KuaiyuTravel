package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.SystemConstants;
import org.redisson.api.RedissonClient;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * 地点控制器
 * 处理地点相关的请求
 * 
 * @author 快鱼快快
 * @since 2026-04-17
 */
@RestController
@RequestMapping("/poi")
public class PoiController {

    @Resource
    public PoiService poiService;
    
    @Resource
    public RedissonClient redissonClient;

    /**
     * 根据id查询地点信息
     * 
     * @param id 地点id
     * @return 地点详情数据
     */
    @GetMapping("/{id}")
    public Result queryPoiById(@PathVariable("id") Long id) {
        return poiService.queryPoiById(id);
    }

    /**
     * 新增地点信息
     * 
     * @param poi 地点数据
     * @return 地点id
     */
    @PostMapping
    public Result savePoi(@RequestBody Poi poi) {
        poiService.savePoiWithBloomFilter(poi);
        return Result.ok(poi.getId());
    }

    /**
     * 更新地点信息
     * 
     * @param poi 地点数据
     * @return 更新结果
     */
    @PutMapping
    public Result updatePoi(@RequestBody Poi poi) {
        // 写入数据库
        return poiService.update(poi);
    }

    /**
     * 根据地点类型分页查询地点信息
     * 
     * @param typeId 地点类型
     * @param current 页码
     * @param x 经度
     * @param y 纬度
     * @return 地点列表
     */
    @GetMapping("/of/type")
    public Result queryPoiByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "x", required = false) Double x,
            @RequestParam(value = "y", required = false) Double y
    ) {
        return poiService.queryPoiByType(typeId, current, x, y);
    }

    /**
     * 根据地点名称关键字分页查询地点信息
     * 
     * @param name 地点名称关键字
     * @param current 页码
     * @return 地点列表
     */
    @GetMapping("/of/name")
    public Result queryPoiByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Poi> page = poiService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .orderByDesc("score")
                .orderByDesc("id")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}