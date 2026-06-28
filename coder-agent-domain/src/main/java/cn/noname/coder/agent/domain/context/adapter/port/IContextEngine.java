package cn.noname.coder.agent.domain.context.adapter.port;

import cn.noname.coder.agent.domain.context.model.valobj.ContextAssemblyResult;
import cn.noname.coder.agent.domain.context.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.context.model.valobj.ContextBudget;

import java.util.List;

public interface IContextEngine {

    ContextAssemblyResult assemble(List<ContextCandidate> candidates, ContextBudget budget);
}
