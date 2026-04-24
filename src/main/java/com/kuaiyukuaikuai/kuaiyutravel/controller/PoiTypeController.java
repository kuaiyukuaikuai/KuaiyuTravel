package com.kuaiyukuaikuai.kuaiyutravel.controller;

import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.service.PoiTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;

/**
 * 地点类型控制器
 * 处理地点类型相关的请求
 * 
 * @author 0
 * @since 2026-04-17
 */
@RestController
@RequestMapping("/poi-type")
public class PoiTypeController {
    @Resource
    private PoiTypeService typeService;

    /**
     * 查询地点类型列表
     * 
     * @return 地点类型列表
     */
    @GetMapping("list")
    public Result queryTypeList() {
        return typeService.queryTypeList();
    }
}