package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.types.exception.AppException;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 编辑工具的受保护路径策略。
 */
@Component
public class ProtectedPathPolicy {

    private static final Set<String> SECRET_NAMES = Set.of(
            "id_rsa", "id_dsa", "id_ecdsa", "id_ed25519", "private.key", "secret.key"
    );

    public void assertEditable(Path workspaceRoot, Path file) {
        Path relative = workspaceRoot.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize());
        String normalized = relative.toString().replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        String fileName = file.getFileName() == null ? "" : file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lower.equals(".env") || lower.startsWith(".env.")
                || lower.startsWith(".git/")
                || lower.startsWith(".coder/")
                || lower.startsWith("target/")
                || lower.contains("/target/")
                || fileName.contains("secret")
                || fileName.contains("token")
                || fileName.contains("password")
                || fileName.contains("credential")
                || SECRET_NAMES.contains(fileName)) {
            throw new AppException("PROTECTED_PATH", "受保护路径禁止编辑：" + normalized);
        }
    }
}
