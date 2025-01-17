package uk.gov.hmcts.reform.roleassignment.befta;

import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.befta.DefaultTestAutomationAdapter;
import uk.gov.hmcts.befta.player.BackEndFunctionalTestScenarioContext;
import uk.gov.hmcts.reform.roleassignment.befta.utils.TokenUtils;
import uk.gov.hmcts.reform.roleassignment.befta.utils.UserTokenProviderConfig;
import uk.gov.hmcts.reform.roleassignment.util.EnvironmentVariableUtils;

import java.util.Date;
import java.util.UUID;

@Slf4j
public class RoleAssignmentTestAutomationAdapter extends DefaultTestAutomationAdapter {
    public static RoleAssignmentTestAutomationAdapter INSTANCE = new RoleAssignmentTestAutomationAdapter();

    @Override
    public Object calculateCustomValue(BackEndFunctionalTestScenarioContext scenarioContext, Object key) {
        //the docAMUrl is is referring the self link in PR
        switch (key.toString()) {
            case ("generateUUID"):
                return UUID.randomUUID();
            case ("generateCaseId"):
                return generateCaseId();
            case ("generateS2STokenForCcd"):
                return new TokenUtils().generateServiceToken(buildCcdSpecificConfig());
            default:
                return super.calculateCustomValue(scenarioContext, key);
        }
    }

    private Object generateCaseId() {
        long currentTime = new Date().getTime();
        String time = Long.toString(currentTime);
        return time + ("0000000000000000".substring(time.length()));
    }

    private UserTokenProviderConfig buildCcdSpecificConfig() {
        UserTokenProviderConfig config = new UserTokenProviderConfig();
        config.setMicroService("ccd_data");
        config.setSecret(System.getenv("CCD_DATA_S2S_SECRET"));
        config.setS2sUrl(EnvironmentVariableUtils.getRequiredVariable("IDAM_S2S_URL"));
        return config;
    }
}
