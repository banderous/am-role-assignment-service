package uk.gov.hmcts.reform.roleassignment.domain.service.common;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.roleassignment.domain.model.ExistingRole;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class IdamRoleService {

    private Map<String, List<ExistingRole>> existingRoleByActorId = new HashMap<>();
    SecurityUtils securityUtils;

    public void getUserId() {
        securityUtils.getUserId();
    }

    public void getUserRole() {
        securityUtils.getUserRolesHeader();
    }


    public Collection<ExistingRole> getIdamRoleAssignmentsForActor(String actorId) throws Exception {
        List<ExistingRole> existingRolesForActor = existingRoleByActorId.get(actorId);
        return existingRolesForActor == null ? new ArrayList<>() : existingRolesForActor;
    }
}