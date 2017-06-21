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

package ua.papka24.server.service.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dto.SubscribeFullInfoDTO;
import ua.papka24.server.service.events.handler.*;
import ua.papka24.server.service.events.main.EventsQueryProcessor;
import ua.papka24.server.service.events.main.data.Notification;
import ua.papka24.server.db.dao.SubscribeDAO;
import ua.papka24.server.db.dto.ChatMessage;
import ua.papka24.server.db.dto.SubscribeInfoDTO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.service.events.event.EmailEvent;
import ua.papka24.server.service.events.event.ResourceChangeEvent;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.utils.logger.Event;

import java.util.List;


 public class EventManager {

    private static final Logger log = LoggerFactory.getLogger(EventManager.class);

    private EventManager(){
       registerEventHandler(ResourceChangeEvent.class, ResourceChangeHandlerHttp.class);
       registerEventHandler(ResourcesChangeEvent.class, EventHistoryHandler.class);
       registerEventHandler(ResourcesChangeEvent.class, ResourceHistoryLog.class);

       registerEventHandler(ResourcesChangeEvent.class, WssPushHandler.class);
       registerEventHandler(EmailEvent.class, EmailHandler.class);

       registerEventHandler(ChatMessage.class, ChatHandler.class);
    }

    private static class Singleton {
        private static final EventManager HOLDER_INSTANCE = new EventManager();
    }

    public static EventManager getInstance() {
        return EventManager.Singleton.HOLDER_INSTANCE;
    }

    public List<SubscribeDAO.SubscribeResult> addSubscribers(String sid, List<SubscribeInfoDTO> subscribes){
        List<SubscribeDAO.SubscribeResult> subIds = SubscribeDAO.getInstance().saveSubscribersList(sid, subscribes);
        if(subIds.size()>0){
            log.info("save new subscribers:{}:{}",subscribes, subIds, Event.ADD_SUBSCRIBE);
        }
        return subIds;
    }

    /**
     * удаление подписок по запросу - удаляется конкретная подписка которая пришла в запросе.
     * @param sid
     * @param subscribes
     * @return
     */
    public List<SubscribeDAO.SubscribeResult> deleteSubscribers(String sid, List<SubscribeInfoDTO> subscribes) {
        List<SubscribeDAO.SubscribeResult> subIds = SubscribeDAO.getInstance().deleteSubscribers(sid, subscribes);
        if(subIds.size()>0){
            log.info("delete subscribes:{}:{}",subscribes, subIds, Event.DELETE_SUBSCRIBE);
        }
        return subIds;
    }

    boolean registerEventHandler(Class<? extends Notification> notificationClass, Class<? extends Handler> handlerClass) {
        return notificationClass != null && handlerClass != null && EventsQueryProcessor.getInstance().registerEventHandler(notificationClass, handlerClass);
    }

    public boolean addNotification(Notification notification) {
        return EventsQueryProcessor.getInstance().addNotification(notification);
    }

    /**
     * уделение всех оформленных пользователем подписок
     * @param user
     */
    public boolean removeSubscriber(Long companyId, UserDTO user) {
        return SubscribeDAO.getInstance().removeSubscriber(companyId, user);
    }

    /**
     * уделение связки админы - сотрудники
     * @param companyId
     */
    public boolean removeCompanySubscribers(Long companyId){
        return SubscribeDAO.getInstance().removeCompanySubscribers(companyId);
    }

    public List<SubscribeFullInfoDTO> getSubscriptions(UserDTO user){
        return SubscribeDAO.getInstance().getSubscriptions(user);
    }
}