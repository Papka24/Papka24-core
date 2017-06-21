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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dao.CompanyDAO;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dto.ShareDTO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.db.redis.RedisDAOManager;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.service.events.EventManager;
import ua.papka24.server.service.events.event.ResourceChangeEvent;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.service.events.main.data.Notification;
import ua.papka24.server.api.DTO.ShareResRequest;
import ua.papka24.server.utils.logger.Event;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Path("share")
public class Share extends REST {

    private static final Logger log = LoggerFactory.getLogger("share");
    private static Type itemsMapType = new TypeToken<HashMap<Long, List<ShareDTO>>>() {}.getType();
    private static Type itemsListType = new TypeToken<List<ShareDTO>>() {}.getType();

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    /*
      Get sharing info by resource id
     */
    public Response get(@HeaderParam("sessionid") String sessionId, @HeaderParam("employee") String employee, @PathParam("id") Long id) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        }
        List<ShareDTO> addedEmails;
        UserDTO user = s.getUser();
        String login = user.getLogin();
        if (employee == null || employee.isEmpty() || !user.getCompanyDTO().hasEmployee(employee)) {
            addedEmails = ResourceDAO.getInstance().getShare(login, id);
        } else {
            addedEmails = ResourceDAO.getInstance().getShare(employee, id, false, user);
        }
        if(addedEmails==null){
            //try see as admin
            Long companyId = user.getCompanyId();
            if(companyId!=null) {
                List<String> admins = CompanyDAO.getInstance().getAdmins(companyId);
                if(admins.contains(login)){
                    addedEmails = ResourceDAO.getInstance().getShare(login, id, false, user);
                }
            }
        }
        if (addedEmails == null) {
            return ERROR_FORBIDDEN;
        } else {
            return Response.ok(new Gson().toJson(addedEmails, itemsListType)).build();
        }
    }

    @POST
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response share(@HeaderParam("sessionid") String sessionId, @PathParam("id") String rawId, String jsonRequest) {
        log.info("share:{}:{}", rawId, jsonRequest, Event.SHARE);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            String[] resourcesIds = rawId.split("_");
            ShareResRequest req;
            try {
                req = ShareResRequest.gson.fromJson(jsonRequest);
            } catch (JsonSyntaxException | IOException e) {
                return ERROR_BAD_REQUEST;
            }

            if (req.isEmpty()) {
                return ERROR_BAD_REQUEST;
            }
            if(req.getComment()!=null && "null".equals(req.getComment().trim().toLowerCase())){
                req.setComment(null);
            }
            Map<Long, List<ShareDTO>> result = new HashMap<>();
            List<ShareDTO> addedEmails;
            for (String resId : resourcesIds) {
                if (resId.chars().allMatch( Character::isDigit )) {
                    Long id  = Long.valueOf(resId);
                    addedEmails = ResourceDAO.getInstance().share(s.getUser(), id, req.getRequestList());
                    if (addedEmails != null) {
                        ResourceChangeEvent notification = Notification.builder().eventType(EventType.SHARED).eventData(req.getComment()).userLogin(s.getUser().getLogin()).shareResDTO(req.getRequestList()).id(id).createResourceChangeNotification();
                        EventManager.getInstance().addNotification(notification);
                        RedisDAOManager.getInstance().markChanged(SessionsPool.find(sessionId));
                        result.put(id, addedEmails);
                    }
                }
            }
            ResourcesChangeEvent wssNotification = Notification.builder().eventType(EventType.SHARED).userLogin(s.getUser().getLogin()).ids(resourcesIds).sharedEmails(req.getRequestList()).createWSSNotification();
            EventManager.getInstance().addNotification(wssNotification);
            if (result.size() == 0) {
                return ERROR_FORBIDDEN;
            } else {
                if (resourcesIds.length == 1) {
                    return Response.ok(new Gson().toJson(result.get(result.keySet().iterator().next()), itemsListType)).build();
                } else {
                    return Response.ok(new Gson().toJson(result, itemsMapType)).build();
                }
            }
        }
    }

    @DELETE
    @Path("/{id}/{email}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteShare(@HeaderParam("sessionid") String sessionId, @PathParam("id") Long id, @PathParam("email") String email) {
        log.info("deleteShare:{}:{}", id, email);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        }
        log.info("deleteShare:{}:{}:{}", s.getUser().getLogin(), id, email);
        if (ResourceDAO.getInstance().deleteShare(s.getUser(), id, email) > 0) {
            log.info("deleteShare:{}:{}:OK", id, email, Event.SHARE_DEL);
            ResourcesChangeEvent notification = Notification.builder().eventType(EventType.DELETE_SHARING).eventData(email).userLogin(s.getUser().getLogin()).id(id).createWSSNotification();
            EventManager.getInstance().addNotification(notification);
            return Response.ok().build();
        } else {
            return ERROR_FORBIDDEN;
        }
    }
}
