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
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import ua.papka24.server.api.DTO.GroupInviteDTO;
import ua.papka24.server.db.redis.model.LoginRedisInfo;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.ExternalSignRequest;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.db.redis.email.EmailQueryRedisManager;
import ua.papka24.server.db.redis.model.RedisSession;
import ua.papka24.server.db.redis.system.SystemOperation;
import ua.papka24.server.security.Session;

import java.util.*;

class RedisDAO extends RedisCluster implements RedisDAOManager {

    private static final String USER_LOGIN  = "user_login";
    private static final String CREATE_TIME = "create_time";
    private static final String STATUS      = "status";
    static final String CHANGE_CHANEL = "change_sessions";
    static final String INVITE_CHANEL = "invite_sessions";
    static final String SYSTEM_CHANEL = "system_chanel";
    private static final String SECRET = "secret";
    private static final String LOGIN_ATTEMPT = "login_attempt";
    static final String SESSION_LIST_MARKER = "#";
    private static final String LOGIN_ATTEMPT_MARKER = "##";
    private static final String EGRPOU_ATTEMPT_MARKER = "EGRPC_";
    private static final String CHAT_ROOM_ID = "roomid:";
    private static final String IS_ROBOT = "isRobot";
    private Gson gson = new Gson();

    private RedisDAO(){
        new Thread(() -> {
            try {
                SessionChangeListener listener = new SessionChangeListener();
                getConnection().subscribe(listener, CHANGE_CHANEL, INVITE_CHANEL, SYSTEM_CHANEL);
            }catch (Exception ex){
                log.error("could not start session change listener",ex);
            }
        }).start();
        if( Main.property.getProperty("emailServer.enable", "true").equals("true")) {
            new Thread(() -> {
                try {
                    ChatMessageExpiringListener chatMessageExpiringListener = new ChatMessageExpiringListener();
                    getConnection().psubscribe(chatMessageExpiringListener, "__keyevent*__:expired*");
                } catch (Exception ex) {
                    log.error("could not start expired listener", ex);
                }
            }).start();
        }
    }

    private static class Singleton {
        private static final RedisDAOManager HOLDER_INSTANCE = new RedisDAO();
    }

    public static RedisDAOManager getInstance() {
        return RedisDAO.Singleton.HOLDER_INSTANCE;
    }

    @Override
    public void updateUserSession(Session session, boolean isBot) {
        try(Jedis con = getConnection()){
           updateUserSession(con,session, isBot);
        }catch (Exception ex){
            log.error("error update user session",ex);
        }
    }

    private void updateUserSession(Jedis con, Session session, boolean isBot){
        int sessionLifetime;
        if(isBot){
            sessionLifetime = BOT_SESSION_LIFETIME;
        }else{
            sessionLifetime = SESSION_LIFETIME;
        }
        String sessionId = session.getSessionId();
        String login = session.getUser().getLogin();
        //session
        con.hset(sessionId, USER_LOGIN, login);
        con.hset(sessionId, CREATE_TIME,String.valueOf(new Date().getTime()));
        con.hset(sessionId, STATUS, SESSION_CREATED);
        con.hset(sessionId, IS_ROBOT, String.valueOf(isBot));
        con.expire(sessionId,sessionLifetime);
        //user
        con.set(login, gson.toJson(session.getUser()));
        con.expire(login, sessionLifetime);
        //user session list
        con.sadd(SESSION_LIST_MARKER+login,sessionId);
        con.expire(SESSION_LIST_MARKER+login, sessionLifetime);
    }


    private void updateUserInfo(Jedis con, UserDTO user){
        String login = user.getLogin();
        con.set(login,gson.toJson(user));
        con.expire(login,SESSION_LIFETIME);
    }

    private void updateSessionExpire(Jedis con, String key, boolean isRobot){
        int sessionLifetime;
        if(isRobot){
            sessionLifetime = BOT_SESSION_LIFETIME;
        }else{
            sessionLifetime = SESSION_LIFETIME;
        }
        con.expire(key, sessionLifetime);
    }

    @Override
    public void closeSession(String login, String sessionId) {
        try(Jedis con = getConnection()){
            con.hset(sessionId,STATUS, SESSION_CLOSED);
            con.hdel(sessionId, USER_LOGIN, CREATE_TIME, STATUS);
            if(login!=null) {
                con.srem(SESSION_LIST_MARKER + login, sessionId);
            }
        }catch (Exception ex){
            log.error("error close user session",ex);
        }
    }

