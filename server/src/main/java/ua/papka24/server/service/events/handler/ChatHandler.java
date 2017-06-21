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
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dto.ChatMessage;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.service.events.main.data.NotifyResult;
import ua.papka24.server.utils.exception.NotSatisfiedException;
import ua.papka24.server.utils.websocket.WebSocketServer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;


public class ChatHandler extends Handler<ChatMessage> {

    private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);
    private static final Gson gson = new Gson();

    @Override
    public NotifyResult notify(ChatMessage notification) throws NotSatisfiedException, InterruptedException, ExecutionException, IOException {
        if(notification !=null){
            try{
                //получение списка пользователей имеющих доступ к ресурсу
                ResourceDTO resourceDTO = ResourceDAO.getInstance().get(notification.roomId);
                Set<String> logins = new HashSet<>();
                logins.add(resourceDTO.getAuthor());
                logins.addAll(resourceDTO.getShares().keySet());
                Holder holder = new Holder();
                holder.method = "chatMessage";
                holder.data = Collections.singletonList(notification);
                String message = gson.toJson(holder);
                for(String login : logins) {
                    WebSocketServer.sendMessage(login, message);
                }
            }catch (Exception ex){
                log.error("error process event ", ex);
            }
        }
        return NotifyResult.OK();
    }

    public static class Holder{
        public String method;
        public List<ChatMessage> data;
    }
}
