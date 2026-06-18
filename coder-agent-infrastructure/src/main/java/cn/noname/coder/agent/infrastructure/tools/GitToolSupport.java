package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.CallStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class GitToolSupport {

    private GitToolSupport() {
    }

    static GitCommandResult git(WorkspaceDescriptor workspace, Duration timeout, String... args) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command(args));
        builder.directory(workspace.rootPath().toFile());
        builder.redirectErrorStream(true);
        long start = System.currentTimeMillis();
        Process process = builder.start();
        boolean finished = process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new GitCommandResult(CallStatus.FAILED, 124, "Git 命令超时", "", System.currentTimeMillis() - start);
        }
        String output = readProcess(process);
        int exitCode = process.exitValue();
        return new GitCommandResult(exitCode == 0 ? CallStatus.SUCCESS : CallStatus.FAILED,
                exitCode,
                exitCode == 0 ? "" : "GIT_EXIT_" + exitCode,
                output,
                System.currentTimeMillis() - start);
    }

    static List<ChangedFile> changedFiles(WorkspaceDescriptor workspace) {
        try {
            GitCommandResult result = git(workspace, Duration.ofSeconds(20), "diff", "--numstat", "HEAD");
            if (result.status() != CallStatus.SUCCESS || result.output().isBlank()) {
                return List.of();
            }
            List<ChangedFile> files = new ArrayList<>();
            for (String line : result.output().lines().toList()) {
                String[] parts = line.split("\\t");
                if (parts.length < 3) {
                    continue;
                }
                files.add(new ChangedFile(parts[2], "MODIFY", null, null, null, "", ""));
            }
            return files;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    static Path runDir(WorkspaceDescriptor workspace, String runId) {
        return workspace.rootPath().resolve(".coder").resolve("runs").resolve(runId).normalize();
    }

    private static List<String> command(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        return command;
    }

    private static String readProcess(Process process) throws Exception {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }
        return output.toString();
    }

    record GitCommandResult(CallStatus status, int exitCode, String errorMessage, String output, long latencyMs) {
    }
}
