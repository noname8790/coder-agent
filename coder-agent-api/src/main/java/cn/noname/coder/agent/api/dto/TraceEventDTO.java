package cn.noname.coder.agent.api.dto;

import java.util.Map;

/**
 * trace.jsonl 中的一行事件。
 */
public record TraceEventDTO(Map<String, Object> event) {
}
