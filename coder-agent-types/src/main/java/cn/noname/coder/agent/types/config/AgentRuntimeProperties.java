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
    private Tools tools = new Tools();
    private Pgvector pgvector = new Pgvector();
    private Embedding embedding = new Embedding();
    private Memory memory = new Memory();
    private Context context = new Context();
    private ToolApproval toolApproval = new ToolApproval();
    private Eval eval = new Eval();

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
        private Boolean streamingEnabled;
        private Boolean toolCallingEnabled;
        private ContextBudget budget;
    }

    @Data
    public static class ContextBudget {
        private Integer maxContextTokens;
        private Integer maxOutputTokens;
        private Integer inputBudgetTokens;
        private Integer memoryBudgetTokens;
        private Integer fileSummaryBudgetTokens;
        private Integer rawFileBudgetTokens;
        private Integer toolResultBudgetTokens;
        private Integer recentMessageBudgetTokens;
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
    public static class Tools {
        private int maxListEntries = 200;
        private int maxReadBytes = 65536;
        private int maxSearchResults = 100;
        private int maxToolInlineChars = 4000;
        private int shellTimeoutSeconds = 120;
        private List<String> allowedCommandPrefixes = new ArrayList<>(List.of(
                "cd",
                "git status",
                "git diff",
                "git log",
                "git init",
                "git reset",
                "git rm",
                "git clean",
                "git restore",
                "git checkout -b",
                "git add",
                "git commit",
                "mvn test",
                "mvn -q test",
                "mvn test -Dtest",
                "mvn -Dtest",
                "mvn -q -Dtest",
                "mvn clean test",
                "mvn compile",
                "mvn -q compile",
                "mvn test-compile",
                "mvn -q test-compile",
                "mvn package",
                "mvn clean package",
                "mvn -pl",
                "java -version"
        ));
        private List<String> dangerousTokens = new ArrayList<>(List.of(
                "&&", "||", "|", ">", "<", ";", "`", "$(", " rm ", " del ", " rmdir ",
                "Remove-Item", "Move-Item", "git push"
        ));
    }

    @Data
    public static class Pgvector {
        private boolean enabled = false;
        private String url = "";
        private String username = "";
        private String password = "";
        private String schema = "public";
        private String tablePrefix = "coder_agent";
        private int vectorDimensions = 1024;
        private String indexType = "hnsw";
        private String similarity = "cosine";
    }

    @Data
    public static class Embedding {
        private String provider = "openai-compatible";
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private String endpointType = "embeddings";
        private int timeoutSeconds = 120;
    }

    @Data
    public static class Memory {
        private boolean enabled = false;
        private int topK = 8;
        private double minScore = 0.35;
        private int maxChunksPerRun = 20;
        private int maxEmbeddingCallsPerRun = 8;
        private int maxAutoSummaryFilesPerRun = 6;
        private int maxFileBytesForSummary = 65536;
    }

    @Data
    public static class Context {
        private int maxInputTokens = 32000;
        private int systemReserveTokens = 2000;
        private int memoryBudgetTokens = 6000;
        private int fileSummaryBudgetTokens = 6000;
        private int rawSnippetBudgetTokens = 8000;
        private int toolResultBudgetTokens = 4000;
        private int recentMessageBudgetTokens = 4000;
        private int outputReserveTokens = 4000;
    }

    @Data
    public static class ToolApproval {
        private boolean enabled = true;
        private int timeoutSeconds = 600;
    }

    @Data
    public static class Eval {
        private boolean enabled = true;
        private int caseTimeoutSeconds = 600;
        private int maxConcurrentCases = 1;
    }
}
