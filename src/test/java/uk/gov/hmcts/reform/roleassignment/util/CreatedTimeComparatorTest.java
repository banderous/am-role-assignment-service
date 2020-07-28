package uk.gov.hmcts.reform.roleassignment.util;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.roleassignment.domain.model.enums.Status;
import uk.gov.hmcts.reform.roleassignment.helper.TestDataBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

class CreatedTimeComparatorTest {

    CreatedTimeComparator sut = new CreatedTimeComparator();

    Integer result;

    @Test
    void compare() throws IOException {
        result = sut.compare(TestDataBuilder.buildRoleAssignment(Status.CREATED),
                             TestDataBuilder.buildRoleAssignment(Status.CREATED));
        assertEquals(-1 ,result);

    }
}
