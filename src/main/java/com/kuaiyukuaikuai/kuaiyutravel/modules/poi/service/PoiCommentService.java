package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.PoiComment;

public interface PoiCommentService extends IService<PoiComment> {

    /**
     * 发表评价
     */
    Result saveComment(PoiComment poiComment);

    /**
     * 分页查询地点的评价列表 (带用户信息)
     */
    Result queryCommentPage(Long poiId, Integer current, Integer size);
}