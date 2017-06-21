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

package ua.papka24.server.service.events.handler;

import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.service.events.main.data.NotifyResult;
import ua.papka24.server.Main;
import ua.papka24.server.db.dto.ShareDTO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.utils.websocket.WebSocketServer;
import ua.papka24.server.utils.websocket.data.WebSocketResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


public class WssPushHandler extends Handler<ResourcesChangeEvent> {

    private static final Logger log = LoggerFactory.getLogger(WssPushHandler.class);
    private static final ExpiringMap<String, List<ResourcesChangeEvent>> signEventHolder = ExpiringMap.builder()
            .asyncExpirationListener(new SignExpirationListener()).expirationPolicy(ExpirationPolicy.CREATED)
            .expiration(10, TimeUnit.SECONDS).build();

    @Override
    public NotifyResult notify(ResourcesChangeEvent notification) throws InterruptedException, ExecutionException, IOException {
        try{
            if(Main.property.getProperty("webSocket.enable","false").equals("true")) {
                EventType eventType = EventType.valueOf(notification.getEventType());
                switch (eventType) {
                    case SHARED: {
                        sendShare(notification);
                        break;
                    }
                    case CREATE:{
                        sendCreate(notification);
                        break;
                    }
                    case SET_TAG:
                    case RESTORE:
                    case RENAME:{
                        sendRename(notification);
                        break;
                    }
                    case DELETE_NOT_OWN:
                    case DELETE:{
                        sendDelete(notification);
                        break;
                    }
                    case DELETE_SHARING:
                    case VIEWED: {
                        sendAll(notification);
                        break;
                    }
                    case SIGN:{
                        sendSign(notification);
                        break;
                    }
                    case JOIN_COMPANY:
                    case LEAVE_COMPANY:{
                        sendReset(notification);
                        break;
                    }
                }
            }
        }catch (Exception ex){
            log.error("error while do push notification",ex);
            return NotifyResult.ERROR("error do notification");
        }
        return NotifyResult.OK();
    }

    private void sendReset(ResourcesChangeEvent rci) {
        //docid -> companyId;
        WebSocketServer.sendReset(rci.getEventType(), rci.getEventData(), rci.getCompanyId());
    }

    private void sendRename(ResourcesChangeEvent notifierMsg){
        UserDTO user = UserDTO.load(notifierMsg.getUserLogin());
        if(user!=null){
            List<ResourceDTO> docs = new ArrayList<>();
            for(Long docId : notifierMsg.getDocID()){
                ResourceDTO resource = ResourceDAO.getInstance().getResource(user, docId, ResourceDAO.SUPER_ADMIN);
                if(resource!=null){
                    docs.add(resource);
                }
            }
            notifierMsg.setResourceDTO(docs);
            WebSocketServer.sendMessage(notifierMsg.getUserLogin(), notifierMsg);
        }
    }

    private void sendCreate(ResourcesChangeEvent notifierMsg){
        WebSocketServer.sendMessage(notifierMsg.getUserLogin(), notifierMsg, WebSocketResponse.Method.CREATE);
    }

    private void sendDelete(ResourcesChangeEvent notifierMsg){
        if(notifierMsg.getEventType().equals(EventType.DELETE_NOT_OWN.name())){
            notifierMsg.setEventType(EventType.DELETE.name());
        }
        List<Long> deletedResources = new ArrayList<>();
        List<Long> trashResources = new ArrayList<>();
        UserDTO user = UserDTO.load(notifierMsg.getUserLogin());
        List<Long> resourcesExists = ResourceDAO.getInstance().checkUserResourcesVisible(user, notifierMsg.getDocID());
        for (Long resourceId : notifierMsg.getDocID()) {
            if(resourcesExists.contains(resourceId)){
                trashResources.add(resourceId);
            }else{
                deletedResources.add(resourceId);
            }
        }
        if(!deletedResources.isEmpty()) {
            notifierMsg.setDocID(deletedResources);
            WebSocketServer.sendDeleteMessage(notifierMsg.getUserLogin(), notifierMsg);
            sendShareUsers(notifierMsg, true);
        }
        if(!trashResources.isEmpty()) {
            notifierMsg.setDocID(trashResources);
            sendRename(notifierMsg);
        }
    }

    private void sendShareUsers(ResourcesChangeEvent notifierMsg, boolean ignoreCheck){
        log.info("sendShareUsers : {} : {}", notifierMsg, ignoreCheck);
        for(Long id : notifierMsg.getDocID()) {
            List<ShareDTO> shares = ResourceDAO.getInstance().getShare(notifierMsg.getUserLogin(), id, false, null, ignoreCheck);
            if (shares != null) {
                shares.forEach(share -> {
                    try {
                        UserDTO user = UserDTO.load(share.getUser());
                        if (user != null) {
                            List<ResourceDTO> docs = new ArrayList<>();
                            for(Long docId : notifierMsg.getDocID()) {
                                ResourceDTO dto = ResourceDAO.getInstance().getResource(user, docId, ResourceDAO.SUPER_ADMIN);
                                if(dto!=null){
                                    docs.add(dto);
                                }
                            }
                            notifierMsg.setResourceDTO(docs);
                            WebSocketServer.sendMessage(share.getUser(), notifierMsg);
                        }
                    } catch (Exception ex) {
                        log.error("error send push notification", ex);
                    }
                });
            }
        }
    }