    @Override
    public Session findSession(String sessionId) {
        try(Jedis con = getConnection()){
            List<String> sessionInfo = con.hmget(sessionId,USER_LOGIN,STATUS, IS_ROBOT);
            String userLogin = sessionInfo.get(0);
            String status = sessionInfo.get(1);
            Boolean isRobot = Boolean.valueOf(sessionInfo.get(2));
            if(userLogin!=null && status!=null){
                if(!status.equals(SESSION_CLOSED)){
                    String userJson = con.get(userLogin);
                    if(userJson!=null && !userJson.isEmpty()) {
                        UserDTO user = null;
                        try {
                            user = gson.fromJson(userJson, UserDTO.class);
                            if(user.getCompanyId()!=null && user.getCompanyId()==0){
                                user.setCompanyId(null);
                            }
                        } catch (Exception ex) {
                            log.warn("cannot get user object from redis:{}", ex);
                        }
                        if (user != null) {
                            updateSessionExpire(con, sessionId, isRobot);
                            return new Session(sessionId, user, isRobot);
                        } else {
                            updateSessionExpire(con, sessionId, isRobot);
                            return new Session(sessionId, userLogin, isRobot);
                        }
                    }else {
                        updateSessionExpire(con, sessionId, isRobot);
                        return new Session(sessionId, userLogin, isRobot);
                    }
                }
            }
        }catch (Exception ex){
            log.error("error find user session",ex);
        }
        return null;
    }

    @Override
    public boolean checkSession(String sessionId) {
        try(Jedis con = getConnection()){
            List<String> list = con.hmget(sessionId,USER_LOGIN);
            log.info("Result: { }", list.get(0));
            return con.hmget(sessionId,USER_LOGIN).get(0) != null;
        }catch (Exception ex){
            log.info("error connection to db");
            return false;
        }
    }

    @Override
    public void saveRegResInfo(String secret, String data) {
        try(Jedis con = getConnection()){
            con.set(secret,data);
            con.expire(secret,REGRES_LIFETIME);
        }catch (Exception ex){
            log.error("error save reg/res info",ex);
        }
    }

    @Override
    public String getRegResInfo(String secret) {
        try(Jedis con = getConnection()){
            return con.get(secret);
        }catch (Exception ex){
            log.error("error get reg/res info",ex);
        }
        return null;
    }

    /**
     *  для перегрузки данных по сессии. не приводит к обновлению данных из базу
     * @param session
     */
    @Override
    public void markChanged(Session session) {
        try(Jedis con = getConnection()){
            updateUserSession(con, session, session.isRobot());
            con.publish(CHANGE_CHANEL, session.getUser().getLogin());
        }catch(Exception ex){
            log.error("error markChanged info",ex);
            //
        }
    }

    /**
     * для перегрузки данных по пользователю. приводит к обновлению данных из базы
     * @param userLogin
     */
    @Override
    public void markChanged(String userLogin) {
        try(Jedis con = getConnection()){
            UserDTO user = UserDTO.load(userLogin);
            if(user!=null){
                updateUserInfo(con, user);
            }
            con.publish(CHANGE_CHANEL, userLogin);
        }catch(Exception ex){
            log.error("error markChanged info",ex);
        }
    }

    @Override
    public Set<String> getUserSessions(String userLogin){
        try(Jedis con = getConnection()){
            return con.smembers(SESSION_LIST_MARKER+userLogin);
        }catch(Exception ex){
            log.error("error getUserSessionsList info",ex);
        }
        return null;
    }

    @Override
    public void saveCompanyInvite(GroupInviteDTO groupInvite) {
        try(Jedis con = getConnection()){
            String s = gson.toJson(groupInvite);
            con.sadd(SECRET, s);
            con.expire(SECRET, SECRET_LIFETIME);
            con.publish(INVITE_CHANEL,s);
        }catch(Exception ex){
            log.error("error saveCompanyInvite",ex);
        }
    }

    @Override
    public boolean companyInviteExists(GroupInviteDTO groupInvite) {
        try(Jedis con = getConnection()){
            return con.sismember(SECRET, gson.toJson(groupInvite));
        }catch(Exception ex){
            log.error("error searchCompanyInvite",ex);
        }
        return false;
    }

    @Override
    public void addCloudSignCheck(String docIdentifier, String encodeString) {
        try(Jedis con = getConnection()){
            con.set(docIdentifier,encodeString);
            con.expire(docIdentifier,CLOUD_LIFETIME);
        }catch(Exception ex){
            log.error("error addCloudSignCheck",ex);
        }
    }

    @Override
    public String getCloudSign(String cloudId) {
        try(Jedis con = getConnection()){
            return con.get(cloudId);
        }catch(Exception ex){
            log.error("error getCloudSign",ex);
        }
        return null;
    }

    @Override
    public void deleteCloudSign(String cloudId) {
        try(Jedis con = getConnection()){
            con.del(cloudId);
        }catch(Exception ex){
            log.error("error deleteCloudSign",ex);
        }
    }

