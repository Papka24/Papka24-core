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
import ua.papka24.server.db.dao.DiffDAO;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.db.scylla.history.HistoryManager;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.service.events.main.data.NotifyResult;
import ua.papka24.server.utils.exception.NotSatisfiedException;
import ua.papka24.server.utils.exception.ScyllaInteractionException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;


public class EventHistoryHandler extends Handler<ResourcesChangeEvent>{

    private static final Logger log = LoggerFactory.getLogger(EventHistoryHandler.class);
    private static final Gson gson = new Gson();

    @Override
    public NotifyResult notify(ResourcesChangeEvent notification) throws NotSatisfiedException, InterruptedException, ExecutionException, IOException {
        List<Long> docsId = notification.getDocID();

        long magicFactor = 0;
        for(Long docId : docsId) {
            Set<String> associatedUsersLogins;
            final String[] eventTypeString = {notification.getEventType()};
            //добавляет по мс для того чтобы сохранить в базу ( если будет групповое событие последняя запись перекроет остальные по времени)
            Date date = new Date(notification.getTime()+(magicFactor++));
            List<DiffDAO.SyncItem> syncItemList = new ArrayList<>();
            EventType eventType = EventType.valueOf(eventTypeString[0]);
            switch (eventType){
                case RENAME:
                case CREATE:
                case RESTORE:
                case SET_TAG:
                case LEAVE_COMPANY:
                case JOIN_COMPANY:{
                    associatedUsersLogins = new HashSet<String>(){{add(notification.getUserLogin());}};
                    break;
                }
                default:{
                    associatedUsersLogins = ResourceDAO.getInstance().getAssociatedUsers(docId);
                    break;
                }
            }
            List<String> sharedDestination;
            if(notification.getShareEmails() != null){
                sharedDestination = Arrays.asList(notification.getShareEmails());
            }else{
                sharedDestination = new ArrayList<>();
            }
            if (eventType == EventType.LEAVE_COMPANY || eventType == EventType.JOIN_COMPANY ||
                    (associatedUsersLogins.isEmpty() && eventType == EventType.DELETE)) {
                DiffDAO.SyncItem syncItem = new DiffDAO.SyncItem();
                syncItem.login = notification.getUserLogin();
                try {
                    String extra = "";
                    boolean saveRes = HistoryManager.getInstance().saveEvent(notification.getUserLogin(), date, docId, eventTypeString[0], extra);
                    boolean fullSaveRes = HistoryManager.getInstance().saveFullEvent(notification.getUserLogin(), date, docId, eventTypeString[0], extra, notification.getUserLogin());
                    if (saveRes && fullSaveRes) {
                        syncItem.date = date;
                    }
                } catch (ScyllaInteractionException see) {
                    log.error("error save history event", see);
                }
                syncItemList.add(syncItem);
            } else {
                associatedUsersLogins.forEach(login -> {
                    DiffDAO.SyncItem syncItem = new DiffDAO.SyncItem();
                    syncItem.login = login;
                    try {
                        ResourceDTO resourceDTO = ResourceDAO.getInstance().getUserResource(login, docId);
                        String extra = null;
                        if (resourceDTO != null) {
                            extra = gson.toJson(resourceDTO);
                        } else {
                            try {
                                if (eventType == EventType.DELETE || eventType == EventType.DELETE_NOT_OWN) {
                                    extra = "";
                                }

                            } catch (Exception ex) {
                                log.warn("error get event type", ex);
                            }
                        }
                        if(eventType == EventType.SHARED && sharedDestination.contains(login)){
                            eventTypeString[0] = EventType.CREATE.name();
                        }
                        boolean saveRes = HistoryManager.getInstance().saveEvent(login, date, docId, eventTypeString[0], extra);
                        boolean fullSaveRes = HistoryManager.getInstance().saveFullEvent(login, date, docId, eventTypeString[0], extra, notification.getUserLogin());
                        if (saveRes && fullSaveRes) {
                            syncItem.date = date;
                        }
                    } catch (ScyllaInteractionException see) {
                        log.error("error save resource", see);
                    }
                    syncItemList.add(syncItem);
                });
            }
            DiffDAO.getInstance().syncTime(syncItemList);
        }
        return NotifyResult.OK();
    }
}
