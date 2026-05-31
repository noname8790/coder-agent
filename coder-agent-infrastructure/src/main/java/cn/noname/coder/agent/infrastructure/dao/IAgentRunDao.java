package cn.noname.coder.agent.infrastructure.dao;

import cn.noname.coder.agent.infrastructure.dao.po.AgentRunPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * agent_run DAO。
 */
@Mapper
public interface IAgentRunDao extends BaseMapper<AgentRunPO> {
}
