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

package ua.papka24.server.db.scylla.chat;

import com.datastax.driver.core.*;
import ua.papka24.server.db.dto.ChatMessage;
import ua.papka24.server.db.scylla.ScyllaCluster;
import ua.papka24.server.utils.exception.ReceivingDataException;
import ua.papka24.server.utils.exception.SavingDataException;
import ua.papka24.server.Main;

import java.util.*;

public class ChatDAO extends ScyllaCluster {

    public static final int STATUS_CREATE  = 0;
    public static final int STATUS_READ    = 1;
    public static final int STATUS_EDIT    = 2;
    public static final int STATUS_DELETE  = 4;

    private ChatDAO() {
    }

    private static class Singleton {
        private static final ChatDAO HOLDER_INSTANCE = new ChatDAO();
    }

    public static ChatDAO getInstance() {
        return ChatDAO.Singleton.HOLDER_INSTANCE;
    }

    public List<ChatMessage> getMessages(long roomId, Long time) throws ReceivingDataException {
        List<ChatMessage> messages = new ArrayList<>();
        try {
            Session session = getSession();
            if (session != null) {
                BoundStatement bound;
                if(time==null){
                    bound = preparedStatementMap.get("chat_archive.select").bind()
                            .setLong(0, roomId)
                            .setInt(1, Integer.valueOf(Main.property.getProperty("chat_limit","1000")));
                }else{
                    bound = preparedStatementMap.get("chat_archive.select_time").bind()
                            .setLong(0, roomId)
                            .setLong(1, time);
                }
                ResultSetFuture rs = session.executeAsync(bound);
                ResultSet rows = rs.get();
                if (rows != null) {
                    rows.forEach(e -> {
                        ChatMessage message = new ChatMessage(e.getString(1), e.getLong(0), e.getLong(2), e.getString(3));
                        message.setStatus(e.getInt(4));
                        message.r = message.getStatus() != STATUS_CREATE;
                        if(message.getStatus() != STATUS_DELETE) {
                            messages.add(message);
                        }
                    });
                }
            }
        } catch (Exception ex) {
            throw new ReceivingDataException(ex);
        }
        return messages;
    }

    public ChatMessage saveMessage(ChatMessage message) throws SavingDataException {
        try {
            Session session = getSession();
            if (session != null) {
                BoundStatement bound = preparedStatementMap.get("chat_archive.save").bind()
                        .setLong(0, message.roomId)
                        .setString(1, message.login)
                        .setLong(2, message.time)
                        .setInt(3, STATUS_CREATE)
                        .setString(4, message.getText());
                session.executeAsync(bound);
            }
        } catch (Exception ex) {
            throw new SavingDataException(ex);
        }
        return message;
    }

    public void changeStatus(Long roomId, Long time, int status) throws SavingDataException {
        try{
            Session session = getSession();
            if(session!=null){
                BoundStatement bound = preparedStatementMap.get("chat_archive.update_status").bind()
                        .setInt(0, status)
                        .setLong(1, roomId)
                        .setLong(2, time);
                session.executeAsync(bound);
            }
        } catch (Exception ex) {
            throw new SavingDataException(ex);
        }
    }

    public void deleteMessage(Long roomId, Long time) throws SavingDataException {
        try{
            Session session = getSession();
            if(session!=null){
                BoundStatement bound = preparedStatementMap.get("chat_archive.delete_message").bind()
                        .setLong(0, roomId)
                        .setLong(1, time);
                session.executeAsync(bound);
            }
        } catch (Exception ex) {
            throw new SavingDataException(ex);
        }
    }
}
