package cn.noname.coder.agent.domain.agent.model.entity;

import cn.noname.coder.agent.domain.agent.model.valobj.ContextBudget;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelProvider {

    private Long id;
    private String modelKey;
    private String displayName;
    private String provider;
    private String baseUrl;
    private String apiKeyCipher;
    private String modelName;
    private String endpointType;
    private Double temperature;
    private Integer timeoutSeconds;
    private Boolean streamingEnabled;
    private Boolean toolCallingEnabled;
    private Boolean defaultModel;
    private String status;
    private ContextBudget budget;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
