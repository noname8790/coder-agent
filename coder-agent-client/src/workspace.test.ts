import { describe, expect, it } from "vitest";
import { workspaceKeyFromPath } from "./workspace";

describe("workspaceKeyFromPath", () => {
  it("uses the selected Windows directory name", () => {
    expect(workspaceKeyFromPath("E:\\IdeaProjects\\coder-agent")).toBe("coder-agent");
  });

  it("uses the selected directory name when the path has a trailing separator", () => {
    expect(workspaceKeyFromPath("E:/IdeaProjects/agent-test-demo/")).toBe("agent-test-demo");
  });
});
