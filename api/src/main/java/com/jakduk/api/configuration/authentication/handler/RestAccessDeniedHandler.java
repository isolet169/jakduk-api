package com.jakduk.api.configuration.authentication.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jakduk.api.restcontroller.vo.RestErrorResponse;
import com.jakduk.core.common.util.ObjectMapperUtils;
import com.jakduk.core.exception.ServiceError;
import com.jakduk.core.service.CommonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author pyohwan
 * 16. 6. 22 오전 12:44
 */

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Autowired
    CommonService commonService;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setCharacterEncoding("utf-8");

        RestErrorResponse restErrorResponse = new RestErrorResponse(ServiceError.UNAUTHORIZED_ACCESS);
        String errorJson = ObjectMapperUtils.getObjectMapper().writeValueAsString(restErrorResponse);

        PrintWriter out = response.getWriter();
        out.print(errorJson);
        out.flush();
        out.close();
    }
}
