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

package ua.papka24.server.db.dao;

import ua.papka24.server.api.DTO.EmailInfo;
import ua.papka24.server.api.helper.EmailHelper;
import ua.papka24.server.db.dto.UserDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;


public class SpamDAO extends DAO {

    private SpamDAO(){}

    private static class Singleton {
        private static final SpamDAO HOLDER_INSTANCE = new SpamDAO();
    }

    public static SpamDAO getInstance() {
        return SpamDAO.Singleton.HOLDER_INSTANCE;
    }

    // Письмо-1
    // Условия:
    //    Зарегистрировался
    //    Не подписал ни одного документа более 24 часов с момента регистрации (и до момента отправки письма)
    public void detectNotActiveUsersV2() {
        Connection con = getConnection();
        try{
            Set<String> logins = new HashSet<>();
            Calendar now = Calendar.getInstance();
            now.add(Calendar.DAY_OF_YEAR, -1);
            long stopDate = now.getTimeInMillis();
            now.add(Calendar.DAY_OF_YEAR, -6);
            long startDate = now.getTimeInMillis();
            PreparedStatement ps = con.prepareStatement(
                    "SELECT login FROM users WHERE create_time >= ? and create_time < ?");
            ps.setLong(1, startDate);
            ps.setLong(2, stopDate);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                logins.add(rs.getString(1));
            }
            rs.close();
            ps.close();
            for (String login : logins) {
                PreparedStatement checkStatement = con.prepareStatement("SELECT id FROM resource WHERE author = ? AND signed LIMIT 1");
                checkStatement.setString(1, login);
                ResultSet resultSet = checkStatement.executeQuery();
                if(!resultSet.next()){
                    resultSet.close();
                    checkStatement.close();
                    checkStatement = con.prepareStatement("SELECT resource_id FROM sign_record WHERE login = ? LIMIT 1");
                    checkStatement.setString(1, login);
                    resultSet = checkStatement.executeQuery();
                    if(!resultSet.next()){
                        resultSet.close();
                        checkStatement.close();
                        checkStatement = con.prepareStatement(
                                "SELECT 1 FROM spam WHERE user_login = ? AND type = 1");
                        checkStatement.setString(1, login);
                        resultSet = checkStatement.executeQuery();
                        if(!resultSet.next()){
                            resultSet.close();
                            checkStatement.close();
                            checkStatement = con.prepareStatement(
                                    "SELECT 1 FROM spam_mode WHERE login = ? AND NOT mode & 2 = 0");
                            checkStatement.setString(1, login);
                            resultSet = checkStatement.executeQuery();
                            if(!resultSet.next()){
                                resultSet.close();
                                checkStatement.close();
                                PreparedStatement ins = con.prepareStatement("INSERT INTO spam (user_login, time, type) VALUES (?,?,?)");
                                ins.setString(1, login);
                                ins.setLong(2, (new Date()).getTime());
                                ins.setInt(3, 1);
                                if (ins.executeUpdate() > 0) {
                                    EmailHelper.sendSpam1(login);
                                }
                                ins.close();
                            }
                        }
                    }
                }
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't send spam 1", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
    }

    // Письмо-2
    // Условия:
    //   Не получал Письмо-1
    //   Сам загрузил и подписал документ, но не зашарил более 24 часов
    //   с момента регистрации (и до момента отправки письма)
    public void loadWithoutShare() {
        Connection c = getConnection();
        List<String> users = new ArrayList<>();
        try {
            // get users
            PreparedStatement ps = c.prepareStatement(
                "SELECT u.login " +
                    "FROM users u " +
                        "JOIN resource r ON (r.author = u.login AND r.status = 0) " +
                        "LEFT JOIN resource res ON (res.author = u.login AND res.status !=0) " +
                        "left join spam_mode sm on (sm.login = u.login) " +
                    "WHERE u.create_time < ? " +
                        "AND r.signed " +
                        "and (sm.mode ISNULL or sm.mode & 3 = 0 ) " +
                        "AND (2 NOT IN (SELECT spam.type FROM spam WHERE spam.user_login = u.login)) " +
                        "AND (1 NOT IN (SELECT spam.type FROM spam WHERE spam.user_login = u.login)) " +
                    "GROUP BY u.login " +
                    "HAVING count(res.id) = 0");
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -1);
            long time = cal.getTimeInMillis();
            ps.setLong(1, time);
            ResultSet result = ps.executeQuery();
            while (result.next()) {
                users.add(result.getString("login"));
            }
            for (String user : users) {
                ps = c.prepareStatement("INSERT INTO spam (user_login, time, type) VALUES (?,?,?)");
                ps.setString(1, user);
                ps.setLong(2, (new Date()).getTime());
                ps.setInt(3, 2);
                ps.executeUpdate();
                EmailHelper.sendSpam2(user);
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't send spam 2", e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
    }

    // Письмо-5
    // Условия:
    //   С тобой зашарили, но ты не зарегистрировался более 72 часов с момента зашаривания (и до момента отправки письма)
    public void notRegisteredPeople() {
        Connection c = getConnection();
        try {
            PreparedStatement ps = c.prepareStatement(
                "SELECT s.user_login, r.author, r.id, r.name, u.full_name " +
                        "FROM resource r " +
                            "JOIN share s ON (s.resource_id = r.id) " +
                            "LEFT JOIN spam ON (s.user_login = spam.user_login AND spam.resource_id = r.id) " +
                            "LEFT JOIN users u ON (u.login = r.author) " +
                            "left join spam_mode sm on (sm.login = s.user_login) " +
                        "WHERE s.user_login NOT IN (SELECT users.login FROM users) " +
                                "AND s.status = 0 AND s.time < ? " +
                                "and (sm.mode ISNULL or sm.mode & 4 = 0 ) " +
                                "AND r.status < 10 " +
                                "AND (5 NOT IN (SELECT spam.type FROM spam WHERE spam.user_login = s.user_login AND spam.resource_id = r.id)) " +
                        "ORDER BY s.user_login");
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -3);
            ps.setLong(1, cal.getTimeInMillis());
            ResultSet result = ps.executeQuery();
            HashMap<String, Map<String, List<InfoHolder>>> unUsers = new HashMap<>();
            String userLogin, resourceName, author;
            long resourceId;
            while (result.next()) {
                userLogin = result.getString("user_login");
                resourceId = result.getLong("id");
                resourceName = result.getString("name");
                author = result.getString("author");

                Map<String, List<InfoHolder>> userLog = unUsers.computeIfAbsent(userLogin, k -> new HashMap<>());
                List<InfoHolder> docs = userLog.computeIfAbsent(author, k -> new ArrayList<>(4));
                InfoHolder ih = new InfoHolder();
                ih.resourceId = resourceId;
                ih.desc = resourceName;
                docs.add(ih);
            }

            for (String user : unUsers.keySet()) {
                Map<String, List<InfoHolder>> authorDocs = unUsers.get(user);
                for(String auth : authorDocs.keySet()){
                    List<InfoHolder> infoHolders = authorDocs.get(auth);
                    for(InfoHolder ih : infoHolders) {
                        ps = c.prepareStatement("INSERT INTO spam (user_login, resource_id, time, type) VALUES (?,?,?,?)");
                        ps.setString(1, user);
                        ps.setLong(2, ih.resourceId);
                        ps.setLong(3, (new Date()).getTime());
                        ps.setInt(4, 5);
                        ps.executeUpdate();
                    }
                }
                EmailHelper.sendSpam5(user, authorDocs);
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't send spam 5", e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
    }

    public boolean deleteUserFromSpam(String encryptedEmail) {
        String emailString;
        final int[] type = new int[1];
        EmailInfo email = EmailHelper.getDecryptedEmailInfo(encryptedEmail);
        if (email==null) {
            emailString = EmailHelper.getDecryptedId(encryptedEmail);
            Optional<Integer> reduce = Arrays.stream(EmailHelper.Type.values()).map(e -> e.type).reduce((e1, e2) -> e1 | e2);
            reduce.ifPresent(integer -> type[0] = integer);
        }else{
            emailString = email.getLogin();
            type[0] = email.getType();
        }
        Connection c = getConnection();
        boolean result = false;
        try {
            PreparedStatement ps = c.prepareStatement(
                    "WITH upd AS ( " +
                                "UPDATE spam_mode SET mode = mode | ? WHERE login = ? RETURNING mode " +
                                ") " +
                        "INSERT INTO spam_mode (login, mode) " +
                            "SELECT ?, ? " +
                            "WHERE NOT EXISTS (SELECT 1 FROM upd) ");
            ps.setInt(1, type[0]);
            ps.setString(2, emailString);
            ps.setString(3, emailString);
            ps.setInt(4, type[0]);
            result = ps.executeUpdate() > 0;
            c.commit();
        } catch (Exception e) {
            log.error("Can't disable email spam for {}", email, e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }


    // Письмо-3
    // Условия:
    //   Зашарил подписанный документ с контрагентом, а тот не зарегистрировался более
    //   72 часов с момента зашаривания (и до момента отправки письма)
    // Письмо-4
    // Условия:
    //   Зашарил подписанный документ с контрагентом, тот зарегистрировался, но не
    //   подписал письмо в ответ более 72 часов с момента зашаривания (и до момента отправки письма)
    //
    //   Не получал Письмо-3
    public void sharingWithNoReaction(){
        Connection c = getConnection();
        try {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -3);
            long end = cal.getTimeInMillis();
            cal.add(Calendar.DATE, -3);
            long start = cal.getTimeInMillis();

            String initiator, resourceName, sharedUser;
            HashMap<String, Map<String, List<InfoHolder>>> authorsList = new HashMap<>();
            long resourceId;

            //letter type 4
            PreparedStatement ps = c.prepareStatement(
                    "SELECT s.user_login, s.initiator, s.resource_id, r.name, u.full_name, s.status " +
                    "FROM share s " +
                    "  LEFT JOIN spam_mode sm ON (s.initiator = sm.login) " +
                    "  LEFT JOIN resource r ON (r.author = s.initiator AND s.resource_id = r.id) " +
                    "  JOIN users u ON (u.login = s.initiator) " +
                    "WHERE s.time >= ? AND s.time < ? AND r.status < 10 " +
                    "  AND s.status not in (0,3,13) " +
                    "  AND s.spam_mode = 0 AND (sm.mode ISNULL OR sm.mode & 5 = 0 ) " +
                    "  and not 3 in (select spam.type from spam where spam.user_login = s.initiator and spam.resource_id = s.resource_id) " +
                    "  and not 4 in (select spam.type from spam where spam.user_login = s.initiator and spam.resource_id = s.resource_id) ");
            ps.setLong(1, start);
            ps.setLong(2, end);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                initiator = rs.getString(2);
                sharedUser = rs.getString(1);

                InfoHolder ri = new InfoHolder();
                ri.resourceId = rs.getLong(3);
                ri.desc = rs.getString(4);
                ri.sharedUserFullName = rs.getString(5);
                ri.status = rs.getInt(6);
                ri.type = 4;

                Map<String, List<InfoHolder>> sharedUsersList = authorsList.computeIfAbsent(initiator, k -> new HashMap<>());

                List<InfoHolder> documents = sharedUsersList.computeIfAbsent(sharedUser, k -> new ArrayList<>());
                documents.add(ri);
            }
            ps.close();
            // letter type 3
            ps = c.prepareStatement(
                    "SELECT s.user_login, s.initiator, s.resource_id, r.name, s.status "+
                            "FROM share s " +
                            "  LEFT JOIN spam_mode sm ON (s.initiator = sm.login) " +
                            "  LEFT JOIN resource r ON (r.author = s.initiator AND s.resource_id = r.id) " +
                            "WHERE s.time >= ? AND s.time < ? AND r.status < 10 AND s.status = 0 " +
                            "  AND s.spam_mode = 0 AND (sm.mode ISNULL OR sm.mode & 5 = 0 ) " +
                            "  AND NOT 3 in (SELECT spam.type FROM spam WHERE spam.user_login = s.initiator AND spam.resource_id = s.resource_id) ");
            ps.setLong(1, start);
            ps.setLong(2, end);
            rs = ps.executeQuery();
            while(rs.next()){
                sharedUser = rs.getString(1);
                initiator = rs.getString(2);
                resourceId = rs.getLong(3);
                resourceName = rs.getString(4);

                //берем по автору
                Map<String, List<InfoHolder>> sharedUsersList = authorsList.computeIfAbsent(initiator, k -> new HashMap<>());

                //берем по контрагенту
                List<InfoHolder> documents = sharedUsersList.computeIfAbsent(sharedUser, k -> new ArrayList<>());

                InfoHolder ih = new InfoHolder();
                ih.resourceId = resourceId;
                ih.desc = resourceName;
                ih.type = 3;
                ih.status = rs.getInt(5);
                documents.add(ih);
            }
            //check that we do

            //update table
            for(String resAuthor : authorsList.keySet()){
                Map<String,List<InfoHolder>> sharedUsersList = authorsList.get(resAuthor);
                for(String sharUser : sharedUsersList.keySet()){
                    List<InfoHolder> documents = sharedUsersList.get(sharUser);
                    for(InfoHolder ih : documents){
                        ps = c.prepareStatement(
                                "WITH sel AS ( " +
                                "     SELECT 1 FROM spam WHERE user_login = ? AND resource_id = ? AND type = ? " +
                                " ) " +
                                " INSERT INTO spam (user_login, resource_id, time, type) SELECT ?,?,?,?  WHERE NOT EXISTS (SELECT 1 FROM sel) ");
                        ps.setString(1, resAuthor);
                        ps.setLong(2, ih.resourceId);
                        ps.setInt(3, ih.type);
                        ps.setString(4, resAuthor);
                        ps.setLong(5, ih.resourceId);
                        ps.setLong(6, (new Date()).getTime());
                        ps.setInt(7, ih.type);
                        ps.executeUpdate();
                    }
                }
                //send spam
                EmailHelper.sendSpam34(resAuthor, sharedUsersList);
            }
            c.commit();
        }catch (Exception e) {
            log.error("Can't send spam 3", e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
    }

    public boolean isEmailAllowed(String login, int type){
        Connection con = getConnection();
        boolean res = true;
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT ? = mode & ? " +
                            "FROM spam_mode " +
                            "WHERE login = ? ");
            ps.setInt(1, type);
            ps.setInt(2, type);
            ps.setString(3, login);
            ResultSet resultSet = ps.executeQuery();
            if(resultSet.next()){
                res = !resultSet.getBoolean(1);
            }
            con.commit();
        }catch (Exception e) {
            log.error("check spam restriction", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public static class InfoHolder {
        public long resourceId;
        public String desc;
        public int type;
        public String sharedUserFullName;
        public int status;
    }

    int deleteSpamUser(Connection con, UserDTO user) throws SQLException {
        PreparedStatement ps = con.prepareStatement("DELETE FROM spam where user_login = ? ");
        ps.setString(1, user.getLogin());
        return ps.executeUpdate();
    }

    int deleteUserFromSpamMode(Connection con, String login) throws SQLException {
        PreparedStatement ps = con.prepareStatement("DELETE FROM spam_mode where login = ? ");
        ps.setString(1, login);
        return ps.executeUpdate();
    }

    public int deleteUserFromSpamMode(String login) {
        int res = 0;
        Connection con = getConnection();
        try{
            res = deleteUserFromSpamMode(con, login);
            con.commit();
        }catch (Exception e) {
            log.error("deleteUserFromSpamMode", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public int deleteSpamModeFromUser(UserDTO user, int spamMode) {
        int res = 0;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE spam_mode SET mode = (mode & ~?) where login = ?");
            ps.setInt(1, spamMode);
            ps.setString(2, user.getLogin());
            res = ps.executeUpdate();
            con.commit();
        }catch (Exception e) {
            log.error("deleteSpamModeFromUser", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public Integer getUserSpamMode(UserDTO user) {
        Integer res = null;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT mode FROM spam_mode WHERE login = ?");
            ps.setString(1, user.getLogin());
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                res = rs.getInt(1);
            }
            con.commit();
        }catch (Exception e) {
            log.error("deleteSpamModeFromUser", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public void addSpamMode(String login, Integer spamType) {
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "WITH upd AS ( " +
                            "UPDATE spam_mode SET mode = mode | ? WHERE login = ? RETURNING mode " +
                            ") " +
                            "INSERT INTO spam_mode (login, mode) " +
                            "SELECT ?, ? " +
                            "WHERE NOT EXISTS (SELECT 1 FROM upd) ");
            ps.setInt(1, spamType);
            ps.setString(2, login);
            ps.setString(3, login);
            ps.setInt(4, spamType);
            ps.executeUpdate();
            con.commit();
        }catch (Exception e) {
            log.error("deleteSpamModeFromUser", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
    }
}
