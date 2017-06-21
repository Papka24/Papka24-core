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

import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.DeltaHttpResponse;
import ua.papka24.server.api.DTO.ShareAllResultDTO;
import ua.papka24.server.api.helper.CompanyHelper;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dto.FilterDTO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.db.scylla.history.HistoryManager;
import ua.papka24.server.db.scylla.history.model.ResourceDTOHolder;
import ua.papka24.server.security.CryptoManager;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.service.events.EventManager;
import ua.papka24.server.service.events.event.ResourceChangeEvent;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.service.events.main.data.Notification;
import ua.papka24.server.utils.datetime.DateTimeUtils;
import ua.papka24.server.utils.logger.Event;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static ua.papka24.server.utils.websocket.WebSocketServer.historyControlTime;


@Path("resource")
public class Resource extends REST {

    private static final Logger log  = LoggerFactory.getLogger(Resource.class);
    private static Type itemsListType = new TypeToken<List<ResourceDTO>>() {}.getType();

    @POST
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    public Response search(@HeaderParam("sessionid") String sessionId, @HeaderParam("employee") String employee, String filterJson) {
        Session session = SessionsPool.find(sessionId);
        if (session == null) {
            return ERROR_FORBIDDEN;
        } else {
            List<ResourceDTO> result;
            FilterDTO filter;
            if (StringUtils.isEmpty(filterJson)) {
                filter = new FilterDTO();
            }else {
                try {
                    filter = FilterDTO.gson.fromJson(filterJson);
                    if (!FilterDTO.isFilterCorrect(filter)) {
                        return ERROR_BAD_REQUEST;
                    }
                } catch (Exception e) {
                    Main.log.error(e.toString());
                    return ERROR_BAD_REQUEST;
                }
            }
            result = CompanyHelper.search(session.getUser(),employee,filter);
            if (result == null) {
                return ERROR_NOT_FOUND;
            }
            return Response.ok(Main.gson.toJson(result, itemsListType)).build();
        }
    }

    @GET
    @Path("/delta/{timestamp}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getDelta(@HeaderParam("sessionid") String sessionId, @PathParam("timestamp") Long timestamp){
        Session session = SessionsPool.find(sessionId);
        if (session == null) {
            return ERROR_FORBIDDEN;
        } else {
            DeltaHttpResponse response = new DeltaHttpResponse();
            response.timestamp = new java.util.Date().getTime();
            String login = session.getUser().getLogin();
            Long companyId = session.getUser().getCompanyId();
            try {
                if (timestamp != null && (timestamp >= (new java.util.Date().getTime() - DateTimeUtils.ONE_WEEK) && timestamp > historyControlTime)) {
                    ResourceDTOHolder eventsInfo = HistoryManager.getInstance().getEventsInfo(session, login, timestamp);
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
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@HeaderParam("sessionid") String sessionId, @PathParam("id") Long id) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            UserDTO user = s.getUser();
            ResourceDTO result = ResourceDAO.getInstance().getResource(user,id);
            if (result == null) {
                return ERROR_NOT_FOUND;
            } else {
                return Response.ok(ResourceDTO.gson.toJson(result)).build();
            }
        }
    }

    @GET
    @Path("/withsign/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getWithSign(@HeaderParam("sessionid") String sessionId, @PathParam("id") Long id) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            ResourceDTO result = ResourceDAO.getInstance().getWithSign(s.getUser().getLogin(), id, s.getUser());
            byte[] file = new byte[0];
            if (result == null) {
                return ERROR_NOT_FOUND;
            } else {
                try {
                    byte[] data = Files.readAllBytes(Paths.get(Main.CDNPath, result.getSrc(), result.getHash()));
                    if (result.getSigns()== null){
                        file = data;
                    } else {
                        file = CryptoManager.joinSignedData(data, result.getSigns());
                    }
                } catch (IOException e) {
                    log.error("error withsign",e);
                }
                return Response.ok(file).build();
            }
        }
    }

    @PUT
    @Path("/name/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response put(@HeaderParam("sessionid") String sessionId, @PathParam("id") Long id, String newName) {
        Session session = SessionsPool.find(sessionId);
        if (session == null) {
            return ERROR_FORBIDDEN;
        } else {
            if (newName.length() > 128) {
                newName = newName.substring(0, 128);
            }
            newName = newName.replace("<", "&#60;").replace(">", "&#62;");
            int result = ResourceDAO.getInstance().rename(session.getUser(), id, newName);
            if (result == 0) {
                return ERROR_NOT_FOUND;
            } else if (result == -1) {
                return ERROR_FORBIDDEN;
            } else {
                ResourcesChangeEvent wssNotification = Notification.builder().eventType(EventType.RENAME).userLogin(session.getUser().getLogin()).id(id).eventData(newName).createWSSNotification();
                EventManager.getInstance().addNotification(wssNotification);
                return Response.ok(newName).build();
            }
        }
    }

