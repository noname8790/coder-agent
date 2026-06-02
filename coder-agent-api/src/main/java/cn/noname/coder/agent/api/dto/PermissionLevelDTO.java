package cn.noname.coder.agent.api.dto;

import java.util.List;

/**
 * 权限等级说明响应。
 */
public record PermissionLevelDTO(
        String code,
        String displayName,
        String description,
        List<String> allowedFeatures,
        List<String> forbiddenFeatures,
        String riskNotice
) {
}
