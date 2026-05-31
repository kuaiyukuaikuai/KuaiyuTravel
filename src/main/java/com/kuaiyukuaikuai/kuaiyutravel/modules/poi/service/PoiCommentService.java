package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.PoiComment;
import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.vo.PoiCommentVO;

public interface PoiCommentService extends IService<PoiComment> {

    /**
     * 发表评价
     */
    void saveComment(PoiComment poiComment);

    /**
     * 分页查询地点的评价列表 (带用户信息)
     */
    Page<PoiCommentVO> queryCommentPage(Long poiId, Integer current, Integer size);
}
