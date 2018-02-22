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
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.db.scylla.Analytics;
import ua.papka24.server.security.Session;
import ua.papka24.server.security.SessionsPool;
import ua.papka24.server.service.events.EventManager;
import ua.papka24.server.service.events.event.ResourceChangeEvent;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.service.events.main.data.Notification;
import ua.papka24.server.utils.logger.Event;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;


@Path("sign")
public class Sign extends REST {

    private static final Logger log = LoggerFactory.getLogger("sign");
    public static TypeAdapter<String[]> gson = new Gson().getAdapter(String[].class);
    private static final Gson mainGson = new Gson();

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@HeaderParam("sessionid") String sessionId, @HeaderParam("employee") String employee, @PathParam("id") Long id) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            List<String> signs;
            UserDTO user = s.getUser();
            signs = ResourceDAO.getInstance().getSigns(user.getLogin(), id, user);
            if (signs == null) {
                return ERROR_FORBIDDEN;
            } else {
                return Response.ok(mainGson.toJson(signs, new TypeToken<List<String>>() {
                }.getType())).build();
            }
        }
    }

    @POST
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response post(@HeaderParam("v") String frontVersion, @HeaderParam("sessionid") String sessionId, @PathParam("id") Long id, String rawSign) {
        Session s = SessionsPool.find(sessionId);
        if (s == null) {
            return ERROR_FORBIDDEN;
        } else {
            if (rawSign == null || rawSign.length() == 0) {
                return ERROR_BAD_REQUEST;
            }
            if (frontVersion == null) {
                frontVersion = "API";
            }
            HashMap<Long, ArrayList<String>> document = new HashMap<>();
            HashMap<Long, ArrayList<String>> results = new HashMap<>();
            // Multi sign document logic, when id = 0
            if (id == 0) {
                document = mainGson.fromJson(rawSign, new TypeToken<HashMap<Long, ArrayList<String>>>() {
                }.getType());
            } else {
                ArrayList<String> newSigns = new ArrayList<>();
                if (rawSign.indexOf('[') == 0) {
                    newSigns = mainGson.fromJson(rawSign, new TypeToken<List<String>>() {
                    }.getType());
                } else {
                    newSigns.add(rawSign);
                }
                document.put(id, newSigns);
            }
            for (Long docId : document.keySet()) {
                ArrayList<String> resultSigns = ResourceDAO.getInstance().addSign(s.getUser(), docId, document.get(docId));
                if (resultSigns != null && resultSigns.size()>0) {
                    log.info("request to add {} sign:{}", resultSigns.size(), docId, Event.ADD_SIGN);
                    ArrayList<String> notifierSigns = new ArrayList<>();
                    for (String sign : resultSigns) {
                        if (document.get(docId).contains(sign)) {
                            notifierSigns.add(sign);
                        }
                    }
                    if (notifierSigns.size() > 0) {
                        Analytics.getInstance().saveEvent(Analytics.Event.sign, new Date(), s.getUser().getLogin(), frontVersion, "successful:" + notifierSigns.size());
                        ResourceChangeEvent noti = Notification.builder().eventType(EventType.SIGN).eventData(mainGson.toJson(notifierSigns, new TypeToken<List<String>>() {
                        }.getType())).userLogin(s.getUser().getLogin()).id(docId).createResourceChangeNotification();
                        EventManager.getInstance().addNotification(noti);
                        ResourcesChangeEvent wssNotification = Notification.builder().eventType(EventType.SIGN).userLogin(s.getUser().getLogin()).id(docId).createWSSNotification();
                        EventManager.getInstance().addNotification(wssNotification);
                    } else {
                        Analytics.getInstance().saveEvent(Analytics.Event.sign, new Date(), s.getUser().getLogin(), frontVersion, "unsuccessful");
                    }
                    log.info("sign added:{}", docId, Event.ADD_SIGN);
                    results.put(docId, resultSigns);
                }
            }
            if (results.size() == 0) {
                return ERROR_BAD_REQUEST;
            } else if (results.size() == 1) {
                return Response.ok(mainGson.toJson(results.get(results.keySet().iterator().next()), new TypeToken<List<String>>() {
                }.getType())).build();
            } else {
                return Response.ok(mainGson.toJson(results, new TypeToken<HashMap<Long, ArrayList<String>>>() {
                }.getType())).build();
            }
        }
    }

}