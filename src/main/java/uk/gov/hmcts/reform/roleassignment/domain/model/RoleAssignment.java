package uk.gov.hmcts.reform.roleassignment.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.ActorIdType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Classification;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.GrantType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleCategory;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.RoleType;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;


@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public  class RoleAssignment {

    private UUID id; //this will be generated by application while saving request entity.
    private ActorIdType actorIdType;
    private UUID actorId;
    private RoleType roleType;
    private String roleName;
    private Classification classification;
    private GrantType grantType;
    private RoleCategory roleCategory;
    private boolean readOnly;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;


    private String process; //need to map from request
    private String reference; //need to map from request
    private Integer statusSequence; //this will be populated from status entity. Need to extend status entity.
    private Status status; //this will be set by app default = created
    private LocalDateTime created; //this will be set by app
    private String log; //this will be set app based on drool validation rule name on individual assignments.
    private Request request;
    private Map<String, JsonNode> attributes;
    private JsonNode notes;
}
