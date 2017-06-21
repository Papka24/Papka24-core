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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.redis.RedisDAOManager;
import ua.papka24.server.api.DTO.ExternalSignRequest;
import ua.papka24.server.api.DTO.ExternalSignResponse;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Path("externalSign")
public class ExternalSign extends REST {

    private static Gson gson = new Gson();
    private static final long LIFETIME = 60 * 60 * 1000;
    private static final ExpiringMap<String, ExternalSignRequest> requests = ExpiringMap.builder()
            .expirationPolicy(ExpirationPolicy.CREATED).expiration(60, TimeUnit.MINUTES).build();
    private static final Logger log = LoggerFactory.getLogger(ExternalSign.class);

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response generate(@HeaderParam("sessionid") String sessionId, String requestData){
        Session session = SessionsPool.find(sessionId);
        if(session==null){
            return ERROR_NOT_FOUND;
        }
        ExternalSignRequest request;
        try {
            request = ExternalSignRequest.gson.fromJson(requestData);
        } catch (Exception jse){
            return ERROR_BAD_REQUEST;
        }

        if(request.resourceId == null || request.resourceId <=0 || request.redirectURL == null || request.redirectURL.length()<=0){
            return ERROR_BAD_REQUEST;
        }
        boolean allowed = ResourceDAO.getInstance().checkResourceAccess(session.getUser().getLogin(), request.resourceId, null);
        if(!allowed){
            return ERROR_FORBIDDEN;
        }

        String encryptedData = UUID.randomUUID().toString();
        long ttl = new java.util.Date().getTime();
        if(request.ttl==null){
            // Если время не задано берем 60 минут
            ttl += LIFETIME;
        } else {
            if (request.ttl > 1440){
                // Если время указано более чем 24 часа, ставим 24
                request.ttl = 1440L;
            }
            ttl += request.ttl * 60 * 1000;
        }
        request.initiator = session.getUser().getLogin();
        request.ttl = ttl;
        requests.put(encryptedData,request);
        RedisDAOManager.getInstance().saveExternalSignRequest(encryptedData, request);

        ExternalSignResponse response = new ExternalSignResponse();
        response.id = encryptedData;
        response.ttl = ttl;
        return Response.ok().entity(gson.toJson(response)).build();
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getResource(@PathParam("id") String id){
        if(id==null){
            return ERROR_BAD_REQUEST;
        }
        ExternalSignRequest request = requests.get(id);
        if(request==null){
            request = RedisDAOManager.getInstance().getExternalSignRequest(id);
        }
        if(request == null){
            return ERROR_NOT_FOUND;
        }
        if(checkExpired(request,id)){
            return ERROR_FORBIDDEN;
        }

        ResourceDTO resourceDTO = ResourceDAO.getInstance().get(request.resourceId);
        if(resourceDTO==null){
            return ERROR_SERVER;
        }
        request.resource = resourceDTO;
        return Response.ok().entity(ExternalSignRequest.gson.toJson(request)).build();
    }

    private boolean checkExpired(ExternalSignRequest request, String id){
        Date now = new Date();
        Date ttl = new Date(request.ttl);
        if(now.after(ttl)){
            requests.remove(id);
            RedisDAOManager.getInstance().removeExternalSignRequest(id);
            log.info("request expired:{}",id);
            return true;
        }else{
            return false;
        }
    }

    @POST
    @Path("/{id}")
    public Response sign(@PathParam("id") String id, String rawSign){
        List<String> newSigns = new ArrayList<>();
        if (rawSign.indexOf('[') == 0) {
            newSigns = new Gson().fromJson(rawSign, new TypeToken<List<String>>() {
            }.getType());
        } else {
            newSigns.add(rawSign);
        }
        ExternalSignRequest request = requests.get(id);
        if(request==null){
            request = RedisDAOManager.getInstance().getExternalSignRequest(id);
        }
        if(request!=null) {
            if(checkExpired(request,id)){
                return ERROR_FORBIDDEN;
            }
            List<String> resultSigns = ResourceDAO.getInstance().addSign(request.initiator +"@", request.resourceId, newSigns);
            if (newSigns == null) {
                return ERROR_FORBIDDEN;
            } else {
                requests.remove(id);
                return Response.ok(gson.toJson(resultSigns, new TypeToken<List<String>>() {
                }.getType())).build();
            }
        }else{
            return ERROR_NOT_FOUND;
        }
    }
}
