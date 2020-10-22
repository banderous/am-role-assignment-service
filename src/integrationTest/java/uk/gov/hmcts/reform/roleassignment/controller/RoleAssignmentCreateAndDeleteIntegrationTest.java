package uk.gov.hmcts.reform.roleassignment.controller;

import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.roleassignment.BaseTest;
import uk.gov.hmcts.reform.roleassignment.MockUtils;
import uk.gov.hmcts.reform.roleassignment.domain.model.AssignmentRequest;
import uk.gov.hmcts.reform.roleassignment.domain.model.UserRoles;
import uk.gov.hmcts.reform.roleassignment.domain.service.security.IdamRoleService;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;
import uk.gov.hmcts.reform.roleassignment.launchdarkly.FeatureConditionEvaluation;
import uk.gov.hmcts.reform.roleassignment.oidc.JwtGrantedAuthoritiesConverter;

import javax.inject.Inject;
import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class RoleAssignmentCreateAndDeleteIntegrationTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(RoleAssignmentCreateAndDeleteIntegrationTest.class);

    private static final String ASSIGNMENT_ID = "f7edb29d-e421-450c-be66-a10169b04f0a";
    private static final String ACTOR_ID = "123e4567-e89b-42d3-a456-556642445612";
    private static final String COUNT_HISTORY_RECORDS_QUERY = "SELECT count(1) AS n FROM role_assignment_history";
    private static final String COUNT_ASSIGNMENT_RECORDS_QUERY = "SELECT count(1) AS n FROM role_assignment";
    private static final String GET_ACTOR_FROM_ASSIGNMENT_QUERY = "SELECT actor_id FROM role_assignment WHERE id IN "
        + "(SELECT id FROM role_assignment_history WHERE actor_id = ?)";
    private static final String GET_ASSIGNMENT_STATUS_QUERY = "SELECT status FROM role_assignment_history "
        + "WHERE actor_id = ? ORDER BY created";
    public static final String CREATED = "CREATED";
    public static final String APPROVED = "APPROVED";
    public static final String LIVE = "LIVE";
    public static final String DELETED = "DELETED";
    public static final String DELETE_APPROVED = "DELETE_APPROVED";
    private static final String AUTHORISED_SERVICE = "ccd_gw";

    private MockMvc mockMvc;
    private JdbcTemplate template;

    @Inject
    private WebApplicationContext wac;

    @Inject
    private JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter;

    @Autowired
    private DataSource ds;

    @Mock
    private Authentication authentication;

    @Mock
    private SecurityContext securityContext;

    @MockBean
    private IdamRoleService idamRoleService;

    @MockBean
    private FeatureConditionEvaluation featureConditionEvaluation;

    @Before
    public void setUp() throws Exception {
        template = new JdbcTemplate(ds);
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
        MockitoAnnotations.initMocks(this);
        String uid = "6b36bfc6-bb21-11ea-b3de-0242ac130006";
        UserRoles roles = UserRoles.builder()
            .uid(uid)
            .roles(Arrays.asList("caseworker", "am-import"))
            .build();

        doReturn(roles).when(idamRoleService).getUserRoles(anyString());
        doReturn(authentication).when(securityContext).getAuthentication();
        SecurityContextHolder.setContext(securityContext);
        doReturn(true).when(featureConditionEvaluation).preHandle(any(),any(),any());
        MockUtils.setSecurityAuthorities(authentication, MockUtils.ROLE_CASEWORKER);
        UserInfo userInfo = UserInfo.builder()
            .uid("6b36bfc6-bb21-11ea-b3de-0242ac130006")
            .sub("emailId@a.com")
            .build();
        ReflectionTestUtils.setField(
            jwtGrantedAuthoritiesConverter,
            "userInfo", userInfo

        );
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql",
            "classpath:sql/insert_organisation_role_assignment.sql"
            })
    public void shouldCreateRoleAssignmentsWithReplaceExistingTrue() throws Exception {
        logger.info(" History record count before create assignment request {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count before create assignment request {}", getAssignmentRecordsCount());
        AssignmentRequest assignmentRequest = TestDataBuilder.createRoleAssignmentRequest(
            false, false);
        logger.info(" assignmentRequest :  {}", mapper.writeValueAsString(assignmentRequest));
        final String url = "/am/role-assignments";


        mockMvc.perform(post(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders())
                            .content(mapper.writeValueAsBytes(assignmentRequest))
        ).andExpect(status().is(201)).andReturn();

        logger.info(" -- Role Assignment record created successfully -- ");
        List<String> statusList = getStatusFromHistory();
        assertNotNull(statusList);
        assertEquals(3, statusList.size());
        assertEquals(CREATED, statusList.get(0));
        assertEquals(APPROVED, statusList.get(1));
        assertEquals(LIVE, statusList.get(2));
        assertEquals(3, getAssignmentRecordsCount().longValue());
        assertEquals(ACTOR_ID, getActorFromAssignmentTable());
        logger.info(" History record count after create request : {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count after create assignment request: {}", getAssignmentRecordsCount());
        logger.info(" LIVE table actor Id after create assignment request : {}", getActorFromAssignmentTable());

        //Insert role assignment records with replace existing is True
        AssignmentRequest assignmentRequestWithReplaceExistingTrue = TestDataBuilder.createRoleAssignmentRequest(
            true,
            true
        );
        logger.info(
            "** Creating another role assignment record with request :   {}",
            mapper.writeValueAsString(assignmentRequestWithReplaceExistingTrue)
        );

        mockMvc.perform(post(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders())
                            .content(mapper.writeValueAsBytes(assignmentRequestWithReplaceExistingTrue))
        ).andExpect(status().is(201)).andReturn();

        List<String> newStatusList = getStatusFromHistory();
        assertNotNull(newStatusList);
        assertEquals(8, newStatusList.size());
        assertEquals(CREATED, newStatusList.get(0));
        assertEquals(APPROVED, newStatusList.get(1));
        assertEquals(LIVE, newStatusList.get(2));
        assertEquals(DELETE_APPROVED, newStatusList.get(3));
        assertEquals(CREATED, newStatusList.get(4));
        assertEquals(APPROVED, newStatusList.get(5));
        assertEquals(DELETED, newStatusList.get(6));
        assertEquals(LIVE, newStatusList.get(7));
        assertEquals(3, getAssignmentRecordsCount().longValue());
        assertEquals(ACTOR_ID, getActorFromAssignmentTable());
        logger.info(" History record count after create request : {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count after create assignment request : {}", getAssignmentRecordsCount());
        logger.info(" LIVE table actor Id after create assignment request : {}", getActorFromAssignmentTable());
    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql",
            "classpath:sql/insert_assignment_records_to_delete.sql"})
    public void shouldDeleteRoleAssignmentsByProcessAndReference() throws Exception {

        logger.info(" Method shouldDeleteRoleAssignmentsByProcessAndReference starts :");
        logger.info(" History record count before create assignment request : {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count before create assignment request : {}", getAssignmentRecordsCount());
        final String url = "/am/role-assignments";

        mockMvc.perform(delete(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders())
                            .param("process", "S-052")
                            .param("reference", "S-052")
        )
            .andExpect(status().is(204))
            .andReturn();

        assertAssignmentRecords();

    }

    @Test
    @Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts =
        {"classpath:sql/role_assignment_clean_up.sql",
            "classpath:sql/insert_assignment_records_to_delete.sql"})
    public void shouldDeleteRoleAssignmentsByAssignmentId() throws Exception {

        logger.info(" Method shouldDeleteRoleAssignmentsByAssignmentId starts : ");
        logger.info(" History record count before create assignment request : {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count before create assignment request : {}", getAssignmentRecordsCount());
        final String url = "/am/role-assignments/" + ASSIGNMENT_ID;

        mockMvc.perform(delete(url)
                            .contentType(JSON_CONTENT_TYPE)
                            .headers(getHttpHeaders())
        )
            .andExpect(status().is(204))
            .andReturn();

        assertAssignmentRecords();
    }



    private void assertAssignmentRecords() {
        logger.info(" History record count after create assignment request : {}", getHistoryRecordsCount());
        logger.info(" LIVE table record count after create assignment request : {}", getAssignmentRecordsCount());
        List<String> statusList = getStatusFromHistory();
        assertEquals(5, statusList.size());
        assertEquals(CREATED, statusList.get(0));
        assertEquals(APPROVED, statusList.get(1));
        assertEquals(LIVE, statusList.get(2));
        assertEquals(DELETE_APPROVED, statusList.get(3));
        assertEquals(DELETED, statusList.get(4));
    }

    private Integer getHistoryRecordsCount() {
        return template.queryForObject(COUNT_HISTORY_RECORDS_QUERY, Integer.class);
    }

    private Integer getAssignmentRecordsCount() {
        return template.queryForObject(COUNT_ASSIGNMENT_RECORDS_QUERY, Integer.class);
    }

    @NotNull
    private HttpHeaders getHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(AUTHORIZATION, "Bearer user1");
        String s2SToken = MockUtils.generateDummyS2SToken(AUTHORISED_SERVICE);
        headers.add("ServiceAuthorization", "Bearer " + s2SToken);
        return headers;
    }

    public List<String> getStatusFromHistory() {
        return template.queryForList(GET_ASSIGNMENT_STATUS_QUERY, new Object[]{ACTOR_ID}, String.class);
    }

    public String getActorFromAssignmentTable() {
        return template.queryForObject(GET_ACTOR_FROM_ASSIGNMENT_QUERY, new Object[]{ACTOR_ID}, String.class);
    }
}
