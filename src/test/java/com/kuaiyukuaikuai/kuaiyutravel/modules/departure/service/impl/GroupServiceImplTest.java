package com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.impl;

import com.kuaiyukuaikuai.kuaiyutravel.common.exception.BusinessException;
import com.kuaiyukuaikuai.kuaiyutravel.common.exception.ErrorCode;
import com.kuaiyukuaikuai.kuaiyutravel.common.utils.UserHolder;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.entity.Group;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.mapper.GroupMapper;
import com.kuaiyukuaikuai.kuaiyutravel.modules.departure.service.GroupMemberService;
import com.kuaiyukuaikuai.kuaiyutravel.modules.my.vo.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 组团服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupMapper groupMapper;

    @Mock
    private GroupMemberService groupMemberService;

    @InjectMocks
    private GroupServiceImpl groupService;

    @BeforeEach
    void setUp() {
        UserDTO user = new UserDTO();
        user.setId(1L);
        UserHolder.saveUser(user);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void createGroup_ShouldThrowException_WhenTitleEmpty() {
        // Given
        Group group = new Group();
        group.setMaxPeople(10);

        // Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> groupService.createGroup(group));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void createGroup_ShouldThrowException_WhenMaxPeopleInvalid() {
        // Given
        Group group = new Group();
        group.setTitle("测试团");
        group.setMaxPeople(0);

        // Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> groupService.createGroup(group));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void queryByGroupNo_ShouldThrowException_WhenGroupNoEmpty() {
        // Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> groupService.queryByGroupNo(""));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }

    @Test
    void updateGroup_ShouldThrowException_WhenGroupIdAndGroupNoBothNull() {
        // Given
        Group group = new Group();

        // Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> groupService.updateGroup(group));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), exception.getCode());
    }
}
