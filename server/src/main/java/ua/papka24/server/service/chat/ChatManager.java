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

package ua.papka24.server.service.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.redis.RedisDAOManager;
import ua.papka24.server.service.events.EventManager;
import ua.papka24.server.service.events.main.data.Notification;
import ua.papka24.server.utils.exception.ReceivingDataException;
import ua.papka24.server.db.dto.ChatMessage;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.db.scylla.chat.ChatDAO;
import ua.papka24.server.utils.exception.SavingDataException;

import java.util.List;
import java.util.Objects;

import static ua.papka24.server.db.scylla.chat.ChatDAO.STATUS_DELETE;


public class ChatManager {

    private static final Logger log = LoggerFactory.getLogger(ChatManager.class);

    private ChatManager(){}

    private static class Singleton {
        private static final ChatManager HOLDER_INSTANCE = new ChatManager();
    }

    public static ChatManager getInstance() {
        return ChatManager.Singleton.HOLDER_INSTANCE;
    }

    public List<ChatMessage> getMessages(UserDTO user, long roomId, Long time){
        if(!ResourceDAO.getInstance().checkResourceAccess(user.getLogin(), roomId, null)){
            return null;
        }
        try{
            List<ChatMessage> messages = ChatDAO.getInstance().getMessages(roomId, time);
            setRoomStatusRead(roomId, user.getLogin());
            messages.forEach(e-> {
                if(Objects.equals(STATUS_DELETE, e.getStatus())){
                    e.deleteText();
                }
            });
            return messages;
        }catch (Exception ex){
            log.error("error get chat message, login : {}, resource_id : {}", user.getLogin(), roomId, ex);
        }
        return null;
    }

    public ChatMessage saveMessage(ChatMessage message) throws SavingDataException {
        ChatMessage cm = null;
        if(checkAllowed(message)){
            cm = ChatDAO.getInstance().saveMessage(message);
            Notification.Builder builder = Notification.builder().chatMessage(message);
            EventManager.getInstance().addNotification(builder.createChatNotification());
            EventManager.getInstance().addNotification(builder.convertChatToResourceChange());
            RedisDAOManager.getInstance().newChatMessageAdded(message.roomId, message.login, null);
        }
        return cm;
    }

    public void setRoomStatusRead(Long roomId, String login) throws ReceivingDataException {
        List<ChatMessage> messages = ChatDAO.getInstance().getMessages(roomId, null);
        messages.stream().filter(e->!Objects.equals(e.getStatus(), STATUS_DELETE)).forEach(e->{
            try {
                ChatDAO.getInstance().changeStatus(roomId, e.time, ChatDAO.STATUS_READ);
                RedisDAOManager.getInstance().checkChatMessage(roomId, login);
            }catch (Exception ex){
                log.error("cant change status for some messages", ex);
            }
        });
    }

    private boolean checkAllowed(ChatMessage message){
        return checkAllowed(message.login, message.roomId);
    }

    private boolean checkAllowed(String login, Long roomId){
        return ResourceDAO.getInstance().checkResourceAccess(login, roomId, null);
    }

    public void deleteMessage(String login, Long roomId, Long time) throws SavingDataException {
        //проверка что у автора есть права
        if(checkAllowed(login, roomId)){
            ChatDAO.getInstance().deleteMessage(roomId, time);
        }
    }
}