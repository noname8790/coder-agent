package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.ModelProviderListResponseDTO;
import cn.noname.coder.agent.api.dto.ModelProviderRequestDTO;
import cn.noname.coder.agent.api.dto.ModelProviderResponseDTO;
import cn.noname.coder.agent.cases.model.IModelProviderCase;
import cn.noname.coder.agent.types.common.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/model-providers")
public class ModelProviderController {

    private final IModelProviderCase modelProviderCase;

    @GetMapping
    public Response<ModelProviderListResponseDTO> list(@RequestParam(value = "enabledOnly", defaultValue = "false") boolean enabledOnly) {
        return Response.ok(modelProviderCase.list(enabledOnly));
    }

    @GetMapping("/{modelKey}")
    public Response<ModelProviderResponseDTO> query(@PathVariable("modelKey") String modelKey) {
        return Response.ok(modelProviderCase.query(modelKey));
    }

    @PostMapping
    public Response<ModelProviderResponseDTO> create(@RequestBody ModelProviderRequestDTO request) {
        return Response.ok(modelProviderCase.create(request));
    }

    @PutMapping("/{modelKey}")
    public Response<ModelProviderResponseDTO> update(@PathVariable("modelKey") String modelKey,
                                                     @RequestBody ModelProviderRequestDTO request) {
        return Response.ok(modelProviderCase.update(modelKey, request));
    }

    @PostMapping("/{modelKey}/enable")
    public Response<ModelProviderResponseDTO> enable(@PathVariable("modelKey") String modelKey) {
        return Response.ok(modelProviderCase.enable(modelKey));
    }

    @PostMapping("/{modelKey}/disable")
    public Response<ModelProviderResponseDTO> disable(@PathVariable("modelKey") String modelKey) {
        return Response.ok(modelProviderCase.disable(modelKey));
    }

    @PostMapping("/{modelKey}/default")
    public Response<ModelProviderResponseDTO> setDefault(@PathVariable("modelKey") String modelKey) {
        return Response.ok(modelProviderCase.setDefault(modelKey));
    }

    @DeleteMapping("/{modelKey}")
    public Response<ModelProviderResponseDTO> delete(@PathVariable("modelKey") String modelKey) {
        return Response.ok(modelProviderCase.delete(modelKey));
    }
}
