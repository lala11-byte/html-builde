package com.htmlbuilder.user.service.impl;

import com.htmlbuilder.common.exception.BusinessException;
import com.htmlbuilder.user.dto.BindProjectDTO;
import com.htmlbuilder.user.dto.ProjectRelationDTO;
import com.htmlbuilder.user.entity.UserProject;
import com.htmlbuilder.user.mapper.UserProjectMapper;
import com.htmlbuilder.user.service.UserProjectService;
import com.htmlbuilder.user.vo.UserProjectVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserProjectServiceImpl implements UserProjectService {

    private static final String DEFAULT_ROLE = "OWNER";

    private final UserProjectMapper userProjectMapper;

    public UserProjectServiceImpl(UserProjectMapper userProjectMapper) {
        this.userProjectMapper = userProjectMapper;
    }

    @Override
    public List<UserProjectVO> listMine(Long userId) {
        return userProjectMapper.selectByUserId(userId).stream().map(this::toVO).toList();
    }

    @Override
    public UserProjectVO bind(Long userId, BindProjectDTO dto) {
        UserProject existing = userProjectMapper.selectByUserAndProject(userId, dto.getProjectId());
        if (existing != null) {
            return toVO(existing);
        }

        UserProject relation = new UserProject();
        relation.setUserId(userId);
        relation.setProjectId(dto.getProjectId());
        relation.setRole(dto.getRole() == null ? DEFAULT_ROLE : dto.getRole());
        relation.setStarred(false);
        relation.setPinned(false);
        userProjectMapper.insert(relation);
        return toVO(relation);
    }

    @Override
    public UserProjectVO update(Long userId, Long projectId, ProjectRelationDTO dto) {
        UserProject relation = requireRelation(userId, projectId);
        if (dto.getRole() != null) {
            relation.setRole(dto.getRole());
        }
        if (dto.getStarred() != null) {
            relation.setStarred(dto.getStarred());
        }
        if (dto.getPinned() != null) {
            relation.setPinned(dto.getPinned());
        }
        userProjectMapper.updateById(relation);
        return toVO(relation);
    }

    @Override
    public void unbind(Long userId, Long projectId) {
        UserProject relation = requireRelation(userId, projectId);
        userProjectMapper.deleteById(relation.getId());
    }

    private UserProject requireRelation(Long userId, Long projectId) {
        UserProject relation = userProjectMapper.selectByUserAndProject(userId, projectId);
        if (relation == null) {
            throw new BusinessException(1003, "该项目未关联到当前用户");
        }
        return relation;
    }

    private UserProjectVO toVO(UserProject relation) {
        UserProjectVO vo = new UserProjectVO();
        vo.setId(relation.getId());
        vo.setUserId(relation.getUserId());
        vo.setProjectId(relation.getProjectId());
        vo.setRole(relation.getRole());
        vo.setStarred(relation.getStarred());
        vo.setPinned(relation.getPinned());
        vo.setCreatedAt(relation.getCreatedAt());
        vo.setUpdatedAt(relation.getUpdatedAt());
        return vo;
    }
}
