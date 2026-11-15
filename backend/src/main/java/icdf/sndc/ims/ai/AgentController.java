package icdf.sndc.ims.ai;

import icdf.sndc.ims.ai.AgentDtos.AgentRequest;
import icdf.sndc.ims.ai.AgentDtos.AgentResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
public class AgentController {

    private final AgentService agent;

    public AgentController(AgentService agent) {
        this.agent = agent;
    }

    /** 自然語言資產查詢；修改／刪除只會回傳待確認動作，不會直接執行。 */
    @PostMapping("/asset-query")
    public AgentResponse query(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AgentRequest request) {
        return agent.handle(userId(jwt), request);
    }

    @PostMapping("/actions/{id}/confirm")
    public AgentResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return agent.confirm(userId(jwt), id);
    }

    @DeleteMapping("/actions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        agent.cancel(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
