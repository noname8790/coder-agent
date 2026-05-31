package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.RunArtifact;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.ArtifactType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
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
        return artifact(run.getRunId(), ArtifactType.FINAL_RESULT, workspace, file);
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
