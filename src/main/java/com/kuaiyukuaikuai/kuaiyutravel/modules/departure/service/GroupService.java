package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;

/**
 * 组团业务接口
 * 所有方法统一使用 groupNo（String类型）作为唯一标识
 */
public interface GroupService extends IService<Group> {

    /**
     * 发起组团
     */
    Result createGroup(Group group);

    /**
     * 根据编号查询组团详情
     */
    Result queryByGroupNo(String groupNo);

    /**
     * 修改组团信息（支持局部更新，需校验团长权限）
     */
    Result updateGroup(Group group);

    /**
     * 解散/删除组团（通过团号，需校验团长权限）
     */
    Result deleteGroupByNo(String groupNo);

    /**
     * 分页查询所有组团信息 (招募中)
     * @param current 当前页码
     * @param size 每页大小
     */
    Result queryGroupPage(Integer current, Integer size);
}
