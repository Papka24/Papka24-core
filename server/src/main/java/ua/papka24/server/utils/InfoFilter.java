/*
 * Copyright (c) 2017. iDoc LLC
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     (1) Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *     (2) Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *
 *     (3)The name of the author may not be used to
 *     endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package ua.papka24.server.utils;

import com.sun.jersey.api.container.ContainerException;
import com.sun.jersey.core.util.ReaderWriter;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class InfoFilter  implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(InfoFilter.class);

    @Override
    public ContainerRequest filter(ContainerRequest request) {
        String sessionId = request.getHeaderValue("sessionid");
        String realIp = request.getHeaderValue("x-forwarded-for");

        MDC.remove("message");
        MDC.remove("user_login");
        MDC.remove("session");
        MDC.remove("real_ip");
        String login = "";
        try {
            if (sessionId != null) {
                Session session = SessionsPool.find(sessionId);
                if(session!=null) {
                    login = session.getUser().getLogin();
                    MDC.put("user_login", login);
                    MDC.put("session", sessionId);
                }

            }
            MDC.put("request", request.getPath());
            if (!request.getPath().equals("login") && sessionId != null) {
                MDC.put("message", readBody(request));
            }
            if(realIp!=null){
                MDC.put("real_ip",realIp);
            }
        }catch (Exception ex){
            log.error("error in filter",ex);
        }
        return request;
    }

    private String readBody(ContainerRequest request){
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = request.getEntityInputStream();
        final StringBuilder content = new StringBuilder();
        try {
            if (in.available() > 0) {
                ReaderWriter.writeTo(in, out);
                byte[] requestEntity = out.toByteArray();
                printEntity(content, requestEntity);
                request.setEntityInputStream(new ByteArrayInputStream(requestEntity));
                out.close();
                in.close();
            }
        } catch (IOException ex) {
            throw new ContainerException(ex);
        }
        return content.toString();
    }

    private void printEntity(StringBuilder content, byte[] entity) throws IOException {
        if (entity.length == 0) {
            return;
        }
        content.append(new String(entity));
    }
}