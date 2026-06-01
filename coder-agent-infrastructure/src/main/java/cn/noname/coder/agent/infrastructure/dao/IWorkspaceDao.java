package cn.noname.coder.agent.infrastructure.dao;

import cn.noname.coder.agent.infrastructure.dao.po.WorkspacePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IWorkspaceDao extends BaseMapper<WorkspacePO> {
}
