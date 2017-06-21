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

package ua.papka24.server.security;

import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import ua.papka24.server.Main;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.db.redis.RedisDAOManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class SessionsPool {

    private static long sessionTime;
    private static final boolean apiAccessEnable = Boolean.valueOf(Main.property.getProperty("api_access_enable", "true"));

    static{
        try {
            if (Main.property.getProperty("sessionStorage.enable", "false").equals("true")) {
                sessionTime = 10L;
            } else {
                sessionTime = 1440L;
            }
        }catch (Exception ex){
            sessionTime = 1440L;
            ex.printStackTrace();
        }
    }

    private static final ExpiringMap<String, Session> sessions = ExpiringMap.builder()
            .expirationPolicy(ExpirationPolicy.ACCESSED)
            .expiration(sessionTime, TimeUnit.MINUTES)
            .expirationListener(new SessionsExpirationListener())
            .build();

    private static final Map<String, Set<String>> userSessions = new ConcurrentHashMap<>();

    public static Session openSession(UserDTO user, boolean isRobot) {
        Session newSession = new Session(user, isRobot);
        String newSessionId = newSession.getSessionId();
        sessions.put(newSessionId, newSession);

        saveUserSession(user.getLogin(), newSessionId);

        RedisDAOManager.getInstance().updateUserSession(newSession, isRobot);
        MDC.put("user_login",user.getLogin());
        MDC.put("session", newSessionId);
        return newSession;
    }

    private static void saveUserSession(String userLogin, String newSessionId){
        Set<String> sessionList = userSessions.computeIfAbsent(userLogin, k -> new HashSet<>());
        sessionList.add(newSessionId);
    }

    public static Set<String> getUserSession(String userLogin) {
        Set<String> sessionids = new HashSet<>();
        sessionids.addAll(userSessions.computeIfAbsent(userLogin, k -> new HashSet<>()));
        return sessionids;
    }

    public static void closeSession(String sessionId) {
        if (sessions.containsKey(sessionId)) {
            Session session = sessions.get(sessionId);
            String login = null;
            if(session!=null){
                if(session.getUser()!=null){
                    login = session.getUser().getLogin();
                    if(userSessions.containsKey(login)){
                        Set<String> sessionsList = userSessions.get(login);
                        sessionsList.remove(sessionId);
                    }
                }
                session.invalidate();
                sessions.remove(sessionId);
                RedisDAOManager.getInstance().closeSession(login, sessionId);
            }
        }
    }

    public static int getSize(){
        return sessions.size();
    }

    public static UserDTO getUserDTO(String userLogin) {
        return sessions.values().stream()
                .map(Session::getUser).filter(userDto->userDto!=null && userLogin.equals(userDto.getLogin())).findFirst().orElse(null);
    }

    public static Session find(String sessionId) {
        if (StringUtils.isEmpty(sessionId)){
            return null;
        }
        Session session = sessions.get(sessionId);
        if(session==null){
            session = RedisDAOManager.getInstance().findSession(sessionId);
            if(session!=null){
                sessions.put(sessionId,session);
                saveUserSession(session.getUser().getLogin(), sessionId);
            }
        }
        if(!apiAccessEnable && session!=null && session.isRobot()){
            return null;
        }
        return session;
    }

    public static boolean checkSession(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return false;
        Session session = sessions.get(sessionId);
        return session != null || RedisDAOManager.getInstance().checkSession(sessionId);
    }

    public static Session findWithRight(String sessionId, long...privileges) {
        Session s = find(sessionId);
        if (s != null && s.getUser().getPrivileges().canOr(privileges)){
            return s;
        }
        return null;
    }

    public static void invalidate(String sessionId) {
        if(!sessions.containsKey(sessionId)){
            return;
        }
        Session session = RedisDAOManager.getInstance().findSession(sessionId);
        if(session!=null) {
            sessions.put(sessionId, session);
            saveUserSession(session.getUser().getLogin(), sessionId);
        }else{
            Session localSession = sessions.get(sessionId);
            if(localSession!=null){
                Session reloadedSession = new Session(sessionId,localSession.getUser().getLogin(), localSession.isRobot());
                if(reloadedSession.getUser()!=null) {
                    sessions.put(sessionId, reloadedSession);
                    saveUserSession(reloadedSession.getUser().getLogin(), sessionId);
                }
            }
        }
    }

    public static boolean dropSession(String sessionId){
        Session remove = sessions.remove(sessionId);
        return remove!=null;
    }

    /**
     * ExpiringMap не дает корректно удалить сессии forearch через итератор.
     * (вариант - сбрасывать нафиг сесси на сервере - если что заберет из redis)
     * @param login
     */
    public static void dropUser(String login){
        Set<String> userSess = userSessions.get(login);
        if(userSess!=null){
            userSess.forEach(sessions::remove);
            userSessions.remove(login);
        }
    }

    /**
     * Установка главной сессии пользователя. остальные сесии будут закрыты.
     * @param login
     * @param sessionId
     */
    public static void setPrimeSession(String login, String sessionId) {
        Set<String> userSessions = SessionsPool.userSessions.get(login);
        if(userSessions!=null){
            Set<String> newSessions = new HashSet<>(1);
            newSessions.add(sessionId);
            SessionsPool.userSessions.put(login, newSessions);
            RedisDAOManager redisDaoManager = RedisDAOManager.getInstance();
            userSessions.stream().filter(userSession->!userSession.equals(sessionId)).forEach(userSession->{
                sessions.remove(userSession);
                redisDaoManager.closeSession(login, userSession);
            });
        }
    }

    public static Map<String, List<ActiveSession>> getActiveSessions() {
        return userSessions.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, v->{
            Set<String> sessionsId = v.getValue();
            List<ActiveSession> sess = new ArrayList<>();
            for (String s : sessionsId) {
                sess.add(new ActiveSession(sessions.get(s).getSessionId(), sessions.get(s).isRobot()));
            }
            return sess;
        }));
    }

    public static class ActiveSession{
        private final boolean isRobot;
        private String sessionId;

        public ActiveSession(String sessionId, boolean robot) {
            this.sessionId = sessionId;
            this.isRobot = robot;
        }
    }

    private static class SessionsExpirationListener implements ExpirationListener<String, Session>{

        @Override
        public void expired(String sessionId, Session session) {
            String login = session.getUser().getLogin();
            session.invalidate();
            Set<String> sessions = userSessions.get(login);
            if(sessions!=null){
                sessions.remove(sessionId);
            }
        }
    }
}