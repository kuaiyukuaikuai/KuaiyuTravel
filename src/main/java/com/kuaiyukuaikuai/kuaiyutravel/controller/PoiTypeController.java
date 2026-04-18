package com.kuaiyukuaikuai.kuaiyutravel.controller;


import com.kuaiyukuaikuai.kuaiyutravel.dto.Result;
import com.kuaiyukuaikuai.kuaiyutravel.entity.PoiType;
import com.kuaiyukuaikuai.kuaiyutravel.service.PoiTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 0
 * @since 2026-04-17
 */
@RestController
@RequestMapping("/poi-type")
public class PoiTypeController {
    @Resource
    private PoiTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        return typeService.queryTypeList();
    }
}