package cn.noname.coder.agent.domain.model.adapter.port;

import cn.noname.coder.agent.domain.model.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.model.model.valobj.ModelResponse;

/**
 * 模型网关端口，后续可增加 DashScope 专用实现。
 */
public interface IModelGateway {

    ModelResponse call(ModelRequest request);
}
