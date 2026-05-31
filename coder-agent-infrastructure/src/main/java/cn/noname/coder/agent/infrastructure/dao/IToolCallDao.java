package cn.noname.coder.agent.infrastructure.dao;

import cn.noname.coder.agent.infrastructure.dao.po.ToolCallPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IToolCallDao extends BaseMapper<ToolCallPO> {
}
