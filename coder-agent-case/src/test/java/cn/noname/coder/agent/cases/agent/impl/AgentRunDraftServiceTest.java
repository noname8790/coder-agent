package cn.noname.coder.agent.cases.agent.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentRunDraftServiceTest {

    @Test
    void shouldAccumulateVisibleDraftAndClearGivenRunCompleted() {
        // Given 运行中模型分段输出可见正文
        AgentRunDraftService service = new AgentRunDraftService();

        // When 追加两段可见 delta
        service.appendVisibleDelta("run_1", "我来");
        service.appendVisibleDelta("run_1", "检查文件");

        // Then 草稿可被恢复，供刷新/切换会话后继续显示
        assertEquals("我来检查文件", service.content("run_1"));

        // When 运行结束清理草稿
        service.clear("run_1");

        // Then 运行中缓存不再保留
        assertEquals("", service.content("run_1"));
    }

    @Test
    void shouldStripThinkBlockGivenModelStreamsReasoningText() {
        AgentRunDraftService service = new AgentRunDraftService();

        service.appendVisibleDelta("run_1", "<think>hidden reasoning</think>");
        service.appendVisibleDelta("run_1", "visible answer");
        service.appendVisibleDelta("run_1", "</think> final answer");

        assertEquals("visible answer final answer", service.content("run_1"));
    }
}
