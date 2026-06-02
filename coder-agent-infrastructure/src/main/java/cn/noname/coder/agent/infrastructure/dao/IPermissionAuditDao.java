package cn.noname.coder.agent.infrastructure.dao;

import cn.noname.coder.agent.infrastructure.dao.po.PermissionAuditPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * agent_permission_audit DAO。
 */
@Mapper
public interface IPermissionAuditDao extends BaseMapper<PermissionAuditPO> {
}
