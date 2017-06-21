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

import ua.papka24.server.api.DTO.UserDescriptionDTO;
import ua.papka24.server.db.dto.EmployeeDTO;
import ua.papka24.server.db.dto.FriendDTO;
import ua.papka24.server.api.DTO.AdminAnalyticsDTO;
import ua.papka24.server.api.DTO.CompanyDTO;
import ua.papka24.server.api.DTO.CompanyInfoDTO;
import ua.papka24.server.db.dto.UserDTO;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;


public class UserDAO extends DAO {

    private UserDAO(){}

    private static class Singleton {
        private static final UserDAO HOLDER_INSTANCE = new UserDAO();
    }

    public static UserDAO getInstance() {
        return UserDAO.Singleton.HOLDER_INSTANCE;
    }

    public UserDTO getUser(String login) {
        UserDTO user = null;
        login = login.toLowerCase();
        Connection c = getConnection();
        try {

            PreparedStatement ps = c.prepareStatement("SELECT u.login, u.description, u.full_name, u.password_digest, u.security_descr, u.friends, u.heavy, u.auth_code, u.company_id, u.blocked  FROM users AS u WHERE u.login = ?");
            ps.setString(1, login);
            ResultSet result = ps.executeQuery();

            if (result.next()) {
                user = new UserDTO(result);
                Array friendsRaw = result.getArray(6);
                Long companyId = result.getLong(9);
                if(result.wasNull()){
                    companyId = null;
                }
                if (companyId != null) {
                    ps = c.prepareStatement("SELECT c.name, e.login, e.role, e.status, e.start_date, e.initiator, e.stop_date, e.initiator  FROM employees e join companies c on (c.id = e.company_id) WHERE e.company_id = ?");
                    ps.setLong(1, companyId);
                    result = ps.executeQuery();
                    String companyName = null;
                    List<EmployeeDTO> employee = new ArrayList<>();
                    while (result.next()) {
                        if (companyName == null) {
                            companyName = result.getString(1);
                        }
                        EmployeeDTO empl = new EmployeeDTO(result.getString(2),companyId, result.getLong(3), result.getLong(5), result.getLong(7), result.getLong(4), result.getString(8));
                        employee.add(empl);
                    }
                    if (companyName != null) {
                        user.setCompanyDTO(new CompanyDTO(companyId, companyName, employee));
                    }
                }

                if (friendsRaw != null && ((String[]) friendsRaw.getArray()).length > 0) {
                    Map<String, String> friendList = new HashMap<>();
                    String[] friends = (String[]) friendsRaw.getArray();
                    int position = 0;
                    int stepSize = 30;
                    while(position < friends.length){
                        int size = stepSize;
                        if(position+stepSize > friends.length){
                            size = friends.length - position;
                        }
                        if(friends.length < stepSize){
                            size = friends.length;
                        }
                        String[] friendsPart = new String[size];
                        System.arraycopy(friends, position, friendsPart, 0, size);

                        for (String f : friendsPart) {
                            friendList.put(f, "");
                        }
                        StringBuilder builder = new StringBuilder();
                        for (int i = friendsPart.length; i > 0; i--) {
                            builder.append("?,");
                        }
                        ps = c.prepareStatement("SELECT u.login, u.full_name FROM users AS u WHERE u.login IN (" + builder.deleteCharAt(builder.length() - 1).toString() + ")");
                        for (int i = friendsPart.length; i > 0; i--) {
                            ps.setString(i, friendsPart[i - 1]);
                        }
                        result = ps.executeQuery();
                        while (result.next()) {
                            friendList.put(result.getString(1), result.getString(2));
                        }
                        position +=size;
                    }
                    user.setFriends(friendList);
                }
            } else {
                try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
            }
            c.commit();
        } catch (SQLException e) {
            user = null;
            log.error("Can't get user with login = {}", login, e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return user;
    }

    public UserDTO getEmptyUser(String login) {
        UserDTO user = null;
        login = login.toLowerCase();
        Connection c = getConnection();
        try {

            PreparedStatement ps = c.prepareStatement("SELECT u.login, u.password_digest, u.security_descr, u.auth_code, u.blocked, u.company_id, u.description, u.full_name FROM users AS u WHERE u.login = ?");
            ps.setString(1, login);
            ResultSet result = ps.executeQuery();
            if (result.next()) {
                user = new UserDTO(result.getString(1), result.getString(2), result.getLong(3), result.getString(4), result.getLong(5));
                user.setCompanyId(result.getObject(6,Long.class));
                user.setDescription(result.getString(7));
                user.setFullName(result.getString(8));
                Long companyId = user.getCompanyId();
                if (companyId != null) {
                    user.setCompanyDTO(new CompanyDTO(companyId, null, null));
                }
            } else {
                try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
            }
            c.commit();
        } catch (SQLException e) {
            user = null;
            log.error("Can't get user with login = {}", login, e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return user;
    }


    public int create(UserDTO user) {
        int result = 0;
        Connection c = getConnection();
        try {
            PreparedStatement ps = c.prepareStatement("INSERT INTO users (login, description, full_name, password_digest, security_descr, create_time, company_id) VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, user.getLogin());
            ps.setString(2, user.getDescription());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getPasswordDigest());
            ps.setLong(5, user.getPrivileges().getSecurityDescriptor());
            ps.setLong(6, (new java.util.Date()).getTime());
            if(user.getCompanyId()==null){
                ps.setNull(7, Types.BIGINT);
            }else{
                ps.setLong(7, user.getCompanyId());
            }
            result = ps.executeUpdate();
            try {
                ps = c.prepareStatement(
                        "WITH upd AS ( " +
                                "SELECT company_id " +
                                "FROM employees " +
                                "WHERE login = ? AND status = 1 " +
                                ") " +
                                "UPDATE users " +
                                "SET company_id = (SELECT company_id FROM upd) WHERE login = ? AND EXISTS( SELECT 1 FROM upd ) RETURNING company_id ");
                ps.setString(1, user.getLogin());
                ps.setString(2, user.getLogin());
                ResultSet rs = ps.executeQuery();
                if(rs.next()){
                    long aLong = rs.getLong(1);
                    if(!rs.wasNull()){
                        user.setCompanyId(aLong);
                    }
                }
            }catch (Exception ex){
                log.warn("error set company", ex);
            }

            ps = c.prepareStatement("UPDATE share SET STATUS = 1 WHERE user_login = ?");
            ps.setString(1, user.getLogin());
            ps.executeUpdate();
            ps.close();

            ps = c.prepareStatement(
                    "UPDATE resource_cache SET status = 1 WHERE owner = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
            ps.setString(1, user.getLogin());
            ps.setString(2, user.getLogin());
            ps.executeUpdate();

            ps = c.prepareStatement("INSERT INTO analytics_registration(login) VALUES (?)");
            ps.setString(1, user.getLogin());
            ps.executeUpdate();

            c.commit();
        } catch (Exception e) {
            String mess = e.getMessage();
            if(mess!=null && mess.contains("users_pkey")){
               log.error("trying to create duplicate user : {}", user.getLogin());
            }else {
                log.error("Can't create user with login = {}", user.getLogin(), e);
            }
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public UserDTO setCompany(UserDTO user, Long companyId){
        Connection con = getConnection();
        assert con != null;
        try{
            PreparedStatement ps = con.prepareStatement("update users set company_id = ? where login = ? ");
            ps.setLong(1,companyId);
            ps.setString(2,user.getLogin());
            ps.executeUpdate();
            ps = con.prepareStatement("update resource set company_id = ? where author = ? and company_id ISNULL ");
            ps.setLong(1,companyId);
            ps.setString(2,user.getLogin());
            ps.executeUpdate();
            ps = con.prepareStatement("update share set company_id = ? where user_login = ? and company_id ISNULL ");
            ps.setLong(1,companyId);
            ps.setString(2,user.getLogin());
            ps.executeUpdate();
            ps.close();

            ps = con.prepareStatement(
                    "UPDATE resource_cache SET company_id = ? WHERE owner = ? AND company_id = ? AND (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
            ps.setLong(1, companyId);
            ps.setString(2, user.getLogin());
            ps.setLong(3, companyId);
            ps.setString(4, user.getLogin());
            ps.executeUpdate();
            ps.close();

            con.commit();
            user.invalidate();
        } catch (Exception e) {
            log.error("Can't set user company:{}:{}", user, companyId);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return user;
    }



    public int changeDescription(String login, String description) {
        int result = 0;
        login = login.toLowerCase();
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("UPDATE users SET description = ? WHERE users.login = ?");
            ps.setString(1, description);
            ps.setString(2, login);
            result = ps.executeUpdate();
            if (result == 0) {
                c.rollback();
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't create user with login = {}", login, e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public int changePassword(String login, String password) {
        int result = 0;
        login = login.toLowerCase();
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("UPDATE users SET password_digest = ? WHERE users.login = ?");
            ps.setString(1, UserDTO.hash(password));
            ps.setString(2, login);
            result = ps.executeUpdate();
            if (result == 0) {
                c.rollback();
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't update password for login = {}", login, e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public int changeFullName(String login, String name) {
        int result = 0;
        login = login.toLowerCase();
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("UPDATE users SET full_name = ? WHERE users.login = ?");
            ps.setString(1, name);
            ps.setString(2, login);
            result = ps.executeUpdate();
            if (result == 0) {
                c.rollback();
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't update FullName for login = {}", login, e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public boolean deactivateWizard(UserDTO user) {
        Connection c = getConnection();
        try {
            PreparedStatement ps = c.prepareStatement("SELECT u.description FROM users AS u WHERE u.login = ?");
            ps.setString(1, user.getLogin());
            ResultSet result = ps.executeQuery();
            if (result.next()) {
                String d = result.getString(1);
                UserDescriptionDTO description;
                if (d == null) {
                    description = new UserDescriptionDTO();
                } else {
                    try {
                        description = UserDescriptionDTO.gson.fromJson(result.getString(1));
                    } catch (IOException ignored) {
                        description = new UserDescriptionDTO();
                    }
                }
                ps = c.prepareStatement("UPDATE users SET description = ? WHERE users.login = ?");
                description.setShowWizard(false);
                d = UserDescriptionDTO.gson.toJson(description);
                user.setDescription(d);
                ps.setString(1, d);
                ps.setString(2, user.getLogin());
                int r =  ps.executeUpdate();
                c.commit();
                return r>0;
            }
        } catch (Exception e) {
            log.error("Can't deactivate wizard for login = {}", user.getLogin(), e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return false;
    }

    public int updateTags(UserDTO user, Long tagReset, String tags) {
        int result = 0;
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps;
            if (tagReset != 0) {
                ps = c.prepareStatement("UPDATE resource AS r SET tags = (tags & ?) WHERE r.author = ? AND tags IS NOT NULL");
                ps.setLong(1, ~tagReset);
                ps.setString(2, user.getLogin());
                result = ps.executeUpdate();
                ps = c.prepareStatement("UPDATE share AS s SET tags = (tags & ?) WHERE s.user_login = ? AND tags IS NOT NULL");
                ps.setLong(1, ~tagReset);
                ps.setString(2, user.getLogin());
                result = ps.executeUpdate();
            }
            UserDescriptionDTO ud = UserDescriptionDTO.gson.fromJson(user.getDescription());
            tags = tags.replace("<", "").replace(">", "");
            ud.setTagList(tags);
            user.setDescription(UserDescriptionDTO.gson.toJson(ud));
            ps = c.prepareStatement("UPDATE users SET description = ? WHERE login = ?");
            ps.setString(1, user.getDescription());
            ps.setString(2, user.getLogin());
            result = ps.executeUpdate();
            c.commit();
        } catch (Exception e) {
            log.error("Can't update tags for login = {}", user.getLogin(), e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public Map<String,String> addFriends(UserDTO user, List<String> friendsEmails){
        if(user==null || friendsEmails==null || friendsEmails.isEmpty()){
            return new HashMap<>();
        }
        Map<String,String> friends = new HashMap<>();
        friends.putAll(friendsEmails.stream().collect(Collectors.toMap(e->e,e->"")));
        Connection con = getConnection();
        try{
            StringBuilder builder = new StringBuilder();
            for (int i = friendsEmails.size(); i > 0; i--) {
                builder.append("?,");
            }
            PreparedStatement ps = con.prepareStatement("SELECT u.login, u.full_name FROM users AS u WHERE u.login IN (" + builder.deleteCharAt(builder.length() - 1).toString() + ")");
            for (int i = friendsEmails.size(); i > 0; i--) {
                ps.setString(i, friendsEmails.get(i - 1));
            }
            ResultSet resultSet = ps.executeQuery();
            while(resultSet.next()){
                String userLogin = resultSet.getString(1);
                String userFullname = resultSet.getString(2);
                String s = friends.get(userLogin);
                if(s!=null){
                    friends.put(userLogin, userFullname);
                }
            }
            Map<String, String> newFriends = friends.entrySet().stream().filter(e -> !user.getFriends().keySet().contains(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            for(String friendLogin : newFriends.keySet()) {
                ps = con.prepareStatement("UPDATE users AS u SET friends = array_append(u.friends,?::TEXT) WHERE u.login = ? AND ( NOT u.friends && ? OR u.friends ISNULL )");
                ps.setString(1, friendLogin);
                ps.setString(2, user.getLogin());
                ps.setArray(3, con.createArrayOf("text", new String[]{friendLogin}));
                ps.executeUpdate();
            }
            con.commit();
            user.getFriends().putAll(friends);
        }catch (Exception ex) {
            log.error("Can't add users friends", ex);
            try {
                con.rollback();
            } catch (SQLException sqe) {
                log.error("fail rollback connection", ex);
            }
        } finally {
            try {
                con.close();
            } catch (Exception ex) {
                log.error("fail close connection", ex);
            }
        }
        return friends;
    }

    public List<FriendDTO> getFriends(UserDTO user) {
        List<FriendDTO> result = new ArrayList<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("SELECT f.name, f.egrpou, f.logins FROM friends_catalog AS f WHERE f.login = ?");
            ps.setString(1, user.getLogin());
            ResultSet resultSet = ps.executeQuery();
            while(resultSet.next()){
                result.add(new FriendDTO(resultSet));
            }
            con.commit();
        }catch (Exception ex) {
            log.error("Can't get users friends", ex);
            try {
                con.rollback();
            } catch (SQLException sqe) {
                log.error("fail rollback connection", ex);
            }
        } finally {
            try {
                con.close();
            } catch (Exception ex) {
                log.error("fail close connection", ex);
            }
        }
        return result;
    }


    public int enableOTP(UserDTO user) {
        int result = 0;
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("UPDATE users SET auth_code = ? WHERE users.login = ?");
            ps.setString(1, user.getAuthData());
            ps.setString(2, user.getLogin());
            result = ps.executeUpdate();
            if (result == 0) {
                c.rollback();
                return result;
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't set OTP for user with login = {}", user.getLogin(), e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public int disableOTP(String login) {
        int result = 0;
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("UPDATE users SET auth_code = ? WHERE users.login = ? AND users.auth_code NOTNULL");
            ps.setString(1, null);
            ps.setString(2, login);
            result = ps.executeUpdate();
            if (result == 0) {
                c.rollback();
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't disable OTP for user with login = {}", login, e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public List<String> list(){
        List<String> logins = new ArrayList<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("select login from users ");
            ResultSet resultSet = ps.executeQuery();
            while(resultSet.next()){
                logins.add(resultSet.getString(1));
            }
            con.commit();
        }catch (Exception ex){
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return logins;
    }

    private Map<String, AdminAnalyticsDTO> getUsersAnalyticsInfo(List<String> requestList, long from, long to) {
        //choose where condition
        String where;
        if(requestList==null){
            where = "WHERE u.create_time >= ? AND u.create_time<= ? ";
        }else{
            String wher = requestList.stream().map(e -> "?").collect(Collectors.joining(","));
            where = "WHERE u.login in (" + wher + ") ";
        }
        Map<String, AdminAnalyticsDTO> usersAnalyticsInfo = new HashMap<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT u.login, u.full_name, u.create_time, u.company_id, c.name,  sr.egrpou, sr.company, sr.inn, u.friends " +
                            "FROM users u " +
                            "LEFT JOIN sign_record sr ON (u.login = sr.login) " +
                            "LEFT JOIN companies c on (u.company_id = c.id) " +
                            where +
                            "GROUP BY u.login, u.full_name, u.create_time, u.company_id, c.name, sr.egrpou, sr.company, sr.inn ");
            if(requestList==null){
                ps.setLong(1, from);
                ps.setLong(2, to);
            }else{
                final int[] i = {0};
                requestList.forEach(e -> {
                    try {
                        ps.setString(++i[0], e);
                    } catch (SQLException e1) {
                        log.warn("error", e1);
                    }
                });
            }
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                String login = rs.getString(1);
                AdminAnalyticsDTO adminAnalyticsDTO = usersAnalyticsInfo.computeIfAbsent(login, k -> new AdminAnalyticsDTO(login));
                adminAnalyticsDTO.fullName = rs.getString(2);
                adminAnalyticsDTO.registerDate = rs.getLong(3);
                Long companyId = rs.getLong(4);
                if(rs.wasNull()){
                    adminAnalyticsDTO.currentCompanyId = null;
                }else{
                    adminAnalyticsDTO.currentCompanyId = companyId;
                }
                adminAnalyticsDTO.currentCompanyName = rs.getString(5);

                Long egrpou = rs.getLong(6);
                if(rs.wasNull()){
                    egrpou = null;
                }
                String name = rs.getString(7);
                Long inn = rs.getLong(8);
                if(rs.wasNull()){
                    inn = null;
                }
                if(egrpou!=null || name!=null || inn!=null) {
                    CompanyInfoDTO companyInfoDTO = new CompanyInfoDTO(egrpou, name, inn);
                    adminAnalyticsDTO.companies.add(companyInfoDTO);
                }

                Array array = rs.getArray(9);
                if (array != null) {
                    adminAnalyticsDTO.friends = new ArrayList<>(Arrays.asList((String[]) array.getArray()));
                }
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't get user company info", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return usersAnalyticsInfo;
    }

    public Map<String, AdminAnalyticsDTO> getUsersAnalyticsInfo(long from, long to){
        return getUsersAnalyticsInfo(null, from, to);
    }

    public Map<String, AdminAnalyticsDTO> getUsersAnalyticsInfo(List<String> requestList) {
        return getUsersAnalyticsInfo(requestList, -1, -1);
    }

    public List<AdminAnalyticsDTO> getEgrpouAnalyticsInfo(List<Long> egrpouList) {
        List<AdminAnalyticsDTO> adminAnalyticsDTOs = new ArrayList<>();
        if(egrpouList==null || egrpouList.isEmpty()){
            return adminAnalyticsDTOs;
        }
        Connection con = getConnection();
        try{
            String wher = egrpouList.stream().map(e->"?").collect(Collectors.joining(","));
            PreparedStatement ps = con.prepareStatement(
                    "SELECT sr.egrpou, sr.inn, sr.company, count(sr.resource_id) " +
                        "FROM sign_record sr " +
                        "where sr.egrpou in ( "+wher+" ) " +
                        "GROUP BY sr.egrpou, sr.inn, sr.company ");
            int i = 1;
            for (Long aLong : egrpouList) {
                ps.setLong(i++, aLong);
            }
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                Long egrpou = rs.getLong(1);
                if(rs.wasNull()){
                    egrpou = null;
                }
                Long inn = rs.getLong(2);
                if(rs.wasNull()){
                    inn = null;
                }
                String companyName = rs.getString(3);
                long signDocCount = rs.getLong(4);
                CompanyInfoDTO cidto = new CompanyInfoDTO(egrpou,companyName,inn);
                AdminAnalyticsDTO adminAnalyticsDTO = new AdminAnalyticsDTO(cidto);
                adminAnalyticsDTO.signCount = signDocCount;
                adminAnalyticsDTOs.add(adminAnalyticsDTO);
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't get user getFirstShareInfo ", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return adminAnalyticsDTOs;
    }

    public Map<String, AdminAnalyticsDTO> getUserOpCount(final Map<String, AdminAnalyticsDTO> data){
        Set<String> logins = data.keySet();
        if(logins.isEmpty()){
            return data;
        }
        final String where = logins.stream().map(e->"?").collect(Collectors.joining(",","(",")"));
        //количество загруженнх документов
        CountDownLatch latch = new CountDownLatch(3);
        try {
            ExecutorService pool = Executors.newFixedThreadPool(3);
            pool.execute(() -> {
                Connection con = getConnection();
                try {
                    PreparedStatement resourceCount = con.prepareStatement(
                            "SELECT r.author, count(r.id) " +
                                    "FROM resource r " +
                                    " WHERE r.author IN " + where + " AND r.status < 20 " +
                                    " GROUP BY r.author ");
                    int i = 1;
                    for (String login : logins) {
                        resourceCount.setString(i++, login);
                    }
                    ResultSet rs = resourceCount.executeQuery();
                    while (rs.next()) {
                        String login = rs.getString(1);
                        AdminAnalyticsDTO adminAnalyticsDTO = data.get(login);
                        if (adminAnalyticsDTO != null) {
                            adminAnalyticsDTO.docsCount = rs.getLong(2);
                        }
                    }
                    rs.close();
                    resourceCount.close();
                    con.commit();
                    latch.countDown();
                } catch (Exception e) {
                    log.error("Can't get user getUserOpCount ", e);
                    try {
                        con.rollback();
                    } catch (SQLException sqe) {
                        sqe.printStackTrace();
                    }
                } finally {
                    try {
                        con.close();
                    } catch (SQLException sqe) {
                        sqe.printStackTrace();
                    }
                }
            });
            //количество шары
            pool.execute(() -> {
                Connection con = getConnection();
                try {
                    PreparedStatement shareCount = con.prepareStatement(
                            "SELECT s.user_login, count(s.resource_id) " +
                                    "FROM share s " +
                                    "  WHERE s.user_login IN " + where +
                                    " GROUP BY s.user_login");
                    int i = 1;
                    for (String login : logins) {
                        shareCount.setString(i++, login);
                    }
                    ResultSet rs = shareCount.executeQuery();
                    while (rs.next()) {
                        String login = rs.getString(1);
                        AdminAnalyticsDTO adminAnalyticsDTO = data.get(login);
                        if (adminAnalyticsDTO != null) {
                            adminAnalyticsDTO.shareCount = rs.getLong(2);
                        }
                    }
                    rs.close();
                    shareCount.close();
                    con.commit();
                    latch.countDown();
                } catch (Exception e) {
                    log.error("Can't get user getUserOpCount ", e);
                    try {
                        con.rollback();
                    } catch (SQLException sqe) {
                        sqe.printStackTrace();
                    }
                } finally {
                    try {
                        con.close();
                    } catch (SQLException sqe) {
                        sqe.printStackTrace();
                    }
                }
            });
            //количество подписи
            pool.execute(() -> {
                Connection con = getConnection();
                try {
                    PreparedStatement signCount = con.prepareStatement(
                            "SELECT s.login, count(DISTINCT s.resource_id) " +
                                    "FROM sign_record s " +
                                    "WHERE s.login IN " + where +
                                    " GROUP BY s.login ");
                    int i = 1;
                    for (String login : logins) {
                        signCount.setString(i++, login);
                    }
                    ResultSet rs = signCount.executeQuery();
                    while (rs.next()) {
                        String login = rs.getString(1);
                        AdminAnalyticsDTO adminAnalyticsDTO = data.get(login);
                        if (adminAnalyticsDTO != null) {
                            adminAnalyticsDTO.signCount = rs.getLong(2);
                        }
                    }
                    rs.close();
                    signCount.close();
                    con.commit();
                    latch.countDown();
                } catch (Exception e) {
                    log.error("Can't get user getUserOpCount ", e);
                    try {
                        con.rollback();
                    } catch (SQLException sqe) {
                        sqe.printStackTrace();
                    }
                } finally {
                    try {
                        con.close();
                    } catch (SQLException sqe) {
                        sqe.printStackTrace();
                    }
                }
            });
        latch.await();
        }catch (Exception ex){
            log.error("error", ex);
        }
        return data;
    }

    int deleteUser(Connection con, UserDTO user) throws SQLException {
        PreparedStatement ps = con.prepareStatement("DELETE FROM users WHERE login = ?");
        ps.setString(1, user.getLogin());
        return ps.executeUpdate();
    }

    int deleteFriend(Connection con, UserDTO user) throws SQLException {
        PreparedStatement ps = con.prepareStatement("UPDATE users SET friends = array_remove(friends, ?::TEXT) ");
        ps.setObject(1, user.getLogin());
        return ps.executeUpdate();
    }

    public Set<String> getUserEgrpou(String login){
        Set<String> egrpous = new HashSet<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("SELECT egrpou FROM users WHERE login = ? ");
            ps.setString(1, login);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                Array array = rs.getArray(1);
                if(array!=null){
                    Collections.addAll(egrpous, (String[])array.getArray());
                }
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't get getUserEgrpou ", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return egrpous;
    }

    public Set<String> getUserWithoutRegInfo() {
        Set<String> logins = new HashSet<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT u.login " +
                            "FROM users u " +
                            "LEFT JOIN analytics_registration ar ON (u.login = ar.login) " +
                            "WHERE ar.login IS NULL ");
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                logins.add(rs.getString(1));
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't get getUserEgrpou ", e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return logins;
    }
}