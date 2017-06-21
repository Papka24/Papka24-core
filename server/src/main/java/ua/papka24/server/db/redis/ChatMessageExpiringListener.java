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

package ua.papka24.server.db.redis;

import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPubSub;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.api.helper.EmailHelper;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * для того чтобы редис выдавал оповещения по протыханию записи
 * необходимо добавить параметр x в настройках сервера или через клиента
 * пример redis-cli config set notify-keyspace-events gEx
 *
 */
public class ChatMessageExpiringListener extends JedisPubSub {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageExpiringListener.class);
    private static final ExpiringMap<String, List<Long>> notificationHarvester = ExpiringMap.builder()
            .expiration(5, TimeUnit.MINUTES).expirationPolicy(ExpirationPolicy.CREATED)
            .asyncExpirationListener(new NotificationExpirationListener()).build();

    @Override
    public void onPMessage(String pattern, String channel, String message) {
        try {
            if (message != null && Pattern.matches("roomid.*", message)) {
                Calendar now = Calendar.getInstance();
                int hoursOfDay = now.get(Calendar.HOUR_OF_DAY);
                long roomId = Long.valueOf(message.substring(message.indexOf(":") + 1));
                String lastRoomAuthor = RedisDAOManager.getInstance().getLastRoomAuthor(roomId);
                if (hoursOfDay >= 9 && hoursOfDay <= 18) {
                    Set<String> associatedUsers = ResourceDAO.getInstance().getAssociatedUsers(roomId);
                    associatedUsers.stream().filter(e -> !e.equals(lastRoomAuthor)).forEach(e -> {
                        List<Long> roomIds = notificationHarvester.computeIfAbsent(e, k -> new ArrayList<>());
                        if (!roomIds.contains(roomId)) {
                            roomIds.add(roomId);
                        }
                    });
                    RedisDAO.getInstance().deleteChatMessageInfo(roomId);
                } else {
                    RedisDAOManager.getInstance().newChatMessageAdded(roomId, lastRoomAuthor, null);
                }
            }
        } catch (Exception ex) {
            log.error("error process expired redis message", ex);
        }
    }

    private static class NotificationExpirationListener implements ExpirationListener<String, List<Long>> {

        @Override
        public void expired(String userLogin, List<Long> roomsId) {
            log.info("NOW WE SEND EMAIL to {} : {} ", userLogin, roomsId);
            try {
                Map<String, Long> resourcesInfo = roomsId.stream().distinct().collect(
                        Collectors.toMap(resourceId -> ResourceDAO.getInstance().get(resourceId).getName(), resourceId -> resourceId, (key1, key2)-> key1));
                EmailHelper.sendNewChatMessageEmail(userLogin, resourcesInfo);
            } catch (Exception ex) {
                log.error("error send new chat message email", ex);
            }
        }
    }
}
