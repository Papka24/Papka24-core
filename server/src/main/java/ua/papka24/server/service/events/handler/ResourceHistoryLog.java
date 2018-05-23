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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.service.events.main.EventType;
import ua.papka24.server.service.events.main.data.NotifyResult;
import ua.papka24.server.utils.exception.NotSatisfiedException;
import ua.papka24.server.utils.exception.ScyllaInteractionException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class ResourceHistoryLog extends Handler<ResourcesChangeEvent>{

    private static final Logger log = LoggerFactory.getLogger(ResourceHistoryLog.class);

    @Override
    public NotifyResult notify(final ResourcesChangeEvent notification) throws NotSatisfiedException, InterruptedException, ExecutionException, IOException {
        try{
            if(Main.property.getProperty("webSocket.enable","false").equals("true")) {
                EventType eventType = EventType.valueOf(notification.getEventType());
                String message = ""+ notification.getEventData();
                switch (eventType) {
                    case SHARED: {
                        message = Arrays.toString(notification.getShareEmails());
                        break;
                    }
//                    case CREATE:{
//                        break;
//                    }
//                    case SET_TAG:
//                    case RESTORE:
//                    case RENAME:{
//                        break;
//                    }
//                    case DELETE_NOT_OWN:
//                    case DELETE:{
//                        break;
//                    }
//                    case DELETE_SHARING:
//                    case VIEWED: {
//                        break;
//                    }
//                    case SIGN:{
//                        break;
//                    }
//                    case JOIN_COMPANY:
//                    case LEAVE_COMPANY:{
//                        break;
//                    }
                }
                List<Long> resourceIds = notification.getDocID();
                if (resourceIds != null) {
                    for (Long resourceId : resourceIds) {
                        saveResourceHistory(resourceId, eventType.name(), notification.getUserLogin(), message);
                    }
                }
            }
        }catch (Exception ex){
            log.error("error while save resource history",ex);
            return NotifyResult.ERROR("error do notification");
        }
        return NotifyResult.OK();
    }

    private void saveResourceHistory(long resourceId, String event, String login, String message){

    }
}
