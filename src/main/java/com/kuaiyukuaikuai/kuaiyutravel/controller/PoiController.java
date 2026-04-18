package com.kuaiyukuaikuai.kuaiyutravel.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.Poi;
import com.kuaiyukuaikuai.kuaiyutravel.service.PoiService;
import com.kuaiyukuaikuai.kuaiyutravel.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/poi")
public class PoiController {

    @Resource
    public PoiService poiService;

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
        // 写入数据库
        poiService.save(poi);
        // 返回店铺id
        return Result.ok(poi.getId());
    }

    /**
     * 更新地点信息
     *
     * @param poi 地点数据
     * @return 无
     */
    @PutMapping
    public Result updatePoi(@RequestBody Poi poi) {
        // 写入数据库
        return poiService.update(poi);
    }

    /**
     * 根据地点类型分页查询地点信息
     *
     * @param typeId  地点类型
     * @param current 页码
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
     * @param name    地点名称关键字
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
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
