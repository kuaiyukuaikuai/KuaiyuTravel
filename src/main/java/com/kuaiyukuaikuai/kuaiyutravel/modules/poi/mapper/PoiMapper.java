package com.kuaiyukuaikuai.kuaiyutravel.modules.poi.mapper;

import com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
* @author 0
* @description 针对表【tb_poi】的数据库操作Mapper
* @createDate 2026-04-17 11:08:15
* @Entity com.kuaiyukuaikuai.kuaiyutravel.modules.poi.entity.Poi
*/
public interface PoiMapper extends BaseMapper<Poi> {

    @Select("select id from tb_poi")
    List<Long> selectIdList();

    // 写在 PoiMapper.java 里
    @Update("UPDATE tb_poi SET comments = comments + 1 WHERE id = #{poiId}")
    int incrCommentCount(Long poiId);
}




