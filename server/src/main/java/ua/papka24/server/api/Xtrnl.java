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
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dao.SubscribeDAO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.db.dto.SubscribeFullInfoDTO;
import ua.papka24.server.service.events.EventManager;
import ua.papka24.server.db.dto.SubscribeInfoDTO;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


@Path("xtrnl")
public class Xtrnl extends REST {

    private static final Logger log = LoggerFactory.getLogger(Xtrnl.class);

    @POST
    @Path("/reg")
    @Produces(MediaType.APPLICATION_JSON)
    public Response addListener(@HeaderParam("sessionid") String sessionId, @QueryParam("sid") String sid, String subInfo) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = sid;
        }
        return process(sessionId, null, true, subInfo);
    }

    @POST
    @Path("/subscribe")
    @Produces(MediaType.APPLICATION_JSON)
    public Response subscribe(@HeaderParam("sessionid") String sessionId, @QueryParam("sid") String sid, String subInfo) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = sid;
        }
        return process(sessionId, null, true, subInfo);
    }

    @DELETE
    @Path("/unsubscribe")
    @Produces(MediaType.APPLICATION_JSON)
    public Response unsubscribe(@HeaderParam("sessionid") String sessionId, @QueryParam("sid") String sid, @QueryParam("subId") String subId, String subInfo) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = sid;
        }
        return process(sessionId, subId, false, subInfo);
    }

    //legacy v1
    @DELETE
    @Path("/unsubscribe/{subId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response unsubscribe(@HeaderParam("sessionid") String sessionId, @QueryParam("sid") String sid, @PathParam("subId") String subId) {
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = sid;
        }
        return process(sessionId, subId, false, "[]");
    }

    @GET
    @Path("/subscriptions")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@HeaderParam("sessionid") String sessionId) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            List<SubscribeFullInfoDTO> subscriptions = EventManager.getInstance().getSubscriptions(s.getUser());
            if (subscriptions == null) {
                return ERROR_SERVER;
            }
            return Response.ok().entity(new Gson().toJson(subscriptions)).build();
        }
    }

    private Response process(String sid, String subId, boolean subscribeOperation, String subInfo) {
        if (subscribeOperation && (TextUtils.isEmpty(subInfo))) {
            return ERROR_BAD_REQUEST;
        } else {
            log.info("new process subscribe request:{}:{}", subInfo, subscribeOperation);
        }
        List<SubscribeInfoDTO> subscribes = new ArrayList<>();
        try {
            if(!subscribeOperation && !TextUtils.isEmpty(subId)){
                SubscribeInfoDTO ds = new SubscribeInfoDTO(subId);
                subscribes.add(ds);
            }
            if(!TextUtils.isEmpty(subInfo)){
                subscribes = parseJson(subInfo, subscribes);
            }
        } catch (JsonSyntaxException jse) {
            log.error("incorrect json", jse);
            return ERROR_BAD_REQUEST;
        }
        if(subscribeOperation) {
            subscribes = subscribes.stream().filter(e -> {
                try {
                    new URL(e.getUrl());
                    return true;
                } catch (Exception e1) {
                    return false;
                }
            }).collect(Collectors.toList());
        }
        if(subscribes.isEmpty()){
            log.info("subscribes not found");
            return ERROR_BAD_REQUEST;
        }
        List<SubscribeDAO.SubscribeResult> subres;
        if (subscribeOperation) {
            subres = EventManager.getInstance().addSubscribers(sid, subscribes);
        } else {
            subres = EventManager.getInstance().deleteSubscribers(sid, subscribes);
        }
        if (subres.size() > 0) {
            if (subres.size() == 1 && -401 == subres.get(0).getSubId()) {
                return Response.status(401).entity(new Gson().toJson(subres, new TypeToken<List<SubscribeDAO.SubscribeResult>>() {
                }.getType())).build();
            }
            return Response.ok(new Gson().toJson(subres, new TypeToken<List<SubscribeDAO.SubscribeResult>>() {
            }.getType())).build();
        } else {
            return ERROR_SERVER;
        }
    }

    @GET
    @Path("synchronize")
    @Produces(MediaType.APPLICATION_JSON)
    public Response synchronizeResources(@HeaderParam("sessionid") String sessionId){
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            List<SubscribeFullInfoDTO> subscriptions = EventManager.getInstance().getSubscriptions(s.getUser());
            if (subscriptions == null) {
                return ERROR_SERVER;
            }
            List<ResourceDTO> collect = subscriptions.stream()
                    .filter(e->NumberUtils.isDigits(e.getId()))
                    .map(e -> ResourceDAO.getInstance().get(Long.valueOf(e.getId())))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return Response.ok().entity(new Gson().toJson(collect)).build();
        }
    }

    private List<SubscribeInfoDTO> parseJson(String json, List<SubscribeInfoDTO> subscribes) {
        if (json.indexOf('[') == 0) {
            subscribes = new Gson().fromJson(json, new TypeToken<List<SubscribeInfoDTO>>() {
            }.getType());
        } else {
            subscribes.add(new Gson().fromJson(json, SubscribeInfoDTO.class));
        }
        return subscribes;
    }
}