package cn.noname.coder.agent.api.dto;

/**
 * 运行工件索引 DTO。
 */
public record RunArtifactDTO(String artifactType, String relativePath, Long fileSize) {
}
