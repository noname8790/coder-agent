package cn.noname.coder.agent.domain.model.adapter.port;

import cn.noname.coder.agent.domain.model.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.model.model.valobj.ModelStreamEvent;

import java.util.function.Consumer;

public interface IStreamingModelGateway {

    void stream(ModelRequest request, Consumer<ModelStreamEvent> eventConsumer);
}
