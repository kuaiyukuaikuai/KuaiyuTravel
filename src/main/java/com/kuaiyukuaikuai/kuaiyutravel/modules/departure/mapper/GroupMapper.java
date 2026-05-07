package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GroupMapper extends BaseMapper<Group> {

    @Update("UPDATE tb_group SET current_people = current_people + 1 " +
            "WHERE id = #{groupId} AND current_people < max_people AND status = 0")
    int incrementCurrentPeople(Long groupId);

    @Update("UPDATE tb_group SET current_people = current_people - 1 " +
            "WHERE id = #{groupId} AND current_people > 0")
    int decrementCurrentPeople(Long groupId);
}