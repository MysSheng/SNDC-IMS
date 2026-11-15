package icdf.sndc.ims.manager;

import icdf.sndc.ims.manager.ManagerDtos.CreateManagerRequest;
import icdf.sndc.ims.manager.ManagerDtos.ManagerResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/managers")
public class ManagerController {

    private final ManagerService managerService;

    public ManagerController(ManagerService managerService) {
        this.managerService = managerService;
    }

    @GetMapping
    public List<ManagerResponse> list() {
        return managerService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ManagerResponse create(@Valid @RequestBody CreateManagerRequest request) {
        return managerService.create(request);
    }
}
