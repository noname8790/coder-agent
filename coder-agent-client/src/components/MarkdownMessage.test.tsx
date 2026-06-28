import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { MarkdownMessage } from "./MarkdownMessage";

describe("MarkdownMessage", () => {
  it("renders gfm markdown without executing raw html", () => {
    const html = renderToStaticMarkup(
      <MarkdownMessage
        content={[
          "| 项目 | 状态 |",
          "| --- | --- |",
          "| API | ✅ |",
          "",
          "- [x] 已完成",
          "",
          "```java",
          "class Demo {}",
          "```",
          "",
          "<script>alert(1)</script>"
        ].join("\n")}
      />
    );

    expect(html).toContain("<table>");
    expect(html).toContain("type=\"checkbox\"");
    expect(html).toContain("class=\"language-java\"");
    expect(html).not.toContain("<script>");
    expect(html).toContain("&lt;script&gt;alert(1)&lt;/script&gt;");
  });
});