    @Override
    public void invalidateAllUsersSessions(){
        try{
            getSessions().stream().map(e->e.login).forEach(this::markChanged);
        }catch(Exception ex){
            log.error("error invalidateAllUsersSessions",ex);
        }
    }

    @Override
    public List<LoginRedisInfo> getSessions(){
        List<LoginRedisInfo> res = new ArrayList<>();
        try(Jedis con = getConnection()){
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanResult<String> scan;
            ScanParams params = new ScanParams();
            params.match("#*@*");
            List<String> logins = new ArrayList<>();
            do{
                scan = con.scan(cursor,params);
                if(scan!=null) {
                    cursor = scan.getStringCursor();
                    logins.addAll(scan.getResult());
                }
            }while(!Objects.equals(cursor, ScanParams.SCAN_POINTER_START) || scan == null);
            logins.forEach(e->{
                LoginRedisInfo loginRedisInfo = new LoginRedisInfo();
                loginRedisInfo.login = e.substring(1,e.length());
                loginRedisInfo.sessions = new ArrayList<>();
                Set<String> smembers = con.smembers(e);
                if(smembers!=null) {
                    smembers.forEach(l -> {
                        RedisSession redisSession = new RedisSession();
                        redisSession.sessionId = l;
                        redisSession.createTime = con.hget(l, CREATE_TIME);
                        redisSession.status = con.hget(l, STATUS);
                        loginRedisInfo.sessions.add(redisSession);
                    });
                }
                res.add(loginRedisInfo);
            });
        }catch (Exception ex){
            log.error("error getSessions", ex);
        }
        return res;
    }

    @Override
    public boolean dropUserSessions(String login){
        boolean res = false;
        try(Jedis con = getConnection()){
            String s = SESSION_LIST_MARKER + login;
            Set<String> smembers = con.smembers(s);
            smembers.forEach(e-> {
                con.hdel(e, USER_LOGIN, CREATE_TIME, STATUS);
                con.srem(s, e);
            });
            res = con.del(login) > 0;
            SystemOperation systemOperation = new SystemOperation(SystemOperation.CLOSE_SESSIONS, login);
            con.publish(SYSTEM_CHANEL, gson.toJson(systemOperation));
        }catch (Exception ex){
            log.error("error deleteSession",ex);
        }
        return res;
    }

    @Override
    public int incrementAndGetLoginAttempt(String login){
        int attempt = 0;
        try(Jedis con = getConnection()){
            String hget = con.hget(LOGIN_ATTEMPT_MARKER+login, LOGIN_ATTEMPT);
            if(hget!=null){
                attempt = Integer.valueOf(hget);
            }
            con.hset(LOGIN_ATTEMPT_MARKER+login,LOGIN_ATTEMPT, String.valueOf(++attempt));
            con.expire(LOGIN_ATTEMPT_MARKER+login,LOGIN_ATTEMPT_LIFETIME);
        }catch (Exception ex){
            log.error("error incrementAndGetLoginAttempt",ex);
        }
        return attempt;
    }

    @Override
    public void clearLoginAttempt(String login){
        try(Jedis con = getConnection()){
            con.hdel(LOGIN_ATTEMPT_MARKER+login, LOGIN_ATTEMPT);
        }catch (Exception ex){
            log.error("error clearLoginAttempt",ex);
        }
    }

    @Override
    public int getLoginAttempt(String login) {
        int attempt = -1;
        try(Jedis con = getConnection()){
            attempt = Integer.valueOf(con.hget(LOGIN_ATTEMPT_MARKER+login, LOGIN_ATTEMPT));
        }catch (Exception ex){
            log.error("error getLoginAttempt",ex);
        }
        return attempt;
    }

    @Override
    public void saveExternalSignRequest(String uuid, ExternalSignRequest request){
        try(Jedis con = getConnection()){
            con.set(uuid,gson.toJson(request));
            con.expire(uuid,EXTERNAL_SIGN_TIMEOUT);
        }catch (Exception ex){
            log.error("error saveExternalSignRequest",ex);
        }
    }

    @Override
    public ExternalSignRequest getExternalSignRequest(String uuid){
        try(Jedis con = getConnection()){
            String data = con.get(uuid);
            return gson.fromJson(data,ExternalSignRequest.class);
        }catch (Exception ex){
            log.error("getExternalSignRequest error ",ex);
        }
        return null;
    }

    @Override
    public Long removeExternalSignRequest(String uid){
        try(Jedis con = getConnection()){
            return con.del(uid);
        }catch (Exception ex){
            log.error("getExternalSignRequest error ",ex);
        }
        return null;
    }

