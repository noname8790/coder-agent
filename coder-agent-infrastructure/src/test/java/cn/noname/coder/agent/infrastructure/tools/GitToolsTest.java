package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class GitToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldRunGitReadAndWriteToolsGivenLocalRepository() throws Exception {
        // Given a local git repository with uncommitted changes.
        assumeGitAvailable();
        initRepository(tempDir);
        Files.writeString(tempDir.resolve(".gitignore"), ".coder/\nlogs/\n", StandardCharsets.UTF_8);
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("repo", tempDir);

        // When querying status and diff, then structured summaries are returned.
        ToolResult status = new GitStatusTool().execute("run_1", workspace, "{}");
        ToolResult diff = new GitDiffTool().execute("run_1", workspace, "{}");
        assertEquals(CallStatus.SUCCESS, status.status());
        assertTrue(status.summary().contains(".gitignore"));
        assertEquals(CallStatus.SUCCESS, diff.status());
        assertTrue(diff.summary().contains(".gitignore"));
        assertFalse(diff.changedFiles().isEmpty());

        // When staging and committing, then local commit succeeds.
        ToolResult add = new GitAddTool().execute("run_1", workspace, "{\"path\":\".gitignore\"}");
        ToolResult commit = new GitCommitTool().execute("run_1", workspace, "{\"message\":\"test: update readme\"}");
        assertEquals(CallStatus.SUCCESS, add.status());
        assertEquals(CallStatus.SUCCESS, commit.status());
        assertTrue(commit.fullOutput().contains("test: update readme") || commit.fullOutput().contains("1 file changed"));

        // When generating PR draft, then the run artifact is created.
        ToolResult pr = new GeneratePrDraftTool().execute("run_1", workspace,
                "{\"title\":\"Test PR\",\"summary\":\"Verify Git tools\"}");
        assertEquals(CallStatus.SUCCESS, pr.status());
        Path prFile = tempDir.resolve(".coder").resolve("runs").resolve("run_1").resolve("pull-request.md");
        assertTrue(Files.exists(prFile));
        assertTrue(Files.readString(prFile, StandardCharsets.UTF_8).contains("Test PR"));
    }

    @Test
    void shouldReturnStructuredFailureGivenNonGitRepository() throws Exception {
        // Given a non-git workspace.
        assumeGitAvailable();
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("repo", tempDir);

        // When calling git_status, then it fails without hanging.
        ToolResult status = new GitStatusTool().execute("run_1", workspace, "{}");
        assertEquals(CallStatus.FAILED, status.status());
        assertNotNull(status.errorMessage());
    }

    @Test
    void shouldRejectUnsafeGitAddPath() {
        // Given any workspace.
        WorkspaceDescriptor workspace = new WorkspaceDescriptor("repo", tempDir);

        // When git_add targets a parent path, then execution is rejected.
        ToolResult result = new GitAddTool().execute("run_1", workspace, "{\"path\":\"../outside.txt\"}");
        assertEquals(CallStatus.REJECTED, result.status());
        assertEquals("INVALID_ARGUMENT", result.errorMessage());
    }

    private void initRepository(Path root) throws Exception {
        run(root, "init");
        run(root, "config", "user.email", "coder-agent@example.local");
        run(root, "config", "user.name", "Coder Agent Test");
        Files.writeString(root.resolve(".gitignore"), ".coder/\n", StandardCharsets.UTF_8);
        run(root, "add", ".gitignore");
        run(root, "commit", "-m", "chore: init");
    }

    private void assumeGitAvailable() {
        try {
            GitToolSupport.git(new WorkspaceDescriptor("repo", tempDir), Duration.ofSeconds(5), "--version");
        } catch (Exception e) {
            Assumptions.abort("git is not available: " + e.getMessage());
        }
    }

    private void run(Path root, String... args) throws Exception {
        GitToolSupport.GitCommandResult result = GitToolSupport.git(new WorkspaceDescriptor("repo", root),
                Duration.ofSeconds(120), args);
        assertEquals(CallStatus.SUCCESS, result.status(), result.output());
    }
}