    private void sendAll(ResourcesChangeEvent notifierMsg) {
        sendShareUsers(notifierMsg, false);
        if(EventType.valueOf(notifierMsg.getEventType()) == EventType.DELETE_SHARING){
            log.info("delete sharing : {}", notifierMsg);
            WebSocketServer.sendMessage(notifierMsg.getEventData(), notifierMsg, WebSocketResponse.Method.DELETE);
        }
        //отправка автору
        for(Long id : notifierMsg.getDocID()){
            UserDTO resourceOwner = ResourceDAO.getInstance().getResourceOwner(id);
            if(resourceOwner!=null){
                List<ResourceDTO> dtos = new ArrayList<>();
                for(Long docId : notifierMsg.getDocID()) {
                    ResourceDTO dto = ResourceDAO.getInstance().getResource(resourceOwner, docId, ResourceDAO.SUPER_ADMIN);
                    if(dto!=null){
                        dtos.add(dto);
                    }
                }
                notifierMsg.setResourceDTO(dtos);
                WebSocketServer.sendMessage(resourceOwner.getLogin(), notifierMsg);
            }
        }
    }

    private void sendSign(ResourcesChangeEvent notifierMsg) {
        for(Long id : notifierMsg.getDocID()) {
            List<ShareDTO> shares = ResourceDAO.getInstance().getShare(notifierMsg.getUserLogin(), id, false, null, false);
            if (shares != null) {
                shares.forEach(share -> {
                    try {
                        UserDTO user = UserDTO.load(share.getUser());
                        if (user != null) {
                            List<ResourceDTO> docs = new ArrayList<>();
                            for(Long docId : notifierMsg.getDocID()) {
                                ResourceDTO dto = ResourceDAO.getInstance().getResource(user, docId, ResourceDAO.SUPER_ADMIN);
                                if(dto!=null){
                                    docs.add(dto);
                                }
                            }
                            notifierMsg.setResourceDTO(docs);
                            List<ResourcesChangeEvent> resourcesChangeEvents = signEventHolder.computeIfAbsent(share.getUser(), k -> new ArrayList<>());
                            resourcesChangeEvents.add(notifierMsg);
                        }
                    } catch (Exception ex) {
                        log.error("error send push notification", ex);
                    }
                });
            }
        }
        //отправка автору
        for(Long id : notifierMsg.getDocID()){
            UserDTO resourceOwner = ResourceDAO.getInstance().getResourceOwner(id);
            if(resourceOwner!=null){
                List<ResourceDTO> dtos = new ArrayList<>();
                for(Long docId : notifierMsg.getDocID()) {
                    ResourceDTO dto = ResourceDAO.getInstance().getResource(resourceOwner, docId, ResourceDAO.SUPER_ADMIN);
                    if(dto!=null){
                        dtos.add(dto);
                    }
                }
                notifierMsg.setResourceDTO(dtos);
                List<ResourcesChangeEvent> resourcesChangeEvents = signEventHolder.computeIfAbsent(resourceOwner.getLogin(), k -> new ArrayList<>());
                resourcesChangeEvents.add(notifierMsg);
            }
        }
    }

    private void sendShare(ResourcesChangeEvent notifierMsg){
        String[] recipients = notifierMsg.getShareEmails();
        if(recipients!=null) {
            for (String recipient : recipients) {
                UserDTO user = UserDTO.load(recipient.toLowerCase());
                if(user!=null) {
                    List<ResourceDTO> resources = new ArrayList<>();
                    for(Long id : notifierMsg.getDocID()) {
                        ResourceDTO dto = ResourceDAO.getInstance().getResource(user, id, ResourceDAO.SUPER_ADMIN);
                        if(dto!=null){
                            resources.add(dto);
                        }
                    }
                    notifierMsg.setResourceDTO(resources);
                    WebSocketServer.sendMessage(recipient.toLowerCase(), notifierMsg, WebSocketResponse.Method.CREATE);
                }
            }
        }
        //автору для нескольких вкладок
        String userLogin = notifierMsg.getUserLogin();
        UserDTO user = UserDTO.load(userLogin);
        if(user!=null) {
            List<ResourceDTO> resources = new ArrayList<>();
            for(Long id : notifierMsg.getDocID()) {
                ResourceDTO dto = ResourceDAO.getInstance().getResource(user, id, ResourceDAO.SUPER_ADMIN);
                if(dto!=null){
                    resources.add(dto);
                }
            }
            notifierMsg.setResourceDTO(resources);
            WebSocketServer.sendMessage(userLogin, notifierMsg);
        }
    }

    private static class SignExpirationListener implements ExpirationListener<String, List<ResourcesChangeEvent>>{

        @Override
        public void expired(String userLogin, List<ResourcesChangeEvent> signEvents) {
            ResourcesChangeEvent sendEvent = new ResourcesChangeEvent();
            sendEvent.setTime(0L);
            signEvents.forEach(e->{
                sendEvent.setUserLogin(userLogin);
                List<Long> ids = sendEvent.getDocID();
                if(ids == null){
                    ids = new ArrayList<>();
                    sendEvent.setDocID(ids);
                }
                ids.addAll(e.getDocID());
                List<ResourceDTO> resourceDTO = sendEvent.getResourceDTO();
                if(resourceDTO==null){
                    resourceDTO = new ArrayList<>();
                    sendEvent.setResourceDTO(resourceDTO);
                }
                resourceDTO.addAll(e.getResourceDTO());
                if(sendEvent.getTime() < e.getTime()){
                    sendEvent.setTime(e.getTime());
                }
            });
            WebSocketServer.sendMessage(userLogin, sendEvent);
        }
    }
}