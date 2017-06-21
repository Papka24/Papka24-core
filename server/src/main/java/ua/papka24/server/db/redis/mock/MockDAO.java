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

package ua.papka24.server.db.redis.mock;

import ua.papka24.server.api.DTO.ExternalSignRequest;
import ua.papka24.server.api.DTO.GroupInviteDTO;
import ua.papka24.server.db.redis.RedisDAOManager;
import ua.papka24.server.db.redis.model.LoginRedisInfo;
import ua.papka24.server.security.Session;

import java.util.List;
import java.util.Set;

public class MockDAO implements RedisDAOManager {

    @Override
    public void invalidateAllUsersSessions() {

    }

    @Override
    public List<LoginRedisInfo> getSessions() {
        return null;
    }

    @Override
    public boolean dropUserSessions(String login) {
        return false;
    }

    @Override
    public int incrementAndGetLoginAttempt(String login) {
        return 0;
    }

    @Override
    public void clearLoginAttempt(String login) {

    }

    @Override
    public int getLoginAttempt(String login) {
        return 0;
    }

    @Override
    public Long removeExternalSignRequest(String uid) {
        return null;
    }

    @Override
    public List<LoginRedisInfo> getSessionsInfo() {
        return null;
    }

    @Override
    public void flushAll() {

    }

    @Override
    public Long cleanEmailList() {
        return 0L;
    }

    @Override
    public boolean allowErgrouRequest(String login) {
        return false;
    }

    @Override
    public void saveErgrouRequest(String login) {

    }

    @Override
    public void newChatMessageAdded(long roomId, String author, Integer delay) {

    }

    @Override
    public String getLastRoomAuthor(long roomId) {
        return null;
    }

    @Override
    public void checkChatMessage(long roomId, String author) {

    }

    @Override
    public void deleteChatMessageInfo(long roomId) {

    }

    @Override
    public ExternalSignRequest getExternalSignRequest(String uuid) {
        return null;
    }

    @Override
    public void saveExternalSignRequest(String uuid, ExternalSignRequest request) {

    }

    @Override
    public void updateUserSession(Session session, boolean isBot) {
    }

    @Override
    public Set<String> getUserSessions(String login) {
        return null;
    }

    @Override
    public void closeSession(String login, String sessionId) {

    }

    @Override
    public Session findSession(String sessionId) {
        return null;
    }

    @Override
    public boolean checkSession(String sessionId) {
        return false;
    }

    @Override
    public void saveRegResInfo(String secret, String data) {
    }

    @Override
    public String getRegResInfo(String secret) {
        return null;
    }


    @Override
    public void markChanged(Session session) {
    }

    @Override
    public void markChanged(String userLogin) {

    }

    @Override
    public void addCloudSignCheck(String docIdentifier, String encodeString) {
    }

    @Override
    public String getCloudSign(String cloudId) {
        return null;
    }

    @Override
    public void deleteCloudSign(String cloudId) {
    }

    @Override
    public void saveCompanyInvite(GroupInviteDTO groupInvite) {

    }

    @Override
    public boolean companyInviteExists(GroupInviteDTO groupInvite) {
        return false;
    }
}
