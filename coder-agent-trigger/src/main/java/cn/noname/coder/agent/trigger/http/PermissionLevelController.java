package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.PermissionLevelDTO;
import cn.noname.coder.agent.cases.agent.IQueryPermissionLevelCase;
import cn.noname.coder.agent.types.common.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限等级说明 API。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/permission-levels")
public class PermissionLevelController {

    private final IQueryPermissionLevelCase queryPermissionLevelCase;

    @GetMapping
    public Response<List<PermissionLevelDTO>> list() {
        return Response.ok(queryPermissionLevelCase.list());
    }
}
