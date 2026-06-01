package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.types.exception.AppException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/**
 * 文本文件判断和摘要工具。
 */
final class TextFileSupport {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".java", ".xml", ".yml", ".yaml", ".md", ".txt", ".json", ".properties", ".sql", ".gitignore",
            ".gradle", ".pom", ".kt", ".js", ".ts", ".css", ".html", ".vue", ".py"
    );

    private TextFileSupport() {
    }

    static void assertTextFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        boolean text = TEXT_EXTENSIONS.stream().anyMatch(name::endsWith) || !name.contains(".");
        if (!text) {
            throw new AppException("NON_TEXT_FILE", "拒绝编辑非文本文件：" + file.getFileName());
        }
    }

    static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new AppException("FILE_READ_FAILED", "读取文件失败：" + e.getMessage());
        }
    }
}