    @PUT
    @Path("/restore/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response restore(@HeaderParam("sessionid") String sessionId, @PathParam("id") String id) {
        log.info("restore:{}",id);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            int result;
            UserDTO user = SessionsPool.find(sessionId).getUser();
            String[] splitId = id.split("_");
            if (splitId.length > 0) {
                ArrayList<Long> ids = new ArrayList<>();
                for (String resourceId : splitId) {
                    if(!NumberUtils.isCreatable(resourceId)){
                        return ERROR_BAD_REQUEST;
                    }
                    ids.add(Long.valueOf(resourceId));
                }
                result = ResourceDAO.getInstance().restoreAll(user, ids);

            } else {
                result = ResourceDAO.getInstance().restore(user, Long.parseLong(id));
            }
            if (result == 0) {
                return ERROR_NOT_FOUND;
            } else if (result == -1) {
                return ERROR_FORBIDDEN;
            } else {
                log.info("restored:{} login:{}",id, s.getUser().getLogin());
                ResourcesChangeEvent wssNotification = Notification.builder().eventType(EventType.RESTORE).userLogin(user.getLogin()).ids(splitId).createWSSNotification();
                EventManager.getInstance().addNotification(wssNotification);
                return Response.ok().build();
            }
        }
    }

    @PUT
    @Path("/shareall/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response enableShareAll(@HeaderParam("sessionid") String sessionId, @PathParam("id") Long id) {
        log.info("enableShareAll:{}",id, Event.SHARE_ALL);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            ShareAllResultDTO result = ResourceDAO.getInstance().shareAll(SessionsPool.find(sessionId).getUser(), id, false);
            if (result.code == 0) {
                return ERROR_NOT_FOUND;
            } else if (result.code == -1) {
                return ERROR_FORBIDDEN;
            }
            String url = "{\"url\":\"https://"+Main.DOMAIN+"/share/"+result.hash+id+"\"}";
            ResourceChangeEvent notification = Notification.builder().eventType(EventType.SHARE_ALL).eventData(url).userLogin(s.getUser().getLogin()).id(id).createResourceChangeNotification();
            EventManager.getInstance().addNotification(notification);
            return Response.ok().entity(url).build();
        }
    }

    @DELETE
    @Path("/shareall/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response disableShareAll(@HeaderParam("sessionid") String sessionId, @PathParam("id") Long id) {
        log.info("disableShareAll:{}",id);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            ShareAllResultDTO result = ResourceDAO.getInstance().shareAll(SessionsPool.find(sessionId).getUser(), id, true);
            if (result.code == 0) {
                return ERROR_NOT_FOUND;
            }
            if (result.code == -1) {
                return ERROR_FORBIDDEN;
            }
            return Response.ok().build();
        }
    }

    @GET
    @Path("/shareall/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getShareAll(@PathParam("id") String id) {
        log.info("getShareAll:{}",id);
        ResourceDTO result = ResourceDAO.getInstance().getShare(id);
        if (result == null) {
            return ERROR_NOT_FOUND;
        } else {
            return Response.ok(ResourceDTO.gson.toJson(result)).build();
        }
    }


    @DELETE
    @Path("{id}")
    public Response delete(@HeaderParam("sessionid") String sessionId, @PathParam("id") String id) {
        log.info("delete:{}",id);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            int result;
            String[] splitedIds = id.split("_");
            if (splitedIds.length > 0) {
                ArrayList<Long> ids = new ArrayList<>();
                for (String i : splitedIds) {
                    ids.add(Long.parseLong(i));
                }
                result = ResourceDAO.getInstance().deleteAll(SessionsPool.find(sessionId).getUser(), ids);
            } else {
                result = ResourceDAO.getInstance().delete(SessionsPool.find(sessionId).getUser(), Long.parseLong(id));
            }
            log.info("delete:{} login:{} result:{}",id, s.getUser().getLogin(), result);
            if (result > 0) {
                ResourcesChangeEvent wssNotification = Notification.builder().eventType(EventType.DELETE).userLogin(s.getUser().getLogin()).ids(splitedIds).createWSSNotification();
                EventManager.getInstance().addNotification(wssNotification);
                return Response.ok(String.valueOf(result)).build();
            } else {
                return ERROR_NOT_FOUND;
            }
        }
    }

    @PUT
    @Path("/tag/{id}")
    public Response setTag(@HeaderParam("sessionid") String sessionId, @PathParam("id") Long id, String newTags) {
        log.info("setTag:{}",newTags,Event.SET_TAG);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            log.info("setTag:{} :{} :{}",s.getUser().getLogin(), newTags, id, Event.SET_TAG);
            int tag = Integer.valueOf(newTags);
            int result = ResourceDAO.getInstance().setTag(SessionsPool.find(sessionId).getUser(), id, tag);
            if (result < 1) {
                return ERROR_NOT_FOUND;
            } else {
                ResourcesChangeEvent wssNotification = Notification.builder().eventType(EventType.SET_TAG).userLogin(s.getUser().getLogin()).id(id).eventData("tag:" + newTags).createWSSNotification();
                EventManager.getInstance().addNotification(wssNotification);
                return Response.ok().build();
            }
        }
    }

    @PUT
    @Path("/tags/{ids}")
    public Response setTag(@HeaderParam("sessionid") String sessionId, @PathParam("ids") String ids, String newTags) {
        log.info("setTag:{}",newTags,Event.SET_TAG);
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            int result = 0;
            String[] splitIds = ids.split("_");
            for (String id : splitIds){
                result += ResourceDAO.getInstance().setTag(SessionsPool.find(sessionId).getUser(), Long.valueOf(id), Integer.valueOf(newTags));
            }
            if (result < 1) {
                return ERROR_NOT_FOUND;
            } else {
                ResourcesChangeEvent wssNotification = Notification.builder()
                        .eventType(EventType.SET_TAG)
                        .userLogin(s.getUser().getLogin())
                        .ids(splitIds)
                        .eventData("tags:" + newTags)
                        .createWSSNotification();
                EventManager.getInstance().addNotification(wssNotification);
                return Response.ok().build();
            }
        }
    }
}