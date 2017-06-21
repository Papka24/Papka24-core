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

import com.google.gson.Gson;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisPubSub;
import ua.papka24.server.api.Company;
import ua.papka24.server.api.DTO.GroupInviteDTO;
import ua.papka24.server.db.redis.system.SystemOperation;
import ua.papka24.server.security.SessionsPool;

import java.util.Set;

class SessionChangeListener extends JedisPubSub {

    private static final Gson gson = new Gson();

    @Override
    public void onMessage(String channel, String message) {
        switch (channel){
            case RedisDAO.CHANGE_CHANEL:{
                try {
                    Set<String> userSessions = RedisDAO.getInstance().getUserSessions(message);
                    if(userSessions!=null){
                        userSessions.forEach(SessionsPool::invalidate);
                    }
                }catch (Exception ex){
                    LoggerFactory.getLogger(SessionChangeListener.class).warn("error invalidate session:{}",ex);
                }
                break;
            }
            case RedisDAO.INVITE_CHANEL:{
                try{
                    GroupInviteDTO invite = new Gson().fromJson(message,GroupInviteDTO.class);
                    Company.invites.put(invite,null);
                }catch (Exception ex){
                    LoggerFactory.getLogger(SessionChangeListener.class).warn("error invalidate invite:{}",ex);
                }
                break;
            }
            case RedisDAO.SYSTEM_CHANEL:{
                try{
                    SystemOperation systemOperation = gson.fromJson(message, SystemOperation.class);
                    if(systemOperation.code == SystemOperation.CLOSE_SESSIONS) {
                        SessionsPool.dropUser(systemOperation.login);
                    }
                }catch (Exception ex){
                    LoggerFactory.getLogger(SessionChangeListener.class).warn("error process operation:{}",ex);
                }
                break;
            }
        }
    }
}