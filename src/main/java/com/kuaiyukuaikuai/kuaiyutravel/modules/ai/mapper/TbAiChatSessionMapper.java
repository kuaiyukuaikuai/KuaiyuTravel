package com.kuaiyukuaikuai.kuaiyutravel.modules.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.ai.entity.TbAiChatSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TbAiChatSessionMapper extends BaseMapper<TbAiChatSession> {
    // 基础的 CRUD 已经由 BaseMapper 提供，此处暂时无需手写 SQL
}