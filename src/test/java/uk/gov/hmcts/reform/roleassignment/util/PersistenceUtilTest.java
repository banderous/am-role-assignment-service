package uk.gov.hmcts.reform.roleassignment.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.roleassignment.data.RoleAssignmentEntity;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@RunWith(MockitoJUnitRunner.class)
class PersistenceUtilTest {

    @InjectMocks
    PersistenceUtil persistenceUtil = new PersistenceUtil();


    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void convertRoleAssignmentToHistoryEntity() throws IOException {
        assertNotNull(persistenceUtil.convertRoleAssignmentToHistoryEntity(
            TestDataBuilder.buildRoleAssignment(Status.LIVE),
            TestDataBuilder.buildRequestEntity(TestDataBuilder.buildRequest(Status.APPROVED, false))));
    }

    @Test
    void convertRequestToEntity() {
        assertNotNull(persistenceUtil.convertRequestToEntity(TestDataBuilder.buildRequest(Status.APPROVED, false)));
    }

    @Test
    void convertRoleAssignmentToEntity() throws IOException {
        assertNotNull(persistenceUtil.convertRoleAssignmentToEntity(
            TestDataBuilder.buildRoleAssignment(Status.LIVE),
            true
        ));
    }

    @Test
    void convertActorCacheToEntity() throws IOException {
        assertNotNull(persistenceUtil.convertActorCacheToEntity(TestDataBuilder.buildActorCache()));
    }

    @Test
    void convertHistoryEntityToRoleAssignment() throws IOException {
        assertNotNull(persistenceUtil.convertHistoryEntityToRoleAssignment(
            TestDataBuilder.buildHistoryEntity(
                TestDataBuilder.buildRoleAssignment(Status.LIVE),
                TestDataBuilder.buildRequestEntity(TestDataBuilder.buildRequest(Status.APPROVED, false)))));
    }

    @Test
    void convertEntityToRoleAssignment() throws IOException {
        assertNotNull(persistenceUtil.convertEntityToRoleAssignment(
            TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder.buildRoleAssignment(Status.LIVE))));
    }

    @Test
    void convertEntityToRoleAssignmentWithAutorisations() throws IOException {

        RoleAssignmentEntity entity =  TestDataBuilder.buildRoleAssignmentEntity(TestDataBuilder
                                                            .buildRoleAssignment(Status.LIVE));
        entity.setAuthorisations("dev;tester");
        assertNotNull(persistenceUtil.convertEntityToRoleAssignment(
            entity));
    }
}
