package cn.noname.coder.agent.domain.agent.adapter.port;

import cn.noname.coder.agent.domain.agent.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.agent.model.valobj.ModelStreamEvent;

import java.util.function.Consumer;

public interface IStreamingModelGateway {

    void stream(ModelRequest request, Consumer<ModelStreamEvent> eventConsumer);
}
