package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.RunArtifact;
import cn.noname.coder.agent.domain.agent.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.agent.model.valobj.TestCommandReport;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.ArtifactType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件工件端口实现，固定写入 workspaceRoot/.coder/runs/{runId}/。
 */
@Component
@Slf4j
public class ArtifactFilePort implements IArtifactPort {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    @Override
    @SneakyThrows
    public List<RunArtifact> initializeRun(WorkspaceDescriptor workspace, AgentRun run) {
        Path dir = runDir(workspace, run.getRunId());
        Files.createDirectories(dir.resolve("context-snapshot"));
        Files.createDirectories(dir.resolve("tool-output"));

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("runId", run.getRunId());
        meta.put("workspaceKey", run.getWorkspaceKey());
        meta.put("task", run.getTask());
        meta.put("model", run.getModel());
        meta.put("createdAt", run.getCreatedAt());
        Path metaFile = dir.resolve("run-meta.json");
        writeJson(metaFile, meta);
        Path traceFile = dir.resolve("trace.jsonl");
        if (!Files.exists(traceFile)) {
            Files.createFile(traceFile);
        }
        log.info("已初始化运行工件 runId={} dir={}", run.getRunId(), dir);
        return List.of(
                artifact(run.getRunId(), ArtifactType.RUN_META, workspace, metaFile),
                artifact(run.getRunId(), ArtifactType.TRACE, workspace, traceFile)
        );
    }

    @Override
    @SneakyThrows
    public RunArtifact appendTrace(WorkspaceDescriptor workspace, String runId, Map<String, Object> event) {
        Path file = runDir(workspace, runId).resolve("trace.jsonl");
        Files.createDirectories(file.getParent());
        Files.writeString(file, objectMapper.writeValueAsString(event) + System.lineSeparator(), StandardCharsets.UTF_8,
                Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        return artifact(runId, ArtifactType.TRACE, workspace, file);
    }

    @Override
    @SneakyThrows
    public RunArtifact writeContextSnapshot(WorkspaceDescriptor workspace, String runId, int callNo, Map<String, Object> snapshot) {
        Path file = runDir(workspace, runId).resolve("context-snapshot").resolve("model-call-" + callNo + ".json");
        writeJson(file, snapshot);
        return artifact(runId, ArtifactType.CONTEXT_SNAPSHOT, workspace, file);
    }

    @Override
    @SneakyThrows
    public RunArtifact writeToolOutput(WorkspaceDescriptor workspace, String runId, int callNo, String output) {
        Path file = runDir(workspace, runId).resolve("tool-output").resolve("tool-call-" + callNo + ".txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, output == null ? "" : output, StandardCharsets.UTF_8);
        return artifact(runId, ArtifactType.TOOL_OUTPUT, workspace, file);
    }

    @Override
    @SneakyThrows
    public RunArtifact writeFinalResult(WorkspaceDescriptor workspace, AgentRun run, Map<String, Object> result) {
        Path file = runDir(workspace, run.getRunId()).resolve("final-result.json");
        writeJson(file, result);
        log.info("已写入最终结果工件 runId={} status={} path={}", run.getRunId(), run.getStatus(), file);
        return artifact(run.getRunId(), ArtifactType.FINAL_RESULT, workspace, file);
    }

    @Override
    @SneakyThrows
    public List<RunArtifact> writeReviewArtifacts(WorkspaceDescriptor workspace,
                                                  AgentRun run,
                                                  List<ChangedFile> changedFiles,
                                                  List<TestCommandReport> testReports) {
        if ((changedFiles == null || changedFiles.isEmpty()) && (testReports == null || testReports.isEmpty())) {
            return List.of();
        }
        log.info("开始写入审查工件 runId={} changedFileCount={} testReportCount={}",
                run.getRunId(),
                changedFiles == null ? 0 : changedFiles.size(),
                testReports == null ? 0 : testReports.size());
        List<RunArtifact> artifacts = new ArrayList<>();
        if (changedFiles != null && !changedFiles.isEmpty()) {
            Path diff = runDir(workspace, run.getRunId()).resolve("patch.diff");
            Files.createDirectories(diff.getParent());
            Files.writeString(diff, buildDiff(changedFiles), StandardCharsets.UTF_8);
            artifacts.add(artifact(run.getRunId(), ArtifactType.PATCH_DIFF, workspace, diff));

            Path changed = runDir(workspace, run.getRunId()).resolve("changed-files.json");
            writeJson(changed, changedFiles.stream().map(this::changedFileJson).toList());
            artifacts.add(artifact(run.getRunId(), ArtifactType.CHANGED_FILES, workspace, changed));
        }
        if (testReports != null && !testReports.isEmpty()) {
            Path report = runDir(workspace, run.getRunId()).resolve("test-report.json");
            writeJson(report, testReports);
            artifacts.add(artifact(run.getRunId(), ArtifactType.TEST_REPORT, workspace, report));
        }
        Path review = runDir(workspace, run.getRunId()).resolve("review-summary.md");
        Files.createDirectories(review.getParent());
        Files.writeString(review, buildReviewSummary(run, changedFiles, testReports), StandardCharsets.UTF_8);
        artifacts.add(artifact(run.getRunId(), ArtifactType.REVIEW_SUMMARY, workspace, review));
        log.info("已写入审查工件 runId={} artifactCount={}", run.getRunId(), artifacts.size());
        return artifacts;
    }

    @Override
    @SneakyThrows
    public List<Map<String, Object>> readTrace(WorkspaceDescriptor workspace, String runId) {
        Path file = runDir(workspace, runId).resolve("trace.jsonl");
        if (!Files.exists(file)) {
            return List.of();
        }
        List<Map<String, Object>> events = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                events.add(objectMapper.readValue(line, MAP_TYPE));
            }
        }
        return events;
    }

