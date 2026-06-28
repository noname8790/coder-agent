package cn.noname.coder.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureBoundaryTest {

    @Test
    void shouldKeepAiFrameworkTypesOutOfDomainAndCaseLayers() throws Exception {
        // Given domain/case 是 Harness 自定义核心边界
        List<Path> roots = List.of(
                Path.of("coder-agent-domain", "src", "main", "java"),
                Path.of("coder-agent-case", "src", "main", "java"));
        List<String> forbiddenImports = List.of(
                "org.springframework.ai",
                "com.alibaba.cloud.ai",
                "io.agentscope",
                "dev.langchain4j",
                "com.embabel");

        // When 扫描源代码 import
        List<String> violations = roots.stream()
                .filter(Files::exists)
                .flatMap(root -> {
                    try {
                        return Files.walk(root);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> {
                    try {
                        String source = Files.readString(path);
                        return forbiddenImports.stream()
                                .filter(source::contains)
                                .map(importName -> path + " -> " + importName);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();

        // Then 外部 AI 框架只能在 infrastructure adapter 局部适配
        assertTrue(violations.isEmpty(), "domain/case 不应直接引用外部 AI 框架类型：" + violations);
    }
}