    @Override
    public List<LoginRedisInfo> getSessionsInfo() {
        List<LoginRedisInfo> sessions = getSessions();
        try(Jedis con = getConnection()){
            sessions.forEach(s->{
                try {
                    String userJson = con.get(s.login);
                    s.userDTO = gson.fromJson(userJson,UserDTO.class);
                }catch (Exception ex){
                    log.error("error json convert",ex);
                }
            });
        }catch (Exception ex){
            log.error("getSessionsInfo error ",ex);
        }
        return sessions;
    }

    @Override
    public void flushAll(){
        try(Jedis con = getConnection()){
            con.flushAll();
        }catch (Exception ex){
            log.error("getSessionsInfo error ",ex);
        }
    }

    @Override
    public Long cleanEmailList(){
        try(Jedis con = getConnection()){
            return con.del(EmailQueryRedisManager.LIST);
        }catch (Exception ex){
            log.error("clear email list error",ex);
        }
        return -1L;
    }

    @Override
    public boolean allowErgrouRequest(String login) throws Exception{
        try(Jedis con = getConnection()){
            String l = con.get(EGRPOU_ATTEMPT_MARKER + login);
            return l==null;
        }catch (Exception ex){
            log.error("allowErgrouRequest error", ex);
            throw ex;
        }
    }

    @Override
    public void saveErgrouRequest(String login){
        try(Jedis con = getConnection()){
            String s = EGRPOU_ATTEMPT_MARKER + login;
            con.set(s, "egrpou request");
            con.expire(s, EGRPOU_ATTEMPT_TIMEOUT);
        }catch (Exception ex){
            log.error("saveErgrouRequest error", ex);
        }
    }

    @Override
    public void newChatMessageAdded(long roomId, String author, Integer delay){
        try(Jedis con = getConnection()){

            if(delay == null){
                Calendar now = Calendar.getInstance();
                Calendar delayCalendar = Calendar.getInstance();

                int hoursOfDay = now.get(Calendar.HOUR_OF_DAY);
                if (hoursOfDay >= 6 && hoursOfDay < 21) {
                    delayCalendar.set(Calendar.HOUR_OF_DAY, 9);
                    delayCalendar.add(Calendar.DAY_OF_YEAR, 1);
                } else if (hoursOfDay >= 21){
                    delayCalendar.set(Calendar.HOUR_OF_DAY, 12);
                    delayCalendar.add(Calendar.DAY_OF_YEAR, 1);
                } else if (hoursOfDay >= 0 && hoursOfDay < 3){
                    delayCalendar.set(Calendar.HOUR_OF_DAY, 15);
                } else if (hoursOfDay >= 3 && hoursOfDay < 6){
                    delayCalendar.set(Calendar.HOUR_OF_DAY, 18);
                }
                delayCalendar.set(Calendar.MINUTE, 1);
                delayCalendar.set(Calendar.SECOND, 0);
                delayCalendar.set(Calendar.MILLISECOND, 0);

                delay = (int) ((delayCalendar.getTimeInMillis() - now.getTimeInMillis()) / 1000);
            }
            String chatRoomId = CHAT_ROOM_ID + roomId;
            con.hset(chatRoomId, USER_LOGIN, author);
            con.expire(chatRoomId, delay);
            con.set(chatRoomId+USER_LOGIN, author);
            log.info("newChatMessageAdded {} ", chatRoomId);
        }catch (Exception ex){
            log.error("newChatMessageAdded error", ex);
        }
    }

    @Override
    public String getLastRoomAuthor(long roomId){
        String lastAuthor = null;
        try(Jedis con = getConnection()){
            lastAuthor = con.get(CHAT_ROOM_ID+roomId+USER_LOGIN);
        }catch (Exception ex){
            log.error("newChatMessageAdded error", ex);
        }
        return lastAuthor;
    }

    @Override
    public void checkChatMessage(long roomId, String author){
        try(Jedis con = getConnection()){
            String userLogin = con.hget(CHAT_ROOM_ID + roomId, USER_LOGIN);
            if(userLogin!=null && !userLogin.equals(author)){
                con.hdel(CHAT_ROOM_ID+roomId, USER_LOGIN);
                con.del(CHAT_ROOM_ID+roomId+USER_LOGIN);
            }
        }catch (Exception ex){
            log.error("checkChatMessage error", ex);
        }
    }

    @Override
    public void deleteChatMessageInfo(long roomId){
        try(Jedis con = getConnection()){
            con.hdel(CHAT_ROOM_ID+roomId, USER_LOGIN);
            con.del(CHAT_ROOM_ID+roomId+USER_LOGIN);
        }catch (Exception ex){
            log.error("checkChatMessage error", ex);
        }
    }
}