    private Path runDir(WorkspaceDescriptor workspace, String runId) {
        return workspace.rootPath().resolve(".coder").resolve("runs").resolve(runId).normalize();
    }

    private Map<String, Object> changedFileJson(ChangedFile changedFile) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("relativePath", changedFile.relativePath());
        item.put("changeType", changedFile.changeType());
        item.put("beforeHash", changedFile.beforeHash());
        item.put("afterHash", changedFile.afterHash());
        item.put("toolCallNo", changedFile.toolCallNo());
        return item;
    }

    private String buildDiff(List<ChangedFile> changedFiles) {
        StringBuilder diff = new StringBuilder();
        for (ChangedFile file : changedFiles) {
            diff.append("diff --git a/").append(file.relativePath()).append(" b/").append(file.relativePath()).append(System.lineSeparator());
            if ("ADD".equals(file.changeType())) {
                diff.append("new file mode 100644").append(System.lineSeparator());
            }
            diff.append("--- a/").append(file.relativePath()).append(System.lineSeparator());
            diff.append("+++ b/").append(file.relativePath()).append(System.lineSeparator());
            diff.append("@@").append(System.lineSeparator());
            appendPrefixed(diff, "-", file.beforeContent());
            appendPrefixed(diff, "+", file.afterContent());
        }
        return diff.toString();
    }

    private void appendPrefixed(StringBuilder diff, String prefix, String content) {
        if (content == null || content.isEmpty()) {
            return;
        }
        content.lines().forEach(line -> diff.append(prefix).append(line).append(System.lineSeparator()));
    }

    private String buildReviewSummary(AgentRun run, List<ChangedFile> changedFiles, List<TestCommandReport> testReports) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Agent Run Review Summary").append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("- runId: ").append(run.getRunId()).append(System.lineSeparator());
        markdown.append("- task: ").append(run.getTask()).append(System.lineSeparator());
        markdown.append("- mode: ").append(run.getMode() == null ? "READ_ONLY" : run.getMode().name()).append(System.lineSeparator());
        markdown.append("- model: ").append(run.getModel()).append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("## Changed Files").append(System.lineSeparator());
        if (changedFiles == null || changedFiles.isEmpty()) {
            markdown.append("- None").append(System.lineSeparator());
        } else {
            changedFiles.forEach(file -> markdown.append("- ").append(file.changeType()).append(" ").append(file.relativePath()).append(System.lineSeparator()));
        }
        markdown.append(System.lineSeparator()).append("## Test Results").append(System.lineSeparator());
        if (testReports == null || testReports.isEmpty()) {
            markdown.append("- NOT_RUN").append(System.lineSeparator());
        } else {
            testReports.forEach(report -> markdown.append("- ").append(report.status()).append(" `").append(report.command()).append("` exitCode=").append(report.exitCode()).append(System.lineSeparator()));
        }
        markdown.append(System.lineSeparator()).append("## Review Focus").append(System.lineSeparator());
        markdown.append("- 人工确认 diff 是否符合任务意图").append(System.lineSeparator());
        markdown.append("- 人工确认测试失败时是否需要继续修复").append(System.lineSeparator());
        markdown.append("- 第二版不会自动 commit、push 或创建 PR").append(System.lineSeparator());
        return markdown.toString();
    }

    @SneakyThrows
    private void writeJson(Path file, Object value) {
        Files.createDirectories(file.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), value);
    }

    @SneakyThrows
    private RunArtifact artifact(String runId, ArtifactType type, WorkspaceDescriptor workspace, Path file) {
        return RunArtifact.builder()
                .runId(runId)
                .artifactType(type)
                .relativePath(workspace.rootPath().relativize(file).toString().replace('\\', '/'))
                .fileSize(Files.exists(file) ? Files.size(file) : 0L)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
