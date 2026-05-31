package cn.noname.coder.agent.infrastructure.dao;

import cn.noname.coder.agent.infrastructure.dao.po.ModelCallPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IModelCallDao extends BaseMapper<ModelCallPO> {
}
