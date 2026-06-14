package cn.noname.coder.agent.api.dto;

import java.util.List;

public record StartEvalRunRequestDTO(String name, List<String> modelKeys) {
}
