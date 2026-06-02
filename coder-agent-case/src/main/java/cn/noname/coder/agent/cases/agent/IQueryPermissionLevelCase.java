package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.PermissionLevelDTO;

import java.util.List;

/**
 * 查询权限等级说明。
 */
public interface IQueryPermissionLevelCase {

    List<PermissionLevelDTO> list();
}
