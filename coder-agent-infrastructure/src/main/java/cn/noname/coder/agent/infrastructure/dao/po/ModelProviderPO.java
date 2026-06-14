package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_model_provider")
public class ModelProviderPO {
    @TableId(type = IdType.AUTO)
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
    private Integer maxContextTokens;
    private Integer maxOutputTokens;
    private Integer inputBudgetTokens;
    private Integer memoryBudgetTokens;
    private Integer fileSummaryBudgetTokens;
    private Integer rawFileBudgetTokens;
    private Integer toolResultBudgetTokens;
    private Integer recentMessageBudgetTokens;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
