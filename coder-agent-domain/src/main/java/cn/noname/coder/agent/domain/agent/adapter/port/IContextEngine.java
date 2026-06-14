package cn.noname.coder.agent.domain.agent.adapter.port;

import cn.noname.coder.agent.domain.agent.model.valobj.ContextAssemblyResult;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextBudget;

import java.util.List;

public interface IContextEngine {

    ContextAssemblyResult assemble(List<ContextCandidate> candidates, ContextBudget budget);
}
