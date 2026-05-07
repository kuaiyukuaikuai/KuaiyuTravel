package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.controller;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.PoiComment;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service.PoiCommentService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 地点评价 控制器
 */
@RestController
@RequestMapping("/poi-comment")
public class PoiCommentController {

    @Resource
    private PoiCommentService poiCommentService;

    /**
     * 发表评价
     * POST /poi-comment/add
     */
    @PostMapping("/add")
    public Result addComment(@RequestBody PoiComment poiComment) {
        if (poiComment.getPoiId() == null) {
            return Result.fail("地点ID不能为空");
        }
        if (poiComment.getContent() == null || poiComment.getContent().trim().isEmpty()) {
            return Result.fail("评价内容不能为空");
        }
        return poiCommentService.saveComment(poiComment);
    }

    /**
     * 分页查看某地点的评价列表
     * GET /poi-comment/list/123?current=1&size=5
     */
    @GetMapping("/list/{poiId}")
    public Result listComments(
            @PathVariable("poiId") Long poiId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "5") Integer size) {
        
        return poiCommentService.queryCommentPage(poiId, current, size);
    }
}