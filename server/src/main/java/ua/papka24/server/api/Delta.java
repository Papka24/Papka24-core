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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.DeltaHttpResponse;
import ua.papka24.server.api.DTO.FullDeltaHttpResponse;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.scylla.history.HistoryManager;
import ua.papka24.server.db.scylla.history.model.ResourceDTOFullHolder;
import ua.papka24.server.db.scylla.history.model.ResourceDTOHolder;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static ua.papka24.server.utils.websocket.WebSocketServer.historyControlTime;


@Path("delta")
public class Delta extends REST{

    private static final Logger log = LoggerFactory.getLogger(Delta.class);

    @GET
    @Path("/time")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTime(@HeaderParam("sessionid") String sessionId){
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            return Response.ok().entity(String.valueOf(new java.util.Date().getTime())).build();
        }
    }

    @GET
    @Path("/count")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCount(@HeaderParam("sessionid") String sessionId, @QueryParam("from") Long dateStart, @QueryParam("to") Long dateEnd){
        Session session = SessionsPool.find(sessionId);
        if (session == null) {
            return ERROR_FORBIDDEN;
        } else {
            if(dateStart == null){
                return ERROR_BAD_REQUEST;
            }
            if(dateEnd == null){
                dateEnd = System.currentTimeMillis();
            }
            DeltaHttpResponse response = new DeltaHttpResponse();
            response.timestamp = System.currentTimeMillis();
            String login = session.getUser().getLogin();
            try {
                if (dateStart > historyControlTime) {
                    response.num = HistoryManager.getInstance().getEventsCount(login, dateStart, dateEnd);
                }else{
                    response.num = -1;
                }
            }catch (Exception ex){
                response.num = -1;
                log.error("error get delta", ex);
            }
            return Response.ok().entity(Main.gson.toJson(response)).build();
        }
    }

    @GET
    @Path("/resources")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getResources(@HeaderParam("sessionid") String sessionId, @QueryParam("from") Long dateStart, @QueryParam("to") Long dateEnd){
        Session session = SessionsPool.find(sessionId);
        if (session == null) {
            return ERROR_FORBIDDEN;
        } else {
            if(dateStart == null){
                return ERROR_BAD_REQUEST;
            }
            DeltaHttpResponse response = new DeltaHttpResponse();
            response.timestamp = System.currentTimeMillis();
            if(dateEnd == null){
                dateEnd = response.timestamp;
            }
            String login = session.getUser().getLogin();
            Long companyId = session.getUser().getCompanyId();
            try {
                if (dateStart > historyControlTime) {
                    ResourceDTOHolder eventsInfo = HistoryManager.getInstance().getEventsInfo(session, login, dateStart, dateEnd);
                    response.create = eventsInfo.createdResources;
                    response.update = eventsInfo.updatedResources;
                    response.delete = eventsInfo.deleteResources;
                }else{
                    response.reset = ResourceDAO.getInstance().search(null, login, null, companyId);
                }
            }catch (Exception ex){
                response.reset = ResourceDAO.getInstance().search(null, login, null, companyId);
                log.error("error get delta", ex);
            }
            return Response.ok().entity(Main.gson.toJson(response)).build();
        }
    }

    @GET
    @Path("/fresources")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getResourcesFullInfo(@HeaderParam("sessionid") String sessionId, @QueryParam("from") Long dateStart, @QueryParam("to") Long dateEnd){
        Session session = SessionsPool.find(sessionId);
        if (session == null) {
            return ERROR_FORBIDDEN;
        } else {
            if(dateStart == null){
                return ERROR_BAD_REQUEST;
            }
            FullDeltaHttpResponse response = new FullDeltaHttpResponse();
            if(dateEnd == null){
                dateEnd = System.currentTimeMillis();
            }
            String login = session.getUser().getLogin();
            Long companyId = session.getUser().getCompanyId();
            try {
                ResourceDTOFullHolder fullEventsInfo = HistoryManager.getInstance().getFullEventsInfo(session, login, dateStart, dateEnd);
                response.create = fullEventsInfo.createdResources;
                response.update = fullEventsInfo.updatedResources;
                response.delete = fullEventsInfo.deleteResources;
            }catch (Exception ex){
                response.reset = ResourceDAO.getInstance().search(null, login, null, companyId);
                log.error("error get delta", ex);
            }
            return Response.ok().entity(Main.gson.toJson(response)).build();
        }
    }
}
