package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.api.dto.PermissionLevelDTO;
import cn.noname.coder.agent.cases.agent.IQueryPermissionLevelCase;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 权限等级说明用例。
 */
@Service
public class QueryPermissionLevelCaseImpl implements IQueryPermissionLevelCase {

    @Override
    public List<PermissionLevelDTO> list() {
        return Arrays.stream(AgentPermissionLevel.values())
                .filter(level -> level != AgentPermissionLevel.L4_DANGEROUS_LOCAL)
                .map(level -> new PermissionLevelDTO(
                        level.name(),
                        level.displayName(),
                        level.description(),
                        level.allowedFeatures(),
                        level.forbiddenFeatures(),
                        level.riskNotice()))
                .toList();
    }
}
