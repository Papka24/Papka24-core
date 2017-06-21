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

import ua.papka24.server.api.DTO.GroupInviteDTO;
import ua.papka24.server.db.redis.model.LoginRedisInfo;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.ExternalSignRequest;
import ua.papka24.server.db.redis.mock.MockDAO;
import ua.papka24.server.security.Session;
import ua.papka24.server.utils.datetime.DateTimeUtils;

import java.util.List;
import java.util.Set;

public interface RedisDAOManager {

    //таймауты в секундах для redis
    int SESSION_LIFETIME           = (int) DateTimeUtils.ONE_WEEK / 1000;
    int BOT_SESSION_LIFETIME       = (int) (2 * DateTimeUtils.ONE_HOUR) / 1000;
    int REGRES_LIFETIME            = (int) (25 * DateTimeUtils.MINUTE) / 1000;
    int INVITE_LIFETIME            = (int) DateTimeUtils.ONE_WEEK / 1000;
    int CLOUD_LIFETIME             = (int) (2 * DateTimeUtils.ONE_HOUR) / 1000;
    int SECRET_LIFETIME            = (int) (10 * DateTimeUtils.MINUTE) / 1000;
    int EXTERNAL_SIGN_TIMEOUT      = (int) (DateTimeUtils.ONE_HOUR) / 1000;
    int EGRPOU_ATTEMPT_TIMEOUT     = (int) (DateTimeUtils.MINUTE) / 1000;
    int HOURS_12                   = (int) (DateTimeUtils.ONE_HOUR * 12) / 1000;
    int LOGIN_ATTEMPT_LIFETIME     = Integer.valueOf(Main.property.getProperty("lfrequency.time","5")) * 60;
    String sessionStorageEnable = Main.property.getProperty("sessionStorage.enable","false");

    String SESSION_CREATED = "c";
    String SESSION_CLOSED = "x";

    Long removeExternalSignRequest(String uid);
    List<LoginRedisInfo> getSessionsInfo();

    void flushAll();

    Long cleanEmailList();

    boolean allowErgrouRequest(String login) throws Exception;

    void saveErgrouRequest(String login);

    void newChatMessageAdded(long roomId, String author, Integer delay);

    String getLastRoomAuthor(long roomId);

    void checkChatMessage(long roomId, String author);

    void deleteChatMessageInfo(long roomId);

    final class DefaultSettings{

        private static final String DEFAULT_SESSION_STORAGE = Main.property.getProperty("defaultSessionStorage");

    }
    ExternalSignRequest getExternalSignRequest(String uuid);
    void saveExternalSignRequest(String uuid, ExternalSignRequest request);
    void updateUserSession(Session session, boolean isBot);
    void closeSession(String login, String sessionId);
    Session findSession(String sessionId);
    boolean checkSession(String sessionId);
    void saveRegResInfo(String secret, String data);
    String getRegResInfo(String secret);
    void markChanged(Session session);
    void markChanged(String userLogin);
    void addCloudSignCheck(String docIdentifier, String encodeString);
    String getCloudSign(String cloudId);
    void deleteCloudSign(String cloudId);
    Set<String> getUserSessions(String userLogin);
    void saveCompanyInvite(GroupInviteDTO groupInvite);
    boolean companyInviteExists(GroupInviteDTO groupInvite);
    void invalidateAllUsersSessions();

    List<LoginRedisInfo> getSessions();

    boolean dropUserSessions(String login);

    /**
     * в случае неуспешной попытки логина необходимо увеличить счетчик попыток логина
     * @param login
     * @return
     */
    int incrementAndGetLoginAttempt(String login);

    /**
     * очистка записей о количестве неуспешных логинов пользователя
     * @param login
     */
    void clearLoginAttempt(String login);

    /**
     * получение количества неуспешных попыток входа пользователя
     * @param login
     * @return
     */
    int getLoginAttempt(String login);

    static RedisDAOManager getInstance(String serverId) {
        if(sessionStorageEnable.equals("true")) {
            if (serverId == null) {
                serverId = "redis";
            }
            switch (serverId) {
                default:
                case "scylla": {
                    throw new UnsupportedOperationException("scylla session not supported");
                }
                case "redis": {
                    return RedisDAO.getInstance();
                }
            }
        }else{
            return new MockDAO();
        }
    }

    static RedisDAOManager getInstance() {
        return getInstance(DefaultSettings.DEFAULT_SESSION_STORAGE);
    }
}
