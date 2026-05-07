package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.controller;

import com.kuaiyukuaikuai.kuaiyutravel.common.utils.Result;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupService;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.Resource;

/**
 * 组团出发控制器
 */
@RestController
@RequestMapping("/group")
public class GroupController {

    @Resource
    private GroupService groupService;

    @PostMapping("/create")
    public Result createGroup(@RequestBody Group group) {
        return groupService.createGroup(group);
    }

    @GetMapping("/query/{groupNo}")
    public Result queryByGroupNo(@PathVariable("groupNo") String groupNo) {
        return groupService.queryByGroupNo(groupNo);
    }

    @PutMapping("/update")
    public Result updateGroup(@RequestBody Group group) {
        return groupService.updateGroup(group);
    }

    @DeleteMapping("/{id}")
    public Result deleteGroup(@PathVariable("id") Long id) {
        return groupService.deleteGroup(id);
    }

    /**
     * 【新增加：分页查询接口】
     * GET /group/list?current=1&size=10
     */
    @GetMapping("/list")
    public Result queryGroupPage(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return groupService.queryGroupPage(current, size);
    }
}