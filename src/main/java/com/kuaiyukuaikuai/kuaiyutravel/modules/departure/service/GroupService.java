package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;

/**
 * 组团业务接口
 * 所有方法统一使用 groupNo（String类型）作为唯一标识
 */
public interface GroupService extends IService<Group> {

    /**
     * 发起组团
     *
     * @return 创建的组团编号（groupNo）
     */
    String createGroup(Group group);

    /**
     * 根据编号查询组团详情
     *
     * @return 组团详情
     */
    Group queryByGroupNo(String groupNo);

    /**
     * 修改组团信息（支持局部更新，需校验团长权限）
     */
    void updateGroup(Group group);

    /**
     * 解散/删除组团（通过团号，需校验团长权限）
     */
    void deleteGroupByNo(String groupNo);

    /**
     * 分页查询所有组团信息 (招募中)
     *
     * @param current 当前页码
     * @param size    每页大小
     * @return 分页数据
     */
    Page<Group> queryGroupPage(Integer current, Integer size);
}
