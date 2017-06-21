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
import ua.papka24.server.service.events.main.data.NotifyResult;
import ua.papka24.server.api.helper.EmailHelper;
import ua.papka24.server.db.dao.SpamDAO;
import ua.papka24.server.service.events.event.EmailEvent;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.utils.exception.NotSatisfiedException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;


public class EmailHandler extends Handler<EmailEvent> {

    private static final Logger log = LoggerFactory.getLogger(EmailHandler.class);
    private static final CollectEmailExpirationListener collectEmailExpirationListener = new CollectEmailExpirationListener();
    private static final ExpiringMap<String,Map<EventType,List<EmailEvent>>> holder =
            ExpiringMap.builder().expirationPolicy(ExpirationPolicy.ACCESSED)
                    .expirationListener(collectEmailExpirationListener).expiration(45, TimeUnit.SECONDS).build();
    private static final Lock lock = new ReentrantLock();

    @Override
    public NotifyResult notify(EmailEvent notification) throws NotSatisfiedException, InterruptedException, ExecutionException, IOException {
        String login = notification.getToEmail();
        Map<EventType,List<EmailEvent>> emailEvents = holder.get(login);
        if(!ResourceDAO.getInstance().checkResourceAccess(login, notification.getDocId(), null)){
            return NotifyResult.DAD();
        }
        if (emailEvents == null) {
            lock.lock();
            try{
                emailEvents = holder.computeIfAbsent(login, k -> new HashMap<>());
            }finally {
                lock.unlock();
            }
        }
        EventType eventType = notification.getEventType();
        List<EmailEvent> emailEventsByType = emailEvents.get(eventType);
        if(emailEventsByType==null){
           lock.lock();
            try{
                emailEventsByType = emailEvents.computeIfAbsent(eventType, k -> new ArrayList<>());
            }finally {
                lock.unlock();
            }
        }
        emailEventsByType.add(notification);
        return NotifyResult.OK();
    }

    private static class CollectEmailExpirationListener implements ExpirationListener<String,Map<EventType,List<EmailEvent>>>{

        @Override
        public void expired(String key, Map<EventType, List<EmailEvent>> value) {
            ExecutorService pool = Executors.newFixedThreadPool(value.size());
            value.keySet().forEach(e-> pool.submit(new Collector(key, e, value.get(e))));
        }
    }

    private static class Collector implements Runnable {

        private final String login;
        private final EventType eventType;
        private final List<EmailEvent> emailEvents;

        Collector(String login, EventType eventType, List<EmailEvent> emailEvents) {
            this.login = login;
            this.eventType = eventType;
            this.emailEvents = emailEvents;
        }

        @Override
        public void run() {
            try {
                EmailHelper.Type type = EmailHelper.Type.valueOf(eventType.name());
                if (SpamDAO.getInstance().isEmailAllowed(login, type.type)) {
                    if (emailEvents.size() == 1) {
                        EmailEvent ee = emailEvents.get(0);
                        if(eventType==EventType.SHARED) {
                            EmailHelper.sendShareEmail(ee.getUser(), ee.getToEmail(), ee.getDocId(), ee.getDocName(), ee.getAuthorName(), ee.isSendInvite(), ee.getComment(), ee.getTemplate());
                        }else if(eventType==EventType.SIGN){
                            EmailHelper.sendSignEmail(ee.getUser(),ee.getToEmail(),ee.getDocName(),ee.getDocId(),ee.getAuthorName());
                        }
                    } else {
                        if(eventType==EventType.SHARED) {
                            List<EmailEvent> sharingByTemplate = emailEvents.stream().filter(e -> e.getTemplate() != null).collect(Collectors.toList());
                            List<EmailEvent> sharingWithoutTemplate = emailEvents.stream().filter(e->e.getTemplate()==null).collect(Collectors.toList());
                            sharingByTemplate.forEach(ee-> EmailHelper.sendShareEmail(ee.getUser(), ee.getToEmail(), ee.getDocId(), ee.getDocName(), ee.getAuthorName(), ee.isSendInvite(), ee.getComment(), ee.getTemplate()));
                            if(sharingWithoutTemplate.size()==1){
                                EmailEvent ee = emailEvents.get(0);
                                EmailHelper.sendShareEmail(ee.getUser(), ee.getToEmail(), ee.getDocId(), ee.getDocName(), ee.getAuthorName(), ee.isSendInvite(), ee.getComment(), ee.getTemplate());
                            }else{
                                EmailHelper.sendMassShareEmail(login, sharingWithoutTemplate);
                            }
                        }else if(eventType==EventType.SIGN){
                            EmailHelper.sendMassSignEmail(login, emailEvents);
                        }
                    }
                }
            }catch (Exception ex){
                log.error("error prepare mails",ex);
            }
        }
    }
}