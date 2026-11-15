package icdf.sndc.ims.manager;

import icdf.sndc.ims.common.ApiException;
import icdf.sndc.ims.common.TextNormalizer;
import icdf.sndc.ims.manager.ManagerDtos.CreateManagerRequest;
import icdf.sndc.ims.manager.ManagerDtos.ManagerResponse;
import icdf.sndc.ims.user.User;
import icdf.sndc.ims.user.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ManagerService {

    private final ManagerRepository managerRepository;
    private final UserRepository userRepository;

    public ManagerService(ManagerRepository managerRepository, UserRepository userRepository) {
        this.managerRepository = managerRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ManagerResponse> list() {
        return managerRepository.findAll(Sort.by("displayName")).stream()
                .map(ManagerResponse::from)
                .toList();
    }

    @Transactional
    public ManagerResponse create(CreateManagerRequest req) {
        String name = TextNormalizer.normalize(req.displayName());
        if (name == null) {
            throw ApiException.invalidField("displayName", "Display name is required");
        }
        User user = null;
        if (req.userId() != null) {
            user = userRepository.findById(req.userId())
                    .orElseThrow(() -> ApiException.invalidField("userId", "User does not exist"));
        }
        return ManagerResponse.from(managerRepository.save(new Manager(name, user)));
    }

    /** 供其他模組使用：取得管理人，不存在時回 400（指出欄位）。 */
    @Transactional(readOnly = true)
    public Manager requireForField(UUID id, String field) {
        return managerRepository.findById(id)
                .orElseThrow(() -> ApiException.invalidField(field, "Manager does not exist"));
    }
}
