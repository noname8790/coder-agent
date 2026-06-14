package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.api.dto.AgentRunDraftResponseDTO;
import cn.noname.coder.agent.cases.agent.IQueryAgentRunDraftCase;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentRunDraftService implements IQueryAgentRunDraftCase {

    private final Map<String, Draft> drafts = new ConcurrentHashMap<>();
    private final IAgentRunRepository runRepository;

    public AgentRunDraftService() {
        this.runRepository = null;
    }

    @Autowired
    public AgentRunDraftService(IAgentRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    public void appendVisibleDelta(String runId, String delta) {
        if (!StringUtils.hasText(runId) || !StringUtils.hasText(delta)) {
            return;
        }
        drafts.compute(runId, (ignored, draft) -> {
            Draft current = draft == null ? new Draft() : draft;
            current.content.append(current.filterVisible(delta));
            current.updatedAt = LocalDateTime.now();
            return current;
        });
    }

    public String content(String runId) {
        Draft draft = drafts.get(runId);
        return draft == null ? "" : draft.content.toString();
    }

    static String sanitizeCompletedText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        int lastClose = text.toLowerCase(java.util.Locale.ROOT).lastIndexOf("</think>");
        if (lastClose >= 0) {
            text = text.substring(lastClose + "</think>".length());
        }
        return text.replaceAll("(?is)<think[^>]*>.*?</think>", "")
                .replaceAll("(?is)<think[^>]*>.*$", "")
                .replace("</think>", "")
                .trim();
    }

    public void clear(String runId) {
        if (StringUtils.hasText(runId)) {
            drafts.remove(runId);
        }
    }

    @Override
    public AgentRunDraftResponseDTO queryDraft(String runId) {
        Draft draft = drafts.get(runId);
        AgentRun run = runRepository == null ? null : runRepository.findByRunId(runId).orElse(null);
        return new AgentRunDraftResponseDTO(
                runId,
                draft == null ? "" : draft.content.toString(),
                run == null || run.getStatus() == null ? null : run.getStatus().name(),
                run == null ? null : run.getFailureReason(),
                draft == null ? null : draft.updatedAt
        );
    }

    private static class Draft {
        private final StringBuilder content = new StringBuilder();
        private LocalDateTime updatedAt = LocalDateTime.now();
        private boolean insideThink;

        private String filterVisible(String delta) {
            String remaining = delta == null ? "" : delta;
            StringBuilder visible = new StringBuilder();
            while (!remaining.isEmpty()) {
                String lower = remaining.toLowerCase(java.util.Locale.ROOT);
                if (insideThink) {
                    int end = lower.indexOf("</think>");
                    if (end < 0) {
                        return visible.toString();
                    }
                    remaining = remaining.substring(end + "</think>".length());
                    insideThink = false;
                    continue;
                }
                int danglingClose = lower.indexOf("</think>");
                if (danglingClose >= 0) {
                    remaining = remaining.substring(danglingClose + "</think>".length());
                    continue;
                }
                int start = lower.indexOf("<think");
                if (start < 0) {
                    visible.append(remaining);
                    break;
                }
                visible.append(remaining, 0, start);
                int startTagEnd = remaining.indexOf('>', start);
                if (startTagEnd < 0) {
                    insideThink = true;
                    break;
                }
                remaining = remaining.substring(startTagEnd + 1);
                insideThink = true;
            }
            return visible.toString();
        }
    }
}
