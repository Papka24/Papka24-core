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

import ua.papka24.server.db.dto.UserDTO;

import java.sql.*;
import java.util.*;

public class FriendCatalogDAO extends DAO {

    private FriendCatalogDAO(){}

    private static class Singleton {
        private static final FriendCatalogDAO HOLDER_INSTANCE = new FriendCatalogDAO();
    }

    public static FriendCatalogDAO getInstance() {
        return FriendCatalogDAO.Singleton.HOLDER_INSTANCE;
    }

    // Update friends catalog on all requests
    void updateResourceByShare(UserDTO user, long resourceId, List<String> shareLogins) {
        Connection c = getConnection();
        assert c != null;
        try {
            // 1. get sign info from this resource
            PreparedStatement ps = c.prepareStatement("SELECT login, company, egrpou FROM sign_record WHERE resource_id = ?");
            ps.setLong(1, resourceId);
            ResultSet rs = ps.executeQuery();
            HashMap<Long, HashSet<String>> usersByCompany = new HashMap<>();
            HashMap<Long, String> companyList = new HashMap<>();
            while (rs.next()) {
                String userLogin = rs.getString(1);
                String cn = rs.getString(2);
                long egrpou = rs.getLong(3);
                if (egrpou > 0) {
                    companyList.put(egrpou, cn);
                    if (!usersByCompany.containsKey(egrpou)) {
                        usersByCompany.put(egrpou, new HashSet<>());
                    }
                    usersByCompany.get(egrpou).add(userLogin);
                }
            }
            // 2. If signs with companies are exist
            if (companyList.size() > 0) {
                // 3. Add self login for read friends_catalog
                shareLogins.add(user.getLogin());
                for (String sl : shareLogins) {
                    // 4. Read all friends_catalog from exist user in this resource
                    HashMap<Long, HashSet<String>> friendsCatalog = readAllFriends(c, sl);
                    HashMap<Long, HashSet<String>> newFriendsCatalog = new HashMap<>();

                    // 5. Prepare data for update friends_catalog
                    for (Long newEgrpou : usersByCompany.keySet()) {
                        if (!newFriendsCatalog.containsKey(newEgrpou)) {
                            if (friendsCatalog.containsKey(newEgrpou)) {
                                newFriendsCatalog.put(newEgrpou, friendsCatalog.get(newEgrpou));
                            } else {
                                newFriendsCatalog.put(newEgrpou, new HashSet<>());
                            }
                        }
                        usersByCompany.get(newEgrpou).stream().filter(nl -> (!friendsCatalog.containsKey(newEgrpou) || !friendsCatalog.get(newEgrpou).contains(nl)) && !sl.equals(nl)).forEach(nl -> newFriendsCatalog.get(newEgrpou).add(nl));
                    }

                    // 6. Update friends_catalog
                    updateAllFriends(c, sl, friendsCatalog, newFriendsCatalog, companyList);
                }
                c.commit();
            }
        } catch (Exception e) {
            log.error("Can't share resource {}", resourceId, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                log.error("Can't rollback", sqe);
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                log.error("Can't close session", sqe);
            }
        }
    }

    // Update friends catalog on sign resource
    void updateResourceBySign(UserDTO user, HashMap<Long, String> companyList, HashSet<String> companyLogins) {
        Connection c = getConnection();
        HashMap<Long, HashSet<String>> newFriendsCatalog = new HashMap<>();
        assert c != null;
        try {
            for (String sl : companyLogins) {
                // 4. Read all friends_catalog from exist user in this resource
                HashMap<Long, HashSet<String>> friendsCatalog = readAllFriends(c, sl);

                // 5. Prepare data for update friends_catalog
                for (Long newEgrpou : companyList.keySet()) {
                    if (!newFriendsCatalog.containsKey(newEgrpou)) {
                        if (friendsCatalog.containsKey(newEgrpou)) {
                            newFriendsCatalog.put(newEgrpou, friendsCatalog.get(newEgrpou));
                        } else {
                            newFriendsCatalog.put(newEgrpou, new HashSet<>());
                        }
                    }
                    if ((!friendsCatalog.containsKey(newEgrpou) || !friendsCatalog.get(newEgrpou).contains(user.getLogin())) && !sl.equals(user.getLogin())){
                        newFriendsCatalog.get(newEgrpou).add(user.getLogin());
                    }
                }

                // 6. Update friends_catalog
                updateAllFriends(c, sl, friendsCatalog, newFriendsCatalog, companyList);
            }
            c.commit();

        } catch (Exception e) {
            log.error("Can't update friends catalog by {} sign", user.getLogin(), e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                log.error("Can't rollback", sqe);
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                log.error("Can't close session", sqe);
            }
        }
    }

    // Read all friends_catalog from exist user in this resource
    private HashMap<Long, HashSet<String>> readAllFriends(Connection c, String login) throws SQLException {
        PreparedStatement ps = c.prepareStatement("SELECT egrpou, logins FROM friends_catalog WHERE login = ?");
        ps.setString(1, login);
        ResultSet rs = ps.executeQuery();
        HashMap<Long, HashSet<String>> friendsCatalog = new HashMap<>();
        while (rs.next()) {
            long egrpou = rs.getLong(1);
            if (egrpou > 0) {
                Array arr = rs.getArray(2);
                if (arr != null) {
                    if (!friendsCatalog.containsKey(egrpou)) {
                        friendsCatalog.put(egrpou, new HashSet<>());
                    }
                    for (String l : (String[]) arr.getArray()) {
                        friendsCatalog.get(egrpou).add(l);
                    }
                }
            }
        }
        return friendsCatalog;
    }

    // Update friends_catalog
    private void updateAllFriends(Connection c, String login, HashMap<Long, HashSet<String>> friendsCatalog, HashMap<Long, HashSet<String>> newFriendsCatalog, HashMap<Long,String> companyList) throws SQLException {
        PreparedStatement ps;
        for (Long egrpou : newFriendsCatalog.keySet()) {
            HashSet<String> friends = newFriendsCatalog.get(egrpou);
            if (friendsCatalog.containsKey(egrpou)) {
                if (friends.size() > friendsCatalog.size()) {
                    // Update exist record
                    ps = c.prepareStatement("UPDATE friends_catalog SET logins = ?  WHERE login = ? AND egrpou = ?");
                    ps.setArray(1, c.createArrayOf("text", friends.toArray()));
                    ps.setString(2, login);
                    ps.setLong(3, egrpou);
                    ps.executeUpdate();
                }
            } else if (friends.size() > 0) {
                // Insert new record
                ps = c.prepareStatement("INSERT INTO friends_catalog (login, egrpou, name, logins) VALUES (?,?,?,?)");
                ps.setString(1, login);
                ps.setLong(2, egrpou);
                ps.setString(3, companyList.get(egrpou));
                ps.setArray(4, c.createArrayOf("text", friends.toArray()));
                ps.executeUpdate();
            }
        }
    }

    public int deleteUser(Connection con, UserDTO user) throws SQLException {
        PreparedStatement ps = con.prepareStatement("DELETE FROM friends_catalog WHERE login = ? ");
        ps.setString(1, user.getLogin());
        return ps.executeUpdate();
    }
}
