package cn.noname.coder.agent.infrastructure.dao;

import cn.noname.coder.agent.infrastructure.dao.po.AgentMessagePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * agent_message DAO。
 */
@Mapper
public interface IAgentMessageDao extends BaseMapper<AgentMessagePO> {
}
