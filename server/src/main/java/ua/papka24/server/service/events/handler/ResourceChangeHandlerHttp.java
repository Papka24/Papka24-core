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

import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dao.SubscribeDAO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.service.events.event.ResourceChangeEvent;
import ua.papka24.server.service.events.handler.data.HttpMessage;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.service.events.main.data.NotifyResult;
import ua.papka24.server.db.dto.SubscribeInfoDTO;
import ua.papka24.server.db.dto.SubscribeInfoDTO.SubscribeType;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;


public class ResourceChangeHandlerHttp extends HttpPostHandler<ResourceChangeEvent> {

    private static final Logger log = LoggerFactory.getLogger(ResourceChangeHandlerHttp.class);
    private Queue<HttpMessage> httpMessages = new ConcurrentLinkedQueue<>();
    private Map<String,String> headers = new HashMap<>();

    private Map<String, String> getHeaders() {
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @Override
    public Queue<HttpMessage> getHttpMessages() {
        return httpMessages;
    }

    @Override
    public NotifyResult notify(ResourceChangeEvent notification) throws InterruptedException, ExecutionException, IOException {
        ResourceChangeEvent resourceChangeInfo = notification;
        if(resourceChangeInfo.getDocID()==null){
            return new NotifyResult(NotifyResult.SKIPED,"skiped. doc id is null");
        }
        String eventTypeStr = resourceChangeInfo.getEventType();
        EventType eventType = null;
        try{
            eventType = EventType.valueOf(eventTypeStr);
        }catch (IllegalArgumentException pe){
            log.warn("unknown event:",pe);
        }
        if(eventType==null){
            return NotifyResult.ERROR("unknown event type:"+ eventTypeStr);
        }
        if(eventType==EventType.LEAVE_COMPANY || eventType==EventType.JOIN_COMPANY){
            return new NotifyResult(NotifyResult.SKIPED,"not my event type:"+eventTypeStr);
        }
        String[] extraData = resourceChangeInfo.getExtraData();
        resourceChangeInfo = resourceChangeInfo.copyWithoutExtra();
        List<SubscribeInfoDTO> notifierList = new ArrayList<>();
        notifierList.addAll(SubscribeDAO.getInstance().getSubscribersList(SubscribeType.OUT, String.valueOf(resourceChangeInfo.getDocID()),resourceChangeInfo.getUserLogin()));

        List<SubscribeInfoDTO> subscribersList = SubscribeDAO.getInstance().getSubscribersList(SubscribeType.IN, extraData);
        notifierList.addAll(subscribersList);

        List<ResourceDTO> resourceDTOs = ResourceDAO.getInstance().search(Collections.singletonList(resourceChangeInfo.getDocID()));
        for(ResourceDTO resourceDTO : resourceDTOs){
            Long companyId = resourceDTO.getCompanyId();
            if(companyId!=null) {
                notifierList.addAll(SubscribeDAO.getInstance().getSubscribersList(SubscribeType.GROUP, String.valueOf(companyId)));
            }
        }

        ResourceChangeEvent finalResourceChangeInfo = resourceChangeInfo;
        final boolean[] bl = {true};
        notifierList.stream().filter(o->o.getEventTypes()==null || o.getEventTypes().length==0 || Arrays.asList(o.getEventTypes()).contains(eventTypeStr)).forEach(o->{
            try {
                HttpMessage httpMessage = new HttpMessage();
                httpMessage.setBody(new Gson().toJson(finalResourceChangeInfo).getBytes("UTF8"));
                httpMessage.setHeaders(getHeaders());
                httpMessage.setUrl(o.getUrl());
                httpMessages.add(httpMessage);
                log.info("Event:{} Request:{} ResourceId:{}", eventTypeStr, httpMessage, finalResourceChangeInfo.getDocID());
            }catch (Exception ex){
               log.warn("some error while create http message:{}",ex);
                bl[0] = false;
            }
        });

        if(bl[0]){
            return super.notify(notification);
        }else{
            return NotifyResult.ERROR("some error while create http message");
        }
    }
}