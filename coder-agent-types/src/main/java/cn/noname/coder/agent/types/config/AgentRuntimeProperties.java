package cn.noname.coder.agent.types.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 运行配置。数据源、模型 URL、API Key、workspace 路径允许留空，部署后再补。
 */
@Data
@ConfigurationProperties(prefix = "coder-agent")
public class AgentRuntimeProperties {

    private Model model = new Model();
    private Budget budget = new Budget();
    private Map<String, String> workspaces = new LinkedHashMap<>();
    private WorkspaceDefaults workspaceDefaults = new WorkspaceDefaults();
    private Tools tools = new Tools();

    @Data
    public static class Model {
        private String defaultModelKey = "";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private String endpointType = "responses";
        private double temperature = 0.2;
        private int timeoutSeconds = 60;
        private Map<String, Backend> models = new LinkedHashMap<>();
    }

    @Data
    public static class Backend {
        private String provider = "openai-compatible";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private String endpointType = "";
        private Double temperature;
        private Integer timeoutSeconds;
    }

    @Data
    public static class Budget {
        private int maxSteps = 25;
        private int maxModelCalls = 25;
        private int maxToolCalls = 50;
        private int timeoutSeconds = 300;
        private int maxConcurrentRuns = 2;
    }

    @Data
    public static class WorkspaceDefaults {
        private List<String> capabilities = new ArrayList<>(List.of("READ_REPOSITORY", "GIT_READ", "RUN_TEST"));
    }

    @Data
    public static class Tools {
        private int maxListEntries = 200;
        private int maxReadBytes = 65536;
        private int maxSearchResults = 100;
        private int maxToolInlineChars = 4000;
        private int shellTimeoutSeconds = 120;
        private List<String> allowedCommandPrefixes = new ArrayList<>(List.of(
                "git status",
                "git diff",
                "git log",
                "mvn test",
                "mvn -q test",
                "mvn clean test",
                "mvn package",
                "mvn clean package",
                "mvn -pl",
                "java -version"
        ));
        private List<String> dangerousTokens = new ArrayList<>(List.of(
                "&&", "||", "|", ">", "<", ";", "`", "$(", " rm ", " del ", " rmdir ",
                "Remove-Item", "Move-Item", "git reset", "git push", "git commit"
        ));
    }
}
