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
import ua.papka24.server.db.dto.ChatMessage;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.utils.exception.SavingDataException;

import java.util.List;


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
        return null;
    }

    public ChatMessage saveMessage(ChatMessage message) throws SavingDataException {
        ChatMessage cm = null;
        if(checkAllowed(message)){
            Notification.Builder builder = Notification.builder().chatMessage(message);
            EventManager.getInstance().addNotification(builder.createChatNotification());
            EventManager.getInstance().addNotification(builder.convertChatToResourceChange());
            RedisDAOManager.getInstance().newChatMessageAdded(message.roomId, message.login, null);
        }
        return cm;
    }

    public void setRoomStatusRead(Long roomId, String login) {
    }

    private boolean checkAllowed(ChatMessage message){
        return checkAllowed(message.login, message.roomId);
    }

    private boolean checkAllowed(String login, Long roomId){
        return ResourceDAO.getInstance().checkResourceAccess(login, roomId, null);
    }

    public void deleteMessage(String login, Long roomId, Long time) {
        //проверка что у автора есть права
    }
}