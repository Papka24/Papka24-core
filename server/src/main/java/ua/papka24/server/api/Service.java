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

package ua.papka24.server.api;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.http.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dao.SignRecordDAO;
import ua.papka24.server.db.redis.RedisDAOManager;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Path("service")
public class Service extends REST {

    private final static Logger log = LoggerFactory.getLogger(Service.class);
    private static Type requestType = new TypeToken<List<Long>>() {}.getType();
    private static Type responseType = new TypeToken<List<Long>>() {}.getType();
    private static final ExpiringMap<String, Void> expiringMap = ExpiringMap.builder()
            .expiration(1, TimeUnit.MINUTES).expirationPolicy(ExpirationPolicy.CREATED).build();

    @POST
    @Path("checkegrpou")
    @Produces(MediaType.APPLICATION_JSON)
    public Response checkEgrpousExists(@HeaderParam("sessionid") String sessionId, String jsonRequest){
        Session session = SessionsPool.find(sessionId);
        if(session==null){
            return ERROR_NOT_FOUND;
        }
        if(TextUtils.isEmpty(jsonRequest)) {
            return ERROR_BAD_REQUEST;
        }
        String login = session.getUser().getLogin();
        //проверка первая на редис (на случай несколько нод)
        boolean isAllowRequest = false;
        try {
            if (RedisDAOManager.getInstance().allowErgrouRequest(login)) {
                isAllowRequest = true;
            }
        }catch (Exception ex){
            if (!expiringMap.containsKey(login)) {
                isAllowRequest = true;
            }
        }
        if(!isAllowRequest){
            return ERROR_TOO_MANY_REQUESTS;
        }
        RedisDAOManager.getInstance().saveErgrouRequest(login);
        expiringMap.put(login,null);
        try{
            List<Long> egrpouList = new Gson().fromJson(jsonRequest, requestType);
            List<Long> result = SignRecordDAO.getInstance().checkEgrpousExists(egrpouList);
            return Response.ok().entity(new Gson().toJson(result,responseType)).build();
        }catch (Exception ex){
            log.error("error checkEgrpousExists ", ex);
        }
        return ERROR_SERVER;
    }
}
