package uk.gov.hmcts.reform.roleassignment.feignclients.configuration;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.roleassignment.apihelper.Constants;
import uk.gov.hmcts.reform.roleassignment.util.SecurityUtils;

@Service
public class DatastoreFeignClientInterceptor {

    @Autowired SecurityUtils securityUtils;

    @Bean
    public RequestInterceptor requestInterceptor() {
        HttpHeaders headers = securityUtils.authorizationHeaders();
        return requestTemplate -> {
            requestTemplate.header(Constants.SERVICE_AUTHORIZATION, headers.get(Constants.SERVICE_AUTHORIZATION));
            requestTemplate.header(HttpHeaders.AUTHORIZATION, headers.get(HttpHeaders.AUTHORIZATION));
        };
    }
}
