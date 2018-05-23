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

import freemarker.template.TemplateNotFoundException;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.lang3.StringUtils;
import ua.papka24.server.api.DTO.ResourceStatisticRequestDTO;
import ua.papka24.server.api.DTO.ShareAllResultDTO;
import ua.papka24.server.db.dto.*;
import ua.papka24.server.db.redis.RedisDAOManager;
import ua.papka24.server.security.CryptoManager;
import ua.papka24.server.service.events.EventManager;
import ua.papka24.server.service.events.main.data.Notification;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.ResourceStatisticResponseDTO;
import ua.papka24.server.api.DTO.ShareResDTO;
import ua.papka24.server.api.helper.EmailHelper;
import ua.papka24.server.db.dao.helper.SearchPreparedStatementV1;
import ua.papka24.server.db.dao.helper.SearchPreparedStatementV2;
import ua.papka24.server.service.billing.BillingQueryManager;
import ua.papka24.server.service.events.EmailNotifier;
import ua.papka24.server.service.events.event.ResourceChangeEvent;
import ua.papka24.server.service.events.event.ResourcesChangeEvent;
import ua.papka24.server.service.events.main.EventType;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.sql.*;
import java.util.*;
import java.util.Date;
import java.util.concurrent.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;


public class ResourceDAO extends DAO {

    public static Long SUPER_ADMIN = -1L;
    private ExpiringMap<String, Set<Long>> allowedToUserResources = ExpiringMap.builder()
            .expiration(1, TimeUnit.MINUTES).expirationPolicy(ExpirationPolicy.CREATED).build();
    private static final ExecutorService pool = Executors.newCachedThreadPool();
    private ExpiringMap<Long, Set<String>> alreadySharedCache = ExpiringMap.builder()
            .expiration(10, TimeUnit.SECONDS).expirationPolicy(ExpirationPolicy.CREATED).build();

    private ResourceDAO(){}

    private static class Singleton {
        private static final ResourceDAO HOLDER_INSTANCE = new ResourceDAO();
    }

    public static ResourceDAO getInstance() {
        return ResourceDAO.Singleton.HOLDER_INSTANCE;
    }

    public ResourceDTO copy(String hash, String fileName, UserDTO user, List<String> signs, String src, boolean bot){
        Connection c = getConnection();
        String path = null;
        Integer size = null;
        Integer type = null;
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("SELECT r.src, r.size, r.type FROM resource AS r WHERE r.hash = ? LIMIT 1");
            ps.setString(1, hash);

            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                path = rs.getString(1);
                size = rs.getInt(2);
                type = rs.getInt(3);
            } else {
                return null;
            }
        } catch (SQLException e) {
            log.error("Can't get resource with id = {}", hash, e);
        } finally {
            try {
                c.commit();
            } catch (Exception ex) {
                log.error("fail commit", ex);
            }
            try {
                c.close();
            } catch (Exception ex) {
                log.error("fail close", ex);
            }
        }
        if(path!=null && hash!=null) {
            Path filePath = Paths.get(Main.CDNPath, path, hash);
            if (!Files.exists(filePath)) {
                log.info("file not exists, fix src path to {}", src);
                changeResourceSrc(hash, src);
                path = null;
            }
        }
        if (path == null || size == null || type == null) {
            return null;
        }
        return create(hash, path, fileName, user, type, size, signs, bot);
    }

    private int changeResourceSrc(String hash, String src){
        int ints = 0;
        Connection con = getConnection();
        try{
            PreparedStatement hashPs = con.prepareStatement("update resource set src = ? where hash = ? ");
            hashPs.setString(1,src);
            hashPs.setString(2,hash);
            ints = hashPs.executeUpdate();
            hashPs.close();

            hashPs = con.prepareStatement(
                    "UPDATE resource_cache SET src = ? where hash = ?");
            hashPs.setString(1, src);
            hashPs.setString(2, hash);
            hashPs.close();

        } catch (SQLException e) {
            try {
                con.rollback();
            } catch (SQLException e1) {
                log.error("fail rollback", e);
            }
            log.error("Can't change src resource", e);
        } finally {
            try {
                con.commit();
            } catch (Exception ex) {
                log.error("fail commit", ex);
            }
            try {
                con.close();
            } catch (Exception ex) {
                log.error("fail close", ex);
            }
        }
        return ints;
    }

    public ResourceDTO create(String hash, String path, String name, UserDTO user, int type, long size, List<String> signs, boolean bot) {
        ResourceDTO result = null;
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("INSERT INTO resource (hash, src, name, type, size, time, author, status, company_id, bot) VALUES (?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);

            ResourceDTO res = new ResourceDTO(hash, path, name, type, size, user.getLogin(), user.getCompanyId());
            long time = (new Date()).getTime();
            res.setTime(time);
            ps.setString(1, res.getHash());
            ps.setString(2, res.getSrc());
            ps.setString(3, res.getName());
            ps.setLong(4, res.getType());
            ps.setLong(5, res.getSize());
            ps.setLong(6, time);
            ps.setString(7, res.getAuthor());
            ps.setInt(8, res.getStatus());
            if(res.getCompanyId() != null && res.getCompanyId() > 0) {
                ps.setLong(9, res.getCompanyId());
            }else{
                ps.setNull(9, Types.BIGINT);
            }
            ps.setBoolean(10, bot);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("Creating new resources failed, no rows affected.");
            }
            ResultSet rs = ps.getGeneratedKeys();
            if (rs != null && rs.next()) {
                res.setId(rs.getLong("id"));
                result = res;
                log.info("create resource:{}:{}",result.getId(),result.getHash());
            }

            user.setHeavyWeight(checkHeavyStatus(c,user.getLogin()));

            if (signs != null && signs.size() > 0) {
                // TODO: check saving signs
                ps = c.prepareStatement("UPDATE resource SET signs = ? WHERE resource.id = ?");
                ps.setArray(1, c.createArrayOf("varchar", signs.toArray()));
                ps.setLong(2, res.getId());
                ps.executeUpdate();
            }

            ps.close();
            //resource (hash, src, name, type, size, time, author, status, company_id)
            ps = c.prepareStatement("INSERT INTO resource_cache (owner, id, hash, src, name, type, size, time, author, status, company_id, created) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
            ps.setString(1, res.getAuthor());
            ps.setLong(2, res.getId());
            ps.setString(3, res.getHash());
            ps.setString(4, res.getSrc());
            ps.setString(5, res.getName());
            ps.setLong(6, res.getType());
            ps.setLong(7, res.getSize());
            ps.setLong(8, time);
            ps.setString(9, res.getAuthor());
            ps.setInt(10, res.getStatus());
            if(res.getCompanyId() != null && res.getCompanyId() > 0) {
                ps.setLong(11, res.getCompanyId());
            }else{
                ps.setNull(11, Types.BIGINT);
            }
            ps.setBoolean(12, Boolean.TRUE);
            ps.executeUpdate();

            c.commit();
            ResourcesChangeEvent wssNotification = Notification.builder().eventType(EventType.CREATE).userLogin(user.getLogin()).id(res.getId()).resource(res).createWSSNotification();
            EventManager.getInstance().addNotification(wssNotification);
        } catch (Exception e) {
            log.error("Can't create resource with hash = {}", hash, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }

    private int getResourceCount(Connection c, String userLogin) throws SQLException {
        int resourceCount = 0;
        userLogin = userLogin.toLowerCase();
        PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM resource WHERE author = ? AND status < ?");
        ps.setString(1, userLogin);
        ps.setInt(2, ResourceDTO.STATUS_DELETED);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            resourceCount = rs.getInt("count");
        }
        ps = c.prepareStatement("SELECT count(*) FROM share s WHERE s.user_login = ?");
        ps.setString(1, userLogin);
        rs = ps.executeQuery();
        if (rs.next()) {
            resourceCount += rs.getInt("count");
        }
        return resourceCount;
    }

    private boolean checkHeavyStatus(Connection con, String userLogin){
        try {
            userLogin = userLogin.toLowerCase();
            int resourceCount = getResourceCount(con, userLogin);
            int heavyWeight = Integer.valueOf(Main.property.getProperty("heavyWeight", "1000"));
            PreparedStatement ps;
            if (resourceCount >= heavyWeight) {
                ps = con.prepareStatement("UPDATE users SET heavy = TRUE WHERE login = ?");
                ps.setString(1, userLogin);
                ps.executeUpdate();
                return true;
            }else{
                ps = con.prepareStatement("UPDATE users SET heavy = FALSE WHERE login = ?");
                ps.setString(1, userLogin);
                ps.executeUpdate();
                return false;
            }
        } catch (Exception ex) {
            log.warn("error check heavyWeight", ex);
        }
        return true;
    }

    /**
     * Получаем документ
     * @param login
     * @param id
     * @return
     */
    public ResourceDTO getWithSign(String login, Long id, UserDTO user) {
        ResourceDTO result = null;
        log.info("getWithSign login:{}\tid:{}", login, id);
        if (!checkResourceAccess(login, id, user)) {
            return null;
        }
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("SELECT r.id, r.src, r.hash, r.signs FROM resource AS r WHERE r.id = ?");
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long resId = rs.getLong(1);
                String src = rs.getString(2);
                String hash = rs.getString(3);
                Array signs = rs.getArray(4);
                if (signs==null){
                    result = new ResourceDTO(resId, src, hash,null);
                } else {
                    result = new ResourceDTO(resId, src, hash, (String[]) signs.getArray());
                }

            }
            c.commit();

        } catch (Exception e) {
            log.error("Can't get resource with id = {}", id, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }
    /**
     * Вернуть документ по id
     * @param login
     * @param id
     * @return
     */
    public ResourceDTO getResource(UserDTO login, Long id) {
        Long bossCompanyId = (login.getCompanyDTO()!=null && login.getCompanyId()!=null && login.getCompanyDTO().haveBoss(login.getLogin()))?login.getCompanyId():0;
        return getResource(login ,id, bossCompanyId);
    }
    /**
     * Вернуть документ по id
     * @param user
     * @param id
     * @param loginBossCompanyId - ид комании инициатора, если = -1 супер админ доступ, без проверки прав доступа
     * @return
     */
    public ResourceDTO getResource(UserDTO user, Long id, Long loginBossCompanyId) {
        ResourceDTO result = null;
        String login = user.getLogin();
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement(
                    "SELECT r.id, r.hash, r.src, r.name, r.type, r.size, r.time, r.author, r.status, r.tags, r.signed, r.company_id " +
                    "FROM resource AS r WHERE r.id = ?");
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result = new ResourceDTO(rs);
                if(result.getStatus()>=20 && result.getAuthor().equals(login)){
                    return null;
                }
                //если запрашивает не автор - то статус и теги документа проставляем от шары
                if (!result.getAuthor().equals(login) || (result.getCompanyId()!=null && !result.getCompanyId().equals(user.getCompanyId())) ) {
                    // Наш юзер не автор или не имеет доступа к документу, продолжаем искать в шарах его
                    ps = c.prepareStatement("SELECT s.status, s.tags, s.time, s.company_id FROM share AS s WHERE s.resource_id = ? AND s.user_login = ?");
                    ps.setLong(1, id);
                    ps.setString(2, login);
                    rs = ps.executeQuery();
                    if (rs.next() && user.theSameCompanyId(rs.getLong(4))) {
                        result.setStatus(rs.getInt(1));
                        result.setTags(rs.getLong(2));
                        result.setTime(rs.getLong(3));
                        result.setSigned(result.getStatus() % 10 == 3);
                    } else if (loginBossCompanyId>0) {
                        // Если не нашли в шарах, ищем шару с такой же id компании как у пользователя (для админа)
                        ps = c.prepareStatement("SELECT s.status, s.tags, s.time FROM share AS s WHERE s.resource_id = ? AND s.company_id = ?");
                        ps.setLong(1, id);
                        ps.setLong(2, loginBossCompanyId);
                        rs = ps.executeQuery();
                        if (rs.next()) {
                            result.setStatus(rs.getInt(1));
                            result.setTags(rs.getLong(2));
                            result.setTime(rs.getLong(3));
                            result.setSigned(result.getStatus() % 10 == 3);
                        }else if (!loginBossCompanyId.equals(result.getCompanyId())){
                            result = null;
                        }
                    } else if (!ResourceDAO.SUPER_ADMIN.equals(loginBossCompanyId)) {
                        // !Либо ресурс автора не в той же комании либо запрос от подсистемы не с правами суперадми
                        // Условия не удовлетворены возвращает пустой ресурс
                        result = null;
                    }
                }
                if (result != null){
                    // список тех кому расшарен документ
                    ps = c.prepareStatement("SELECT s.user_login, s.status FROM share AS s WHERE s.resource_id = ?");
                    ps.setLong(1, id);
                    rs = ps.executeQuery();
                    HashMap<String, Integer> shares = new HashMap<>();
                    while (rs.next()) {
                        shares.put(rs.getString(1), rs.getInt(2));
                    }
                    result.setShares(shares);
                }
            }
            c.commit();
        } catch (Exception e) {
            result = null;
            log.error("Can't get resource with id = {}", id, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }

    /**
     * Получение документа по hash
     * @param hash
     * @return
     */
    public ResourceDTO get(String hash) {
        ResourceDTO result = null;
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("SELECT r.id, r.hash, r.src, r.name, r.type, r.size, r.time, r.author, r.status as res_status, r.tags, r.signed, r.company_id FROM resource AS r WHERE r.hash = ?");
            ps.setString(1, hash);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result = new ResourceDTO(rs);
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't get resource with hash = {}", hash, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }

    public int rename(UserDTO user, Long id, String newName) {
        Connection c = getConnection();
        int result = 0;
        assert c != null;
        try {
            PreparedStatement ps;
            ps = c.prepareStatement("SELECT r.author FROM resource AS r WHERE r.id = ?");
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                if (!rs.getString(1).equals(user.getLogin())) {
                    return -1;
                }
            } else {
                return result;
            }

            ps = c.prepareStatement("UPDATE resource AS r SET name = ? WHERE r.id = ?");
            ps.setString(1, newName);
            ps.setLong(2, id);
            result = ps.executeUpdate();
            if (result == 0) {
                c.rollback();
            }
            ps.close();

            ps = c.prepareStatement(
                    "UPDATE resource_cache SET name = ? WHERE id = ? ");
            ps.setString(1, newName);
            ps.setLong(2, id);
            ps.executeUpdate();

            c.commit();
        } catch (Exception e) {
            log.error("Can't update name of resource {}", id, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }


    public int deleteAll(UserDTO user, List<Long> ids) {
        int result = 0;
        for (long id : ids) {
            result += delete(user, id);
        }
        return result;
    }

    public int delete(String userLogin, Long id) {
        int res = -1;
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("DELETE FROM resource WHERE resource.id = ? and author = ? ");
            ps.setLong(1,id);
            ps.setString(2, userLogin);
            res = ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("DELETE FROM resource_cache WHERE owner = ?  and id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10) ");
            ps.setString(1, userLogin);
            ps.setLong(2, id);
            ps.setString(3, userLogin);
            ps.executeUpdate();

            con.commit();
        } catch (Exception e) {
            log.error("Can't delete resource with id = {}", id, e);
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
        return res;
    }

    public int delete(UserDTO user, Long id) {
        int result = 0;
        Connection c = getConnection();
        String hash;
        String src;
        String author;
        boolean needDeleteFile = false;
        int status;
        int shareStatus;
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("SELECT r.hash, r.src, r.status, r.author FROM resource AS r WHERE r.id = ?");
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                hash = rs.getString(1);
                src = rs.getString(2);
                status = rs.getInt(3);
                author = rs.getString(4);
            } else {
                return 0;
            }
            String login = user.getLogin();
            if (!login.equals(author)) {
                // Not own resources
                ps = c.prepareStatement("SELECT s.status FROM share AS s WHERE s.resource_id = ? AND s.user_login = ?");
                ps.setLong(1, id);
                ps.setString(2, login);
                rs = ps.executeQuery();
                if (rs.next()) {
                    shareStatus = rs.getInt(1);

                    if (shareStatus < ShareDTO.STATUS_IN_TRASH) {
                        ps = c.prepareStatement("UPDATE share SET status = ? WHERE share.resource_id = ? AND share.user_login = ? ");
                        ps.setInt(1, ShareDTO.STATUS_IN_TRASH + shareStatus);
                        ps.setLong(2, id);
                        ps.setString(3, login);
                        result = ps.executeUpdate();
                        ps.close();

                        ps = c.prepareStatement(
                                "UPDATE resource_cache SET status = ? WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                        ps.setInt(1, ShareDTO.STATUS_IN_TRASH + shareStatus);
                        ps.setString(2, login);
                        ps.setLong(3, id);
                        ps.setString(4, login);
                        ps.executeUpdate();
                    } else {
                        ps = c.prepareStatement("DELETE FROM share WHERE share.resource_id = ? AND share.user_login = ?");
                        ps.setLong(1, id);
                        ps.setString(2, login);
                        result = ps.executeUpdate();
                        ps.close();

                        ps = c.prepareStatement("DELETE FROM resource_cache WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                        ps.setString(1, login);
                        ps.setLong(2, id);
                        ps.setString(3, login);
                        ps.executeUpdate();

                        ResourceChangeEvent del = Notification.builder().eventType(EventType.DELETE_NOT_OWN).eventData("del").userLogin(login).id(id).createResourceChangeNotification();
                        EventManager.getInstance().addNotification(del);
                    }

                    if (shareStatus == ShareDTO.STATUS_IN_TRASH && status == ResourceDTO.STATUS_DELETED) {
                        // Maybe need delete resource
                        ps = c.prepareStatement("SELECT count(*) FROM share AS s WHERE s.resource_id = ?");
                        ps.setLong(1, id);
                        rs = ps.executeQuery();
                        if (rs.next() && rs.getInt(1) == 0) {
                            needDeleteFile = true;
                        }
                    }
                } else {
                    return 0;
                }
            } else {
                // Author of resource
                if (status < ResourceDTO.STATUS_IN_TRASH) {
                    ps = c.prepareStatement("UPDATE resource SET status = ? WHERE resource.id = ?");
                    ps.setInt(1, status + ResourceDTO.STATUS_IN_TRASH);
                    ps.setLong(2, id);
                    result = ps.executeUpdate();
                    ps.close();

                    ps = c.prepareStatement(
                            "UPDATE resource_cache SET status = ? WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                    ps.setInt(1, status + ResourceDTO.STATUS_IN_TRASH);
                    ps.setString(2, login);
                    ps.setLong(3, id);
                    ps.setString(4, login);
                    ps.executeUpdate();

                } else {
                    ps = c.prepareStatement("SELECT count(*) FROM share AS s WHERE s.resource_id = ?");
                    ps.setLong(1, id);
                    rs = ps.executeQuery();
                    if (rs.next() && rs.getInt(1) > 0) {
                        // Has link on share request
                        ps = c.prepareStatement("UPDATE resource SET status = ? WHERE resource.id = ?");
                        ps.setInt(1, ResourceDTO.STATUS_DELETED);
                        ps.setLong(2, id);
                        result = ps.executeUpdate();

                        ps = c.prepareStatement(
                                "DELETE FROM resource_cache WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                        ps.setString(1, login);
                        ps.setLong(2, id);
                        ps.setString(3, login);
                        ps.executeUpdate();

                        ps = c.prepareStatement(
                                "UPDATE resource_cache SET delete_by_creator = TRUE WHERE NOT owner = ? AND id = ?");//? perf
                        ps.setString(1, login);
                        ps.setLong(2, id);
                        ps.executeUpdate();
                    } else {
                        needDeleteFile = true;
                    }
                    ResourceChangeEvent del = Notification.builder().eventType(EventType.DELETE).eventData("del").userLogin(login).id(id).createResourceChangeNotification();
                    EventManager.getInstance().addNotification(del);
                }
            }

            if (needDeleteFile) {
                // Resource without share
                ps = c.prepareStatement("DELETE FROM resource WHERE resource.id = ?");
                ps.setLong(1, id);
                result = ps.executeUpdate();
                ps.close();

                ps = c.prepareStatement("DELETE FROM resource_cache WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                ps.setString(1, login);
                ps.setLong(2, id);
                ps.setString(3, login);
                ps.executeUpdate();

                ps = c.prepareStatement("SELECT count(*) FROM resource AS r WHERE r.hash = ?");
                ps.setString(1, hash);
                rs = ps.executeQuery();
                rs.next();
                if (rs.getInt(1) == 0) {
                    // Alone resource, need to delete file
                    File file = Paths.get(Main.CDNPath, src, hash).toFile();
                    File filePng = Paths.get(Main.CDNPath, src, hash + Main.property.getProperty("pngPrefix")).toFile();

                    if (filePng.exists() && !filePng.delete()) {
                        c.rollback();
                        log.error("Can't delete PDF and image from resource {}", file.getPath());
                        return result;
                    }

                    if (!file.delete()) {
                        c.rollback();
                        log.error("Can't delete PDF from resource {}", file.getPath());
                        return result;
                    }
                }
            }
            user.setHeavyWeight(checkHeavyStatus(c, login));
            c.commit();
        } catch (Exception e) {
            log.error("Can't delete resource with id = {}", id, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }

    public int restoreAll(UserDTO user, List<Long> ids) {
        int result = 0;
        for (long id : ids) {
            result += restore(user, id);
        }
        return result;
    }

    public int restore(UserDTO user, Long id) {
        int result = 0;
        Connection c = getConnection();
        String author;
        int status;
        int shareStatus;
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("SELECT r.status, r.author FROM resource AS r WHERE r.id = ?");
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                status = rs.getInt(1);
                author = rs.getString(2);
            } else {
                return 0;
            }

            if (!user.getLogin().equals(author)) {
                // Not own resources
                ps = c.prepareStatement("SELECT s.status FROM share AS s WHERE s.resource_id = ? AND s.user_login = ?");
                ps.setLong(1, id);
                ps.setString(2, user.getLogin());
                rs = ps.executeQuery();
                if (rs.next()) {
                    shareStatus = rs.getInt(1);
                    if (shareStatus >= ShareDTO.STATUS_IN_TRASH) {
                        ps = c.prepareStatement("UPDATE share SET status = ? WHERE share.resource_id = ? AND share.user_login = ? ");
                        ps.setInt(1, shareStatus - ShareDTO.STATUS_IN_TRASH);
                        ps.setLong(2, id);
                        ps.setString(3, user.getLogin());
                        result = ps.executeUpdate();
                        ps.close();

                        ps = c.prepareStatement(
                                "UPDATE resource_cache SET status = ? WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                        ps.setInt(1, shareStatus - ShareDTO.STATUS_IN_TRASH);
                        ps.setString(2, user.getLogin());
                        ps.setLong(3, id);
                        ps.setString(4, user.getLogin());
                        ps.executeUpdate();
                    }
                } else {
                    return -1;
                }
            } else {
                // Author of resource
                if (status >= ResourceDTO.STATUS_IN_TRASH) {
                    ps = c.prepareStatement("UPDATE resource SET status = ? WHERE resource.id = ?");
                    ps.setInt(1, status - ResourceDTO.STATUS_IN_TRASH);
                    ps.setLong(2, id);
                    result = ps.executeUpdate();

                    ps = c.prepareStatement(
                            "UPDATE resource_cache SET status = ? WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                    ps.setInt(1, status - ResourceDTO.STATUS_IN_TRASH);
                    ps.setString(2, user.getLogin());
                    ps.setLong(3, id);
                    ps.setString(4, user.getLogin());
                    ps.executeUpdate();
                }
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't delete resource with id = {}", id, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }

    /**
     * получение расшаренных документов и автоматическая простановка статус для шарингового документа о том что он просмотрен пользователем
     * @param user
     * @param id
     * @return
     */
    public List<ShareDTO> getShare(String user, Long id) {
        return getShare(user,id,true, null);
    }

    /**
     * получение расшаренных документов и простановка статус для шарингового документа о том что он просмотрен пользователем
     * @param user
     * @param id
     * @return
     */

    public ArrayList<ShareDTO> getShare(String user, Long id, boolean setSawStatus, UserDTO boss) {
        return getShare(user, id, setSawStatus, boss, false);
    }

    public ArrayList<ShareDTO> getShare(String user, Long id, boolean setSawStatus, UserDTO boss, boolean ignoreCheck) {
        //проверка что тот кто запрашивает информацию имеет право это делать
        // ignoreCheck - в случае системного обращения. используется в оповещениях по веб сокетам
        // (возможна ситуация что документ уже удален, и тогда проверка не сработает
        if(!ignoreCheck){
            if(!checkResourceAccess(user, id, boss)){
                return null;
            }
        }
        Connection c = getConnection();
        ArrayList<ShareDTO> result = new ArrayList<>();
        assert c != null;
        try {
            PreparedStatement ps;
            ResultSet rs;
            ps = c.prepareStatement("SELECT s.user_login, s.status, s.time, s.tags FROM share AS s WHERE s.resource_id = ?");
            ps.setLong(1, id);
            rs = ps.executeQuery();
            int status = -1;
            while (rs.next()) {
                ShareDTO share = new ShareDTO(id, rs.getString(1), rs.getInt(2), rs.getLong(3), rs.getLong(4));
                if (share.getUser().equals(user)) {
                    status = share.getStatus();
                }
                result.add(share);
            }

            if(setSawStatus) {
                if (status != -1 && status % ShareDTO.STATUS_IN_TRASH < ShareDTO.STATUS_VIEW_BY_USERS) {
                    ps = c.prepareStatement("UPDATE share SET status = ? WHERE share.resource_id = ? AND share.user_login = ?");
                    ps.setInt(1, status <= ShareDTO.STATUS_IN_TRASH ? ShareDTO.STATUS_VIEW_BY_USERS : ShareDTO.STATUS_IN_TRASH + ShareDTO.STATUS_VIEW_BY_USERS);
                    ps.setLong(2, id);
                    ps.setString(3, user);
                    ps.executeUpdate();
                    ps.close();

                    ps = c.prepareStatement(
                            "UPDATE resource_cache SET status = ? WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                    ps.setInt(1, status <= ShareDTO.STATUS_IN_TRASH ? ShareDTO.STATUS_VIEW_BY_USERS : ShareDTO.STATUS_IN_TRASH + ShareDTO.STATUS_VIEW_BY_USERS);
                    ps.setString(2, user);
                    ps.setLong(3, id);
                    ps.setString(4, user);
                    ps.executeUpdate();

                    Notification.Builder builder = Notification.builder().eventType(EventType.VIEWED).userLogin(user).id(id).eventData("");
                    EventManager.getInstance().addNotification(builder.createWSSNotification());
                    EventManager.getInstance().addNotification(builder.createResourceChangeNotification());
                }
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't get sharing resource {}", id, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }

    /**
     * Share res with list of users. If users doesn't exist, send invitation, if it's email.
     *
     * @param user        - owner of request
     * @param resourceId  - resource id
     * @param requestList - list of login for sharing
     * @return - list of valid and new logins
     */
    public List<ShareDTO> share(UserDTO user, Long resourceId, List<ShareResDTO> requestList) {
        if (requestList == null) {
            return null;
        }
        requestList = requestList.stream().filter(e -> EmailHelper.validate(e.getEmail())).collect(Collectors.toList());
        if (requestList.isEmpty()) {
            return null;
        }
        if(!checkResourceAccess(user.getLogin(), resourceId, user)){
            return null;
        }

        requestList = filterAlreadyShared(resourceId, requestList);
        if(requestList.isEmpty()){
            return null;
        }

        requestList = requestList.stream().distinct().collect(Collectors.toList());
        List<ShareDTO> result = new ArrayList<>();
        String userName = user.getLogin();
        String docName = "";

        List<String> shareLogins;

        Connection c = getConnection();
        assert c != null;
        try {
            Set<String> oldUsers = new HashSet<>();
            PreparedStatement ps;
            ResultSet rs;
            // Detect if it's author of resource
            ps = c.prepareStatement("SELECT r.author, r.name, s.user_login, r.status, s.status as share_status, s.time, r.tags FROM resource r LEFT JOIN share s ON (r.id = s.resource_id) WHERE r.id = ?");
            ps.setLong(1, resourceId);
            rs = ps.executeQuery();
            long docStatus = 0;

            boolean firstLine = true;
            while (rs.next()) {
                if (firstLine) {
                    oldUsers.add(rs.getString(1).toLowerCase().trim());
                    docName = rs.getString(2);
                    docStatus = rs.getLong(4);
                    firstLine = false;
                }
                String sharedUser = rs.getString(3);
                oldUsers.add(sharedUser);
                if(sharedUser!=null) {
                    result.add(new ShareDTO(resourceId, sharedUser.toLowerCase(), rs.getInt(5), rs.getLong(6), rs.getLong(7)));
                }
            }
            requestList = requestList.stream().distinct().filter(l -> !oldUsers.contains(l.getEmail())).collect(Collectors.toList());
            shareLogins = requestList.stream()
                    .collect(Collector.of(ArrayList<String>::new, (a, e) -> a.add(e.getEmail()), (l1, l2) -> {
                        l1.addAll(l2);
                        return l1;
                    }));

            if (shareLogins == null || shareLogins.size() == 0) {
                return result;
            }

            if (docStatus < ResourceDTO.STATUS_SHARED) {
                ps = c.prepareStatement("UPDATE resource SET status = ? WHERE resource.id = ?");
                ps.setInt(1, ResourceDTO.STATUS_SHARED);
                ps.setLong(2, resourceId);
                ps.executeUpdate();
                ps.close();

                ps = c.prepareStatement(
                        "UPDATE resource_cache SET status = ? WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                ps.setInt(1, ResourceDTO.STATUS_SHARED);
                ps.setString(2, user.getLogin());
                ps.setLong(3, resourceId);
                ps.setString(4, user.getLogin());
                ps.executeUpdate();
            }

            // update friends name
            if (user.getFriends() == null) {
                user.setFriends(new HashMap<>());
            }
            List<String> newFriends = new ArrayList<>();
            shareLogins.stream().filter(l -> user.getFriends().get(l) == null && !l.equals(userName)).forEach(l -> {
                newFriends.add(l);
                user.getFriends().put(l, "");
            });

            if (newFriends.size() > 0) {
                for (String l : newFriends) {
                    if (!l.equals(userName)) {
                        // append themself to user list
                        ps = c.prepareStatement("UPDATE users AS u SET friends = array_append(u.friends,?::TEXT) WHERE u.login = ? AND ( NOT u.friends && ? OR u.friends ISNULL )");
                        ps.setString(1, l);
                        ps.setString(2, userName);
                        ps.setArray(3, c.createArrayOf("text", new String[]{l}));
                        ps.executeUpdate();
                    }
                }

                Map<String, String> friendList = user.getFriends();

                StringBuilder builder = new StringBuilder();
                for (int i = newFriends.size(); i > 0; i--) {
                    builder.append("?,");
                }
                ps = c.prepareStatement("SELECT u.login, u.full_name FROM users AS u WHERE u.login IN (" + builder.deleteCharAt(builder.length() - 1).toString() + ")");
                for (int i = newFriends.size(); i > 0; i--) {
                    ps.setString(i, newFriends.get(i - 1));
                }
                rs = ps.executeQuery();
                while (rs.next()) {
                    friendList.put(rs.getString(1), rs.getString(2));
                }
            }
            //обновление списка друзей для третьих лиц. всем кто имеет отношение к ресурсу
            ps = c.prepareStatement(
                    "SELECT author " +
                    "FROM resource WHERE id = ? " +
                    "UNION " +
                    "SELECT user_login " +
                    "FROM share WHERE resource_id = ? ");
            ps.setLong(1, resourceId);
            ps.setLong(2, resourceId);
            rs = ps.executeQuery();
            PreparedStatement statement = c.prepareStatement("UPDATE users AS u SET friends = array_append(u.friends,?::TEXT) WHERE u.login = ? AND ( NOT u.friends && ? OR u.friends ISNULL )");
            while(rs.next()){
                String userLogin = rs.getString(1);
                requestList.forEach(e->{
                    try {
                        statement.setString(1, e.getEmail());
                        statement.setString(2, userLogin);
                        statement.setArray(3, c.createArrayOf("text", new String[]{e.getEmail()}));
                        statement.executeUpdate();
                    }catch (Exception ex){
                        log.error("error update friend list",ex);
                    }
                });
            }
            requestList.stream().filter(e->!e.getEmail().equals(user.getLogin())).forEach(e-> RedisDAOManager.getInstance().markChanged(e.getEmail()));
            //-----------------------------
            List<String> existUser = new ArrayList<>();
            StringBuilder builder = new StringBuilder();
            for (int i = shareLogins.size(); i > 0; i--) {
                builder.append("?,");
            }
            ps = c.prepareStatement("SELECT u.login FROM users AS u WHERE u.login IN (" + builder.deleteCharAt(builder.length() - 1).toString() + ")");
            for (int i = shareLogins.size(); i > 0; i--) {
                ps.setString(i, shareLogins.get(i - 1));
            }
            rs = ps.executeQuery();
            while (rs.next()) {
                String existLogin = rs.getString(1);
                if (!userName.equals(existLogin)) {
                    existUser.add(existLogin);
                }
            }
            // Filter email from non exist users
            List<ShareResDTO> existsUserList = requestList.stream().filter(e -> existUser.contains(e.getEmail()))
                    .collect(Collector.of(ArrayList<ShareResDTO>::new, ArrayList::add, (l1, l2) -> {
                        l1.addAll(l2);
                        return l1;
                    }));
            List<ShareResDTO> nonExistsUserList = requestList.stream().filter(e -> !existsUserList.contains(e))
                    .collect(Collectors.toList());
            ps = c.prepareStatement(
                    "INSERT INTO share (resource_id, user_login, status, time, spam_mode, company_id, initiator, comment) " +
                            "(SELECT ?,?,?,?,?,?,?,? " +
                            "WHERE NOT exists(SELECT 1 FROM share WHERE resource_id = ? AND user_login = ? )) ");
            PreparedStatement resourceCache = c.prepareStatement(
                    "INSERT INTO resource_cache(owner, id, hash, src, name, type, size, time, author, status, company_id, created, signed) " +
                            " (SELECT ?,?,?,?,?,?,?,?,?,?,?,?,? " +
                            " WHERE NOT EXISTS (select 1 from resource_cache where owner = ? and id = ? and abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10 ))");

            // Add new request for exist users
            for (ShareResDTO shareRes : existsUserList) {
                String email = shareRes.getEmail();
                UserDTO shareUser = UserDTO.load(email);
                int shareStatus = ShareDTO.STATUS_SEND_TO_KNOWN_USER;
                PreparedStatement checkAlreadySigned = c.prepareStatement(
                        "SELECT 1 FROM sign_record WHERE resource_id = ? AND login = ?");
                checkAlreadySigned.setLong(1, resourceId);
                checkAlreadySigned.setString(2, email);
                ResultSet checkAlreadySignedRs = checkAlreadySigned.executeQuery();
                if(checkAlreadySignedRs.next()){
                    shareStatus = ShareDTO.STATUS_SIGNED_BY_USERS;
                }

                long createTime = new Date().getTime();
                ps.setLong(1, resourceId);
                ps.setString(2, email);
                ps.setInt(3, shareStatus);
                ps.setLong(4, createTime);
                ps.setInt(5, shareRes.getMode());
                if (shareUser.getCompanyId() != null && shareUser.getCompanyId() > 0) {
                    ps.setLong(6, shareUser.getCompanyId());
                } else {
                    ps.setNull(6, Types.BIGINT);
                }
                ps.setString(7, user.getLogin());
                ps.setString(8, shareRes.getComment());

                ps.setLong(9, resourceId);
                ps.setString(10, email);
                int res = ps.executeUpdate();
                if (res > 0) {
                    //todo перенести оповещение в конец. после успешного коммита
                    EmailNotifier.getInstance().notifyEmail(EventType.SHARED, userName, email, resourceId, docName, user.getFullName(), false, shareRes.getComment(), shareRes.getTemplate());
                    result.add(new ShareDTO(resourceId, email, ShareDTO.STATUS_SEND_TO_KNOWN_USER, createTime, 0));
                    checkHeavyStatus(c, email);
                }

                String hash = null, src = null, name = null, author = null;
                int type = -1, size = -1;
                PreparedStatement resourceInfo = c.prepareStatement(
                        "SELECT hash, src, name, type, size, author FROM resource WHERE id = ?");
                resourceInfo.setLong(1, resourceId);
                ResultSet resultSet = resourceInfo.executeQuery();
                if(resultSet.next()){
                    hash = resultSet.getString(1);
                    src = resultSet.getString(2);
                    name = resultSet.getString(3);
                    type = resultSet.getInt(4);
                    size = resultSet.getInt(5);
                    author = resultSet.getString(6);
                }

                resourceCache.setString(1, email);
                resourceCache.setLong(2, resourceId);
                resourceCache.setString(3, hash);
                resourceCache.setString(4, src);
                resourceCache.setString(5, name);
                resourceCache.setInt(6, type);
                resourceCache.setInt(7, size);
                resourceCache.setLong(8,createTime);
                resourceCache.setString(9, author);
                resourceCache.setInt(10, shareStatus);
                if (shareUser.getCompanyId() != null && shareUser.getCompanyId() > 0) {
                    resourceCache.setLong(11, shareUser.getCompanyId());
                } else {
                    resourceCache.setNull(11, Types.BIGINT);
                }
                resourceCache.setBoolean(12, Boolean.FALSE);
                resourceCache.setBoolean(13, shareStatus == ShareDTO.STATUS_SIGNED_BY_USERS);
                resourceCache.setString(14, email);
                resourceCache.setLong(15, resourceId);
                resourceCache.setString(16, email);
                resourceCache.executeUpdate();

            }
            // Add new request for new users
            for (ShareResDTO shareRes : nonExistsUserList) {
                String email = shareRes.getEmail();
                long createTime = new Date().getTime();

                ps.setLong(1, resourceId);
                ps.setString(2, email);
                ps.setInt(3, ShareDTO.STATUS_SEND_TO_UNKNOWN_USER);
                ps.setLong(4, createTime);
                ps.setInt(5, shareRes.getMode());
                ps.setNull(6, Types.BIGINT);
                ps.setString(7, user.getLogin());
                ps.setString(8, shareRes.getComment());
                ps.setLong(9, resourceId);
                ps.setString(10, email);
                int res = ps.executeUpdate();
                if (res > 0) {
                    EmailNotifier.getInstance().notifyEmail(EventType.SHARED, userName, email, resourceId, docName, user.getFullName(), true, shareRes.getComment(), shareRes.getTemplate());
                    result.add(new ShareDTO(resourceId, email, ShareDTO.STATUS_SEND_TO_UNKNOWN_USER, createTime, 0));
                }

                //todo quick govnokod

                String hash = null, src = null, name = null, author = null;
                int type = -1, size = -1;
                PreparedStatement resourceInfo = c.prepareStatement(
                        "SELECT hash, src, name, type, size, author FROM resource WHERE id = ?");
                resourceInfo.setLong(1, resourceId);
                ResultSet resultSet = resourceInfo.executeQuery();
                if(resultSet.next()){
                    hash = resultSet.getString(1);
                    src = resultSet.getString(2);
                    name = resultSet.getString(3);
                    type = resultSet.getInt(4);
                    size = resultSet.getInt(5);
                    author = resultSet.getString(6);
                }

                resourceCache.setString(1, email);
                resourceCache.setLong(2, resourceId);
                resourceCache.setString(3, hash);
                resourceCache.setString(4, src);
                resourceCache.setString(5, name);
                resourceCache.setInt(6, type);
                resourceCache.setInt(7, size);
                resourceCache.setLong(8,createTime);
                resourceCache.setString(9, author);
                resourceCache.setInt(10, ShareDTO.STATUS_SEND_TO_UNKNOWN_USER);
                resourceCache.setNull(11, Types.BIGINT);
                resourceCache.setBoolean(12, Boolean.FALSE);
                resourceCache.setBoolean(13, Boolean.FALSE);
                resourceCache.setString(14, email);
                resourceCache.setLong(15, resourceId);
                resourceCache.setString(16, email);
                resourceCache.executeUpdate();
            }
            c.commit();
            c.close();
            if (shareLogins.size() > 0){
                FriendCatalogDAO.getInstance().updateResourceByShare(user, resourceId, shareLogins);
            }
            Set<String> emails = alreadySharedCache.computeIfAbsent(resourceId, v-> new HashSet<>());
            emails.addAll(shareLogins);
        } catch (Exception e) {
            log.error("Can't share resource {}", resourceId, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                log.error("cant rollback",sqe);
            }
        } finally {
            try {
                if(!c.isClosed()){
                    c.close();
                }
            } catch (SQLException sqe) {
                log.error("can't close session",sqe);
            }
        }
        return result;
    }

    private List<ShareResDTO> filterAlreadyShared(Long resourceId, List<ShareResDTO> requestList) {
        //todo проверку перенести на взаимодействие с redis. проверку вынести на межнодовскую. сейчас только в рамках одной ноды.
        Set<String> emails = alreadySharedCache.get(resourceId);
        if(emails == null){
            return requestList;
        }
        return requestList.stream().filter(e->!emails.contains(e.getEmail())).collect(Collectors.toList());
    }

    public List<String> getSigns(String user, Long id, UserDTO boss) {
        List<String> result = new ArrayList<>();
        if(!checkResourceAccess(user, id, boss)){
            return null;
        }
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement(
                    "SELECT DISTINCT r.signs " +
                            "FROM resource AS r " +
                            "WHERE r.id = ? ");
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            Array arr = null;
            if (rs.next()) {
                arr = rs.getArray(1);
            }
            if (arr != null) {
                result = new ArrayList<>(Arrays.asList((String[]) arr.getArray()));
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't get resources for login = {}", user, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }

    public ArrayList<String> addSign(UserDTO user, Long id, List<String> sign) {
        log.info("addSign: login:{}:{}-{}", user.getLogin(), id, sign.size());
        Connection c = getConnection();
        assert c != null;
        String docName;
        String hash;
        String docAuthor;
        ArrayList<String> signs = new ArrayList<>();
        HashMap<Long,String> newCompanyMap = new HashMap<>();
        HashSet<String> anotherLogins = new HashSet<>();
        try {
            PreparedStatement ps = c.prepareStatement(
                    "WITH admins AS( " +
                                "SELECT login " +
                                "FROM employees " +
                                "WHERE company_id = ? AND role = ? AND status = ? " +
                            ") " +
                            "SELECT r.name, r.hash, r.author, r.signs " +
                            "FROM resource AS r " +
                            "LEFT JOIN share s ON (s.resource_id = r.id) " +
                            "WHERE r.id = ? AND ((r.author = ? OR s.user_login = ?) OR (? IN (SELECT * FROM admins)))");
            if(user.getCompanyId()!=null){
                ps.setLong(1, user.getCompanyId());
            }else{
                ps.setNull(1, Types.BIGINT);
            }
            ps.setLong(2, EmployeeDTO.ROLE_ADMIN);
            ps.setLong(3, EmployeeDTO.STATUS_ACCEPTED);
            ps.setLong(4, id);
            ps.setString(5, user.getLogin());
            ps.setString(6, user.getLogin());
            ps.setString(7, user.getLogin());

            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                c.rollback();
                return null;
            }
            docName = rs.getString(1);
            hash = rs.getString(2);
            docAuthor = rs.getString(3);
            Array arr = rs.getArray(4);
            if (arr != null) {
                signs = new ArrayList<>(Arrays.asList((String[]) arr.getArray()));
            }
            HashMap<String,X509Certificate> newSigns = new HashMap<>();

            for (String newSign : sign) {
                try {
                    HashMap<String,X509Certificate> clearedSigns = CryptoManager.getUniqueCms(signs, newSign, Base64.getUrlDecoder().decode(hash));
                    log.info("new sign (old {}) {} added with result count {}", signs.size(), newSign, clearedSigns.keySet().size());
                    if (clearedSigns.size()>0) {
                        for (String s : clearedSigns.keySet()) {
                            newSigns.put(s, clearedSigns.get(s));
                        }
                        signs.addAll(clearedSigns.keySet());
                    }
                } catch (Exception ignored) {

                }
            }

            if (newSigns.size() == 0) {
                c.rollback();
                return signs;
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String s : newSigns.keySet()) {
                ps = c.prepareStatement("UPDATE resource AS r SET signs = array_append(r.signs,?::TEXT) WHERE r.id = ? AND ( NOT r.signs && ? OR r.signs ISNULL )");
                ps.setString(1, s);
                ps.setLong(2, id);
                ps.setArray(3, c.createArrayOf("text", new String[]{s}));
                ps.executeUpdate();

                ps = c.prepareStatement("INSERT INTO sign_record (resource_id, time, hash, login, egrpou, company, inn) VALUES (?,?,?,?,?,?,?)");
                ps.setLong(1, id);
                ps.setLong(2, (new Date()).getTime());

                md.reset();
                md.update(s.getBytes("UTF-8"));
                ps.setString(3, Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest()));
                ps.setString(4, user.getLogin());
                X509Certificate cert = newSigns.get(s);
                String edrpou = CryptoManager.getEdrpou(cert);
                if (edrpou!=null && StringUtils.isNumeric(edrpou)){
                    Long egrpouLong = Long.valueOf(edrpou);
                    ps.setLong(5, egrpouLong);
                } else {
                    ps.setLong(5, 0);
                }
                String companyName = CryptoManager.getCompanyName(cert);
                if (edrpou != null && companyName != null) {
                    newCompanyMap.put(Long.valueOf(edrpou),companyName);
                }
                ps.setString(6, companyName);

                if (edrpou!=null && StringUtils.isNumeric(edrpou)){
                    Long egrpouLong = Long.valueOf(edrpou);
                    if (docAuthor.equals(user.getLogin())) {
                        BillingQueryManager.addToQueueEgrpou(id, egrpouLong, companyName);
                    }
                }

                String inn = CryptoManager.getInn(cert);
                if (inn!=null && StringUtils.isNumeric(inn)){
                    Long innLong = Long.valueOf(inn);
                    ps.setLong(7, innLong);
                    if(docAuthor.equals(user.getLogin())) {
                        BillingQueryManager.addToQueueInn(id, innLong, companyName);
                    }
                } else {
                    ps.setLong(7, 0);
                }
                ps.executeUpdate();

                //сохраняем пользователю в список егрпоу/инн от подписи которую он использовал
                try {
                    List<String> eginn = new ArrayList<>();
                    if (edrpou != null) {
                        eginn.add(edrpou);
                    }
                    if (inn != null) {
                        eginn.add(inn);
                    }
                    for (String el : eginn) {
                        ps = c.prepareStatement(
                                "UPDATE users SET egrpou = array_append(egrpou,?::TEXT) WHERE login = ? AND ( NOT egrpou && ? OR egrpou ISNULL )"
                        );
                        ps.setString(1, el);
                        ps.setString(2, user.getLogin());
                        ps.setArray(3, c.createArrayOf("TEXT", new String[]{el}));
                        ps.executeUpdate();
                    }
                }catch (Exception ex){
                    log.warn("save egrpou list to user fail", ex);
                }
            }

            if (docAuthor.equals(user.getLogin())) {
                ps = c.prepareStatement("UPDATE resource SET signed = TRUE WHERE resource.id = ? AND resource.author = ?");
                ps.setLong(1, id);
                ps.setString(2, user.getLogin());
                if (ps.executeUpdate() == 0) {
                    c.rollback();
                    return null;
                }
                ps.close();
                ps = c.prepareStatement(
                        "UPDATE resource_cache SET signed = TRUE WHERE owner = ? AND id = ?  and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                ps.setString(1, user.getLogin());
                ps.setLong(2, id);
                ps.setString(3, user.getLogin());
                ps.executeUpdate();
                ps.close();


            } else {
                anotherLogins.add(docAuthor);
                ps = c.prepareStatement("UPDATE share SET status = " + ShareDTO.STATUS_SIGNED_BY_USERS + " WHERE share.resource_id = ? AND share.user_login = ? ");
                ps.setLong(1, id);
                ps.setString(2, user.getLogin());
                if (ps.executeUpdate() == 0) {
                    if(user.getCompanyDTO()!=null) {
                        if (user.getCompanyDTO().getEmployee() == null) {
                            CompanyDAO.getInstance().haveBoss(user.getCompanyId(), user.getLogin());
                        } else {
                            if (!user.getCompanyDTO().haveBoss(user.getLogin())) {
                                c.rollback();
                                return null;
                            }
                        }
                    }
                }
                ps.close();

                ps = c.prepareStatement(
                        "UPDATE resource_cache SET status = 3, signed = TRUE WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                ps.setString(1, user.getLogin());
                ps.setLong(2, id);
                ps.setString(3, user.getLogin());
                ps.executeUpdate();
            }

            ps = c.prepareStatement("SELECT s.user_login FROM share AS s join users u on (u.login = s.user_login) WHERE s.resource_id = ? AND s.status < " + ShareDTO.STATUS_IN_TRASH);
            ps.setLong(1, id);
            rs = ps.executeQuery();
            while (rs.next()) {
                String userLogin = rs.getString(1);
                if (!user.getLogin().equals(userLogin) && userLogin!=null) {
                    anotherLogins.add(userLogin);
                    EmailNotifier.getInstance().notifyEmail(EventType.SIGN, user.getLogin(), userLogin, id, docName, user.getFullName());
                }
            }
            //допольнительно отправить сообщение автору документа
            if (!user.getLogin().equals(docAuthor)) {
                EmailNotifier.getInstance().notifyEmail(EventType.SIGN, user.getLogin(), docAuthor, id, docName, user.getFullName());
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't add sign to resources id = {} for login = {}", id, user.getLogin(), e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }

        FriendCatalogDAO.getInstance().updateResourceBySign(user, newCompanyMap, anotherLogins);
        return signs;
    }

    public List<ResourceDTO> search(List<Long> ids){
        List<ResourceDTO> resources = new ArrayList<>();
        if(ids==null || ids.isEmpty()){
            return resources;
        }
        Connection con = getConnection();
        assert con != null;
        try{
            StringBuilder builder = new StringBuilder();
            for (int i = ids.size(); i > 0; i--) {
                builder.append("?,");
            }
            PreparedStatement ps = con.prepareStatement("SELECT r.id, r.hash, r.src, r.name, r.type, r.size, r.time, r.author, r.status as res_status, r.tags, r.signed, r.company_id FROM resource AS r WHERE r.id in (" + builder.deleteCharAt(builder.length() - 1).toString() + ")");

            for (int i = ids.size(); i > 0; i--) {
                ps.setLong(i, ids.get(i - 1));
            }
            ResultSet resultSet = ps.executeQuery();
            while(resultSet.next()){
                resources.add(new ResourceDTO(resultSet));
            }
            con.commit();
        }catch (Exception e) {
            try {con.rollback();} catch (SQLException sqe) {log.error("error rollback",sqe);}
        } finally {
            try {con.close();} catch (SQLException sqe) {log.error("error close database connection",sqe);}
        }
        return resources;
    }

    /**
     * поиск отдельного документа пользователя. своего или расшаренного
     * @param login - нужен для проверка запрашивает автор документа или нет
     * @param id - идентификатор документа
     * @return
     */
    public ResourceDTO getUserResource(String login, long id){
        ResourceDTO resource = null;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT r.id, r.hash, r.src, r.name, r.type, r.size, r.time AS create_time, r.author, r.status AS res_status, r.tags AS res_tags, r.signed, s.user_login, " +
                        "s.status AS share_status, s.time AS share_time, s.tags AS share_tags, r.company_id AS res_company_id, s.company_id AS sh_company_id " +
                        "FROM resource r " +
                        "LEFT JOIN share s ON (r.id = s.resource_id ) " +
                        "WHERE ( r.id = ? or s.resource_id = ? ) ");
            ps.setLong(1,id);
            ps.setLong(2,id);
            ResultSet rs = ps.executeQuery();
            boolean cutAuthor = false;
            Map<String,Integer> shares = new HashMap<>();
            while(rs.next()){
                if(!cutAuthor) {
                    String author = rs.getString(8);
                    boolean isAuthor;
                    isAuthor = !(author == null || !author.equals(login));
                    resource = new ResourceDTO(rs, isAuthor);
                    cutAuthor = true;
                }
                String shareLogin = rs.getString(12);
                int shareStatus = rs.getInt(13);
                if(shareLogin!=null) {
                    shares.put(shareLogin, shareStatus);
                }
            }
            if(resource!=null){
                resource.setShares(shares);
            }
            con.commit();
        }catch (Exception e) {
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        } finally {
            try {con.close();} catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return resource;
    }

    /**
     * поиск идентификаторов ресурсов связанных с определенным пользователем
     * @param user - пользователь по которому необходимо получить список идентификаторов ресурсов
     * @return - список идентификатоов ресурсов пользователя
     */
    List<Long> getUserResourcesIds(UserDTO user){
        return search(null, user.getLogin(), null, user.getCompanyId()).stream().map(ResourceDTO::getId).collect(Collectors.toList());
    }

    public List<ResourceDTO> companySearch(FilterDTO filter, UserDTO user, String employee) {
        List<ResourceDTO> result;
        if(employee!=null) {
            long role = CompanyDAO.getInstance().getRole(user.getLogin(), user.getCompanyId());
            if(role==EmployeeDTO.ROLE_ADMIN && user.getCompanyDTO().hasEmployee(employee)) {
                result = search(filter, employee, user, user.getCompanyId());
                result = new ArrayList<>(result);
            }else{
                return new ArrayList<>();
            }
        }else{
            return search(filter,user.getLogin(), null, user.getCompanyId());
        }
        return result;
    }

    //фильтрации на стороне сервера
    public List<ResourceDTO> filter(FilterDTO filter, List<ResourceDTO> resourcesList){
        if(filter!=null && filter.contractor!=null){
            resourcesList = resourcesList.stream().parallel()
                .filter(e -> {
                boolean byAuthor = filter.contractor.contains(e.getAuthor());
                boolean byShares = false;
                    if (e.getShares() != null) {
                        Optional<String> first = e.getShares().keySet().stream().filter(sh -> filter.contractor.contains(sh)).findFirst();
                        byShares = first.isPresent();
                    }
                    return byAuthor || byShares;
                }).collect(Collectors.toList());
        }
        return resourcesList;
    }

    public List<ResourceDTO> search(FilterDTO filter, String login, UserDTO boss, Long companyId){
        if(Main.property.getProperty("useSearchMirror", "false").equals("false")){
            return searchV1(filter, login, boss, companyId);
        }else{
            return searchV2(filter, login, boss, companyId);
        }
    }

    private List<ResourceDTO> searchV2(FilterDTO filter, String login, UserDTO boss, Long companyId){
        if(filter == null){
            filter = new FilterDTO();
        }
        List<ResourceDTO> foundedResources = new ArrayList<>();
        Connection con = getConnection();
        try{
            boolean isBoss = (boss != null);
            SearchPreparedStatementV2 statementV2 = SearchPreparedStatementV2.getInstance();
            PreparedStatement preparedStatement = statementV2.getPreparedStatement(filter.getDocList(), filter.getAuthor(),con, login, companyId, isBoss, filter);
            ResultSet rs = preparedStatement.executeQuery();
            while(rs.next()){
                ResourceDTO resource = ResourceDTO.parseSearchV2Result(rs);
                foundedResources.add(resource);
            }
            List<Long> ids = foundedResources.stream().map(ResourceDTO::getId).collect(Collectors.toList());
            if (ids != null && !ids.isEmpty()) {
                List<Long> availableIds = ResourceDAO.getInstance().checkResourcesAccess(con, login, ids, boss);
                foundedResources = foundedResources.stream().filter(e -> availableIds.contains(e.getId())).collect(Collectors.toList());
                if (!availableIds.isEmpty()) {
                    Map<Long, List<ShareDTO>> resShares = ShareDAO.getInstance().getShare(con, login, availableIds);
                    foundedResources.forEach(e -> {
                        List<ShareDTO> shareDTOs = resShares.get(e.getId());
                        if (shareDTOs != null) {
                            e.setShares(shareDTOs.stream().collect(Collectors.toMap(ShareDTO::getUser, ShareDTO::getStatus, (status1, status2) -> status1)));
                        }
                    });
                }
            }
            con.commit();
        }catch (TemplateNotFoundException tne){
            log.warn("Can't get resources for login = {} error:{}", login, tne.getMessage());
            try {
                con.rollback();
            } catch (SQLException sqe) {
                //nothing to do
            }
        }catch (Exception e) {
            log.error("Can't get resources for login = {}", login, e);
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
        return foundedResources;
    }


    private List<ResourceDTO> searchV1(FilterDTO filter, String login, UserDTO boss, Long companyId){
        long t1 = System.currentTimeMillis();
        log.info("begin search V1:{}", t1);
        Map<Long, ResourceDTO> resources = new HashMap<>();
        List<ResourceDTO> resResources = new ArrayList<>();
        Connection con = getConnection();
        log.info("connection get:{}", (System.currentTimeMillis() - t1));
        try {
            SearchPreparedStatementV1 searchPS = SearchPreparedStatementV1.getInstance();
            PreparedStatement ps;
            boolean isBoss = (boss != null);
            if (filter != null && FilterDTO.DocList.trash.name().equals(filter.getDocList())) {
                if (filter.getTagFilter() != null) {
                    filter.setTagFilter(null);
                }
                if (!StringUtils.isEmpty(filter.getAuthor())) {
                    switch (filter.getAuthor()) {
                        default:
                        case "all": {
                            ps = searchPS.getTrashAll(con, login, companyId, isBoss, filter);
                            break;
                        }
                        case "my": {
                            ps = searchPS.getTrashMy(con, login, companyId, isBoss, filter);
                            break;
                        }
                        case "me": {
                            ps = searchPS.getTrashMe(con, login, companyId, isBoss, filter);
                            break;
                        }

                    }
                } else {
                    ps = searchPS.getTrashAll(con, login, companyId, isBoss, filter);
                }
            } else if (filter != null && (FilterDTO.DocList.docs.name().equals(filter.getDocList()) || FilterDTO.DocList.tag.name().equals(filter.getDocList()) )) {
                if (!StringUtils.isEmpty(filter.getAuthor())) {
                    switch (filter.getAuthor()) {
                        default:
                        case "all": {
                            ps = searchPS.getDocAll(con, login, companyId, isBoss, filter);
                            break;
                        }
                        case "my": {
                            ps = searchPS.getDocMy(con, login, companyId, isBoss, filter);
                            break;
                        }
                        case "me": {
                            ps = searchPS.getDocMe(con, login, companyId, isBoss, filter);
                            break;
                        }
                    }
                } else {
                    ps = searchPS.getDocAll(con, login, companyId, isBoss, filter);
                }
            } else {
                ps = searchPS.getStd(con, login, companyId, isBoss, filter);
            }
            log.info("statement prepared:{}", (System.currentTimeMillis() - t1));
            ResultSet rs = ps.executeQuery();
            log.info("search executed:{}", (System.currentTimeMillis() - t1));
            while (rs.next()) {
                long resourceId = rs.getLong(1);
                ResourceDTO res = ResourceDTO.parseSearchResult(rs);
                ResourceDTO oldRes = resources.get(resourceId);
                if (oldRes == null) {
                    resources.put(resourceId, res);
                    oldRes = res;
                }
                Map<String, Integer> shares = oldRes.getShares();
                if (shares == null) {
                    shares = new HashMap<>();
                    oldRes.setShares(shares);
                }
                String userLogin = rs.getString(13);
                int shareStatus = rs.getInt(14);
                if (userLogin != null && shareStatus != 0) {
                    shares.put(userLogin, shareStatus);
                }
                //fill full shares
            }
            log.info("search parsed:{}", (System.currentTimeMillis() - t1));
            resResources = new ArrayList<>(resources.values());
            if (!resResources.isEmpty()) {
                List<Long> ids = resResources.stream().filter(Objects::nonNull).map(ResourceDTO::getId).collect(Collectors.toList());
                if (ids != null && !ids.isEmpty()) {
                    List<Long> availableIds = ResourceDAO.getInstance().checkResourcesAccess(con, login, ids, boss);
                    resResources = resResources.stream().filter(e -> availableIds.contains(e.getId())).collect(Collectors.toList());
                    if (!availableIds.isEmpty()) {
                        //сделать одним запросом
                        Map<Long, List<ShareDTO>> resShares = ShareDAO.getInstance().getShare(login, availableIds);
                        resResources.forEach(e -> {
                            List<ShareDTO> shareDTOs = resShares.get(e.getId());
                            if (shareDTOs != null) {
                                e.setShares(shareDTOs.stream().collect(Collectors.toMap(ShareDTO::getUser, ShareDTO::getStatus, (status1, status2) -> status1)));
                            }
                        });
                    }
                }
            }
            log.info("share added:{}", (System.currentTimeMillis() - t1));
            con.commit();
        }catch (TemplateNotFoundException tne){
            //игнор ошибок работы с шаблонами (очитка памяти в момент вырубания сервера)
            log.warn("Can't get resources for login = {} error:{}", login, tne.getMessage());
            try {
                con.rollback();
            } catch (SQLException sqe) {
                //nothing to do
            }
        }catch (Exception e) {
            log.error("Can't get resources for login = {}", login, e);
            try {
                if(con!=null) {
                    con.rollback();
                }
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                if(con!=null) {
                    con.close();
                }
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        try {
            if(resResources!=null) {
                resResources.sort((r1, r2) -> r1.getTime() < r2.getTime() ? -1 : (r1.getTime() > r2.getTime() ? 1 : 0));
            }
        }catch (Exception ex){
            log.warn("error in sort:{}",ex.getMessage());
        }
        return resResources;
    }

    /**
     * search users which linked to resource
     * @param id - resource id
     * @return - list of linked logins
     */
    public Set<String> getAssociatedUsers(long id){
        Set<String> users = new HashSet<>();
        Connection con = null;
        try{
            con = getConnection();
            PreparedStatement ps = con.prepareStatement(
                    "SELECT u.login AS usr, us.login AS ushr " +
                            "FROM resource r " +
                                "JOIN users u ON (u.login = r.author) " +
                                "LEFT JOIN share s ON (s.resource_id = r.id) " +
                                "LEFT JOIN users us ON (us.login = s.user_login)" +
                            "WHERE r.id = ? ");
            ps.setLong(1,id);
            ResultSet resultSet = ps.executeQuery();
            if(resultSet.next()){
                String aut = resultSet.getString(1);
                String sec = resultSet.getString(2);
                users.add(aut);
                if(sec!=null) {
                    users.add(sec);
                }
            }
            while(resultSet.next()){
                if(resultSet.getString(2)!=null) {
                    users.add(resultSet.getString(2));
                }
            }
            con.commit();
        } catch (SQLException e) {
            log.error("error search linked people",e);
            try {
                con.rollback();
            } catch (SQLException sqe) {
                log.error("error rollback",sqe);
            }
        } finally {
            if(con!=null){
                try {con.close();} catch (SQLException sqe) {log.error("error close connection",sqe);}
            }
        }
        return users;
    }

    public void setCryptedResource(String hash) {
        Connection c = getConnection();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("UPDATE resource SET type = " + ResourceDTO.TYPE_SECURED_PDF + " WHERE resource.hash = ?");
            ps.setString(1, hash);
            ps.executeUpdate();
            c.commit();
        } catch (Exception e) {
            log.error("Can't update resources with hash = {}", hash, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
    }

    /**
     * disable or enable sharing document for all people by link
     */
    public ShareAllResultDTO shareAll(UserDTO user, Long resourceId, boolean delete) {
        Connection c = getConnection();
        String author;
        int status;
        ShareAllResultDTO res = new ShareAllResultDTO();
        assert c != null;
        try {
            PreparedStatement ps = c.prepareStatement("SELECT r.status, r.author, r.hash FROM resource AS r WHERE r.id = ?");
            ps.setLong(1, resourceId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                status = rs.getInt(1);
                author = rs.getString(2);
                res.hash = rs.getString(3);
                if (!author.equals(user.getLogin())) {
                    res.code = -1;
                    return res;
                }
            } else {
                res.code =  0;
                return res;
            }

            if (status >= ResourceDTO.STATUS_DELETED) {
                res.code = 0;
                return res;
            }

            if (delete && status % ResourceDTO.STATUS_IN_TRASH == ResourceDTO.STATUS_SHARED_ALL) {
                // disable sharing
                ps = c.prepareStatement("SELECT count(*) FROM share AS s WHERE s.resource_id = ?");
                ps.setLong(1, resourceId);
                rs = ps.executeQuery();
                int sharedCount = 0;
                if (rs.next()) {
                    sharedCount = rs.getInt(1);
                }
                ps = c.prepareStatement("UPDATE resource SET status = ? WHERE resource.id = ?");
                if (sharedCount > 0) {
                    ps.setInt(1, ResourceDTO.STATUS_SHARED);
                } else {
                    ps.setInt(1, ResourceDTO.STATUS_CREATED);
                }
                ps.setLong(2, resourceId);
                res.code = ps.executeUpdate();
                ps.close();

                ps = c.prepareStatement(
                        "UPDATE resource_cache SET status = ? WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                if (sharedCount > 0) {
                    ps.setInt(1, ResourceDTO.STATUS_SHARED);
                } else {
                    ps.setInt(1, ResourceDTO.STATUS_CREATED);
                }
                ps.setString(2, user.getLogin());
                ps.setLong(3, resourceId);
                ps.setString(4, user.getLogin());
                ps.executeUpdate();

            } else if (!delete) {
                // enable sharing
                ps = c.prepareStatement("UPDATE resource SET status = ? WHERE resource.id = ?");
                if (status < ResourceDTO.STATUS_IN_TRASH) {
                    ps.setInt(1, ResourceDTO.STATUS_SHARED_ALL);
                } else {
                    ps.setInt(1, ResourceDTO.STATUS_SHARED_ALL + ResourceDTO.STATUS_IN_TRASH);
                }
                ps.setLong(2, resourceId);
                res.code = ps.executeUpdate();
                ps.close();

                ps = c.prepareStatement(
                        "UPDATE resource_cache SET status = ? WHERE owner = ? AND id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
                if (status < ResourceDTO.STATUS_IN_TRASH) {
                    ps.setInt(1, ResourceDTO.STATUS_SHARED_ALL);
                } else {
                    ps.setInt(1, ResourceDTO.STATUS_SHARED_ALL + ResourceDTO.STATUS_IN_TRASH);
                }
                ps.setString(2, user.getLogin());
                ps.setLong(3, resourceId);
                ps.setString(4, user.getLogin());
                ps.executeUpdate();
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't delete resource with id = {}", resourceId, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return res;
    }

    public ResourceDTO getShare(String shareid) {
        ResourceDTO result = null;
        Connection c = getConnection();
        assert c != null;
        try {
            log.info("getShareAll:{}", shareid);
            String hash = shareid.substring(0, 43);
            Long id = Long.parseLong(shareid.substring(43));
            PreparedStatement ps = c.prepareStatement("SELECT r.id, r.hash, r.src, r.name, r.type, r.size, r.time, r.author, r.status AS res_status, r.tags, r.signed, r.company_id, r.signs FROM resource AS r WHERE r.id = ? AND r.hash = ?");
            ps.setLong(1, id);
            ps.setString(2, hash);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                result = new ResourceDTO(rs);
                if (result.getStatus() % ResourceDTO.STATUS_IN_TRASH != ResourceDTO.STATUS_SHARED_ALL || result.getStatus() >= ResourceDTO.STATUS_DELETED) {
                    return null;
                }
                Array arr = rs.getArray(13);
                if (arr != null) {
                    result.setSigns((String[]) arr.getArray());
                }
            }
            c.commit();
        }catch(java.lang.StringIndexOutOfBoundsException siobe){
            log.warn("incorrect shareid:{}",shareid);
        } catch (Exception e) {
            log.error("Can't get shared resource with id = {}", shareid, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }


    public int deleteShare(UserDTO user, Long id, String email) {
        Connection c = getConnection();
        int result = 0;
        assert c != null;
        try {
            email = email.toLowerCase();
            PreparedStatement ps;
            ResultSet rs;
            ps = c.prepareStatement("SELECT s.status, r.name FROM resource AS r JOIN share AS s ON (r.id = s.resource_id) JOIN share AS s1 ON (r.id = s1.resource_id) WHERE s.resource_id = ? AND s.user_login = ? AND (r.author = ? OR s1.user_login = ?)");
            ps.setLong(1, id);
            ps.setString(2, email);
            ps.setString(3, user.getLogin());
            ps.setString(4, user.getLogin());
            rs = ps.executeQuery();
            if (rs.next()) {
                int status = rs.getInt(1);
                String docName = rs.getString(2);
                int cutStatus = status % 10;
                if (cutStatus<=ShareDTO.STATUS_SEND_TO_KNOWN_USER) {
                    ps = c.prepareStatement("DELETE FROM share WHERE share.resource_id = ? AND share.user_login = ?");
                    ps.setLong(1, id);
                    ps.setString(2, email);
                    result = ps.executeUpdate();
                    ps.close();

                    ps = c.prepareStatement("DELETE FROM resource_cache WHERE owner = ? AND (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10) AND id = ? AND NOT created");
                    ps.setString(1, email);
                    ps.setString(2, email);
                    ps.setLong(3, id);
                    ps.executeUpdate();
                    ps.close();

                    EmailHelper.sendDisableShareEmail(user.getLogin(), email, id, docName, user.getFullName(), cutStatus);
                }
            }
            c.commit();
        } catch (Exception e) {
            log.error("Can't delete sharing for {} in resource {}", email, id, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }

    public int setTag(UserDTO user, Long id, int newTags) {
        Connection c = getConnection();
        int result = 0;
        assert c != null;
        try {
            String author;
            PreparedStatement ps = c.prepareStatement("SELECT r.author FROM resource AS r WHERE r.id = ?");
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                author = rs.getString(1);
            } else {
                return 0;
            }
            String login = user.getLogin();
            if (login.equals(author)) {
                ps = c.prepareStatement("UPDATE resource AS r SET tags = ? WHERE r.id = ? and r.author = ? ");
                ps.setInt(1, newTags);
                ps.setLong(2, id);
                ps.setString(3, login);
                result = ps.executeUpdate();
                if (result == 0) {
                    return 0;
                }
            } else {
                // Not own resources
                ps = c.prepareStatement("UPDATE share AS s SET tags = ? WHERE s.resource_id = ? and s.user_login = ?");
                ps.setInt(1, newTags);
                ps.setLong(2, id);
                ps.setString(3, login);
                result = ps.executeUpdate();
                if (result == 0) {
                    return 0;
                }
            }
            ps.close();
            ps = c.prepareStatement(
                    "UPDATE resource_cache SET tags = ? WHERE owner = ? and id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10) ");
            ps.setInt(1, newTags);
            ps.setString(2, login);
            ps.setLong(3, id);
            ps.setString(4, login);
            ps.executeUpdate();

            c.commit();
        } catch (Exception e) {
            log.error("Can't update tags of resource {}", id, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return result;
    }

    public ResourceStatisticResponseDTO getResourceStatistic(ResourceStatisticRequestDTO request) {
        Connection con = getConnection();
        assert con != null;
        ResourceStatisticResponseDTO response = new ResourceStatisticResponseDTO();
        try {
            int status = (request.isDeleted() ? Integer.MAX_VALUE : ResourceDTO.STATUS_IN_TRASH);
            //upload documents
            PreparedStatement ps;
            if (request.getEmail() != null) {
                ps = con.prepareStatement(
                        "SELECT count(*) " +
                                "FROM resource " +
                                "WHERE status < ? AND author = ? AND time BETWEEN ? AND ?");
                ps.setInt(1, status);
                ps.setString(2, request.getEmail());
                ps.setLong(3, request.getDateFrom().getTime());
                ps.setLong(4, request.getDateTo().getTime());
            } else {
                ps = con.prepareStatement(
                        "SELECT count(*) " +
                                "FROM resource " +
                                "WHERE status < ? AND time BETWEEN ? AND ?");
                ps.setInt(1, status);
                ps.setLong(2, request.getDateFrom().getTime());
                ps.setLong(3, request.getDateTo().getTime());
            }
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                response.upload = rs.getInt(1);
            }
            //shared
            if (request.getEmail() != null) {
                ps = con.prepareStatement(
                        "SELECT count(DISTINCT r.id) " +
                                "FROM resource r " +
                                "WHERE r.author = ? AND r.status!=0 AND r.status < ? AND time BETWEEN ? AND ?");
                ps.setString(1, request.getEmail());
                ps.setInt(2, status);
                ps.setLong(3, request.getDateFrom().getTime());
                ps.setLong(4, request.getDateTo().getTime());
            } else {
                ps = con.prepareStatement(
                        "SELECT count(DISTINCT r.id) " +
                                "FROM resource r " +
                                "WHERE r.status!=0 AND r.status < ? AND time BETWEEN ? AND ?");
                ps.setInt(1, status);
                ps.setLong(2, request.getDateFrom().getTime());
                ps.setLong(3, request.getDateTo().getTime());
            }
            rs = ps.executeQuery();
            while (rs.next()) {
                response.shared = rs.getInt(1);
            }
            //signed
            if (request.getEmail() != null) {
                ps = con.prepareStatement(
                        "SELECT count(*) " +
                                "FROM resource r WHERE ((r.id IN " +
                                "(SELECT s.resource_id FROM share s WHERE user_login = ? AND s.status = 3 AND r.status < ? )) " +
                                "OR (r.author = ? AND r.signed AND r.status< ?)) AND time BETWEEN ? AND ?");
                ps.setString(1, request.getEmail());
                ps.setInt(2, status);
                ps.setString(3, request.getEmail());
                ps.setInt(4, status);
                ps.setLong(5, request.getDateFrom().getTime());
                ps.setLong(6, request.getDateTo().getTime());
            } else {
                ps = con.prepareStatement(
                        "SELECT count(DISTINCT r.id) " +
                                "FROM resource r " +
                                "WHERE (r.signed OR r.signs IS NOT NULL) AND r.status < ? AND time BETWEEN ? AND ?");
                ps.setInt(1, status);
                ps.setLong(2, request.getDateFrom().getTime());
                ps.setLong(3, request.getDateTo().getTime());
            }
            rs = ps.executeQuery();
            while (rs.next()) {
                response.signed = rs.getInt(1);
            }
            con.commit();
        } catch (Exception ex) {
            log.error("Can't get document statistic", ex);
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
        return response;
    }

    /**
     * Получение автора документа.
     * @param id идентификатор документа
     * @return UserDTO автора документа. либо null  в случае если автор не найдет,
     *          либо автор уже удалил у себя документ полностью (статус документа >=20)
     */
    public UserDTO getResourceOwner(Long id) {
        UserDTO author = null;
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT author FROM resource WHERE id = ? and status < 20 ");
            ps.setLong(1, id);
            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                String authorString = resultSet.getString(1);
                if(authorString!=null) {
                    author = new UserDTO(authorString);
                }
            }
            con.commit();
        } catch (Exception ex) {
            log.error("Can't check resource owner", ex);
            try {
                con.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                con.close();
            } catch (Exception ex) {
                log.error("fail close connection", ex);
            }
        }
        return author;
    }

    public boolean isResourceExists(long id){
        boolean exists = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("select 1 from resource where id = ?");
            ps.setLong(1, id);
            ResultSet resultSet = ps.executeQuery();
            if(resultSet.next()){
                int res = resultSet.getInt(1);
                exists = (res == 1);
            }
            con.commit();
        }catch (Exception ex) {
            log.error("Can't check resource owner", ex);
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
        return exists;
    }

    public boolean isUserExists(String login){
        boolean exists = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("select 1 from users where login = ? ");
            ps.setString(1, login);
            ResultSet resultSet = ps.executeQuery();
            if(resultSet.next()){
                exists = resultSet.getBoolean(1);
            }
            con.commit();
        }catch (Exception ex) {
            log.error("Can't check resource owner", ex);
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
        return exists;
    }

    /**
     * поиск по документам компании. получаем документы. для каждого документа собираем все шары. основные статусы от автора документа.
     * @param companyId - идентификатор компании
     * @return - список документов компании
     */
    public List<ResourceDTO> searchByCompany(Long companyId){
        Map<ResourceDTO, Map<String,Integer>> res = new HashMap<>();
        List<ResourceDTO> collect = null;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT DISTINCT r.id, r.hash, r.src, r.name, r.type, r.size, r.time, r.author, r.status, r.tags, r.signed, r.company_id, s.user_login, s.status, greatest(r.time, s.time) " +
                            "FROM resource r " +
                            "LEFT JOIN share s ON (s.resource_id = r.id) " +
                            "WHERE r.company_id = ? OR s.company_id = ? " +
                            "ORDER BY greatest(r.time, s.time) DESC ");
            if(companyId!=null){
                ps.setLong(1, companyId);
                ps.setLong(2, companyId);
            }else{
                ps.setNull(1,Types.BIGINT);
                ps.setNull(2,Types.BIGINT);
            }
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                ResourceDTO resourceDTO = ResourceDTO.init(rs);
                String shareLogin = rs.getString(13);
                int shareStatus = rs.getInt(14);

                Map<String, Integer> shares;
                if(!res.containsKey(resourceDTO)){
                    res.put(resourceDTO, resourceDTO.getShares());
                    shares = resourceDTO.getShares();
                }else{
                    shares = res.get(resourceDTO);
                }
                if(shareLogin!=null && shareStatus!=0){
                    shares.put(shareLogin, shareStatus);
                }
            }
            con.commit();
            res.forEach(ResourceDTO::setShares);
            //клиент игнорирует сортировку от сервера
            collect = res.keySet().stream().sorted((r1, r2) -> r1.getTime() < r2.getTime() ? 1 : (r1.getTime() > r2.getTime() ? -1 : 0)).collect(Collectors.toList());
        }catch (Exception ex) {
            log.error("Can't searchByCompany", ex);
            try {
                con.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                con.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return collect;
    }

    private List<Long> checkResourcesAccess(Connection con, String login, List<Long> ids, UserDTO boss){
        List<Long> availableIds = new ArrayList<>();
        try{
            String wher = ids.stream().map(e->"?").collect(Collectors.joining(","));
            PreparedStatement ps;
            if(boss!=null && boss.getCompanyId()!=null){
                String select =
                        "SELECT r.id " +
                            "FROM resource r " +
                            "LEFT JOIN users u ON (r.author = u.login) " +
                            "WHERE (r.id in ( "+wher+" ) AND r.author = ? AND r.company_id = ?) " +
                            "UNION " +
                            "SELECT s.resource_id " +
                            "FROM share s " +
                            "LEFT JOIN users u ON (s.user_login = u.login) " +
                            "WHERE (s.resource_id in ( " +wher+ " ) AND s.user_login = ? AND s.company_id = ? ) ";
                ps = con.prepareStatement(select);
                int i = 1;
                for(int j=0; j<2; j++) {
                    for (Long id : ids) {
                        ps.setLong(i++, id);
                    }
                    ps.setString(i++, login);
                    ps.setLong(i++, boss.getCompanyId());
                }
            }else {
                String select =
                        "SELECT r.id " +
                                "FROM resource r " +
                                "LEFT JOIN users u ON (r.author = u.login) " +
                                "WHERE ((r.id in ( " + wher + " ) AND r.author = ? AND ((r.company_id = u.company_id ) OR (r.company_id IS NULL)))) " +
                                "UNION " +
                                "SELECT s.resource_id " +
                                "FROM share s " +
                                "LEFT JOIN users u ON (s.user_login = u.login) " +
                                "WHERE ((s.resource_id in ( " + wher + " ) AND s.user_login = ? AND ((s.company_id = u.company_id ) OR (s.company_id IS NULL)))) ";
                ps = con.prepareStatement(select);
                int i = 1;
                for (int j = 0; j < 2; j++) {
                    for (Long id : ids) {
                        ps.setLong(i++, id);
                    }
                    ps.setString(i++, login);
                }
            }
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                Long resId = rs.getLong(1);
                availableIds.add(resId);
            }
            ps.close();
        }catch (Exception ex) {
            log.error("Can't checkResourceAccess", ex);
        }
        return availableIds.stream().distinct().collect(Collectors.toList());
    }

    /**
     * Проверка прав доступа к ресурсу.
     * Доступ имеет автор и тот кому расшарили документ. при условии что компания совпадает (либо компания у шаринга или ресурса пустая).
     * так же доступ имеет администратор группы. проверка на администратор - третий параметр true.
     * @param login - login пользователя доспут которого необходимо проверить.
     * @param id - id документа доступ к которому необходимо проверить.
     * @param boss - пользователь админ группы. (проверить у ресурса и админа одинаковая группа)
     * @return true - доступ есть, false доступ отсутствует.
     */

    public boolean checkResourceAccess(String login, long id, UserDTO boss) {
        Set<Long> resourcesIds = allowedToUserResources.get(login);
        if(resourcesIds!=null){
            if(resourcesIds.contains(id)){
                return true;
            }
        }
        if(boss!=null){
            resourcesIds = allowedToUserResources.get(boss.getLogin());
            if(resourcesIds!=null){
                if(resourcesIds.contains(id)){
                    return true;
                }
            }
        }
        ResourceDTO resourceDTO = get(id);
        if(resourceDTO==null){
            return false;
        }
        if (login.equals(resourceDTO.getAuthor())) {
            Set<Long> resIds = allowedToUserResources.computeIfAbsent(login, v -> new HashSet<>());
            resIds.add(id);
            return true;
        }
        if (resourceDTO.getShares().containsKey(login)) {
            Set<Long> resIds = allowedToUserResources.computeIfAbsent(login, v -> new HashSet<>());
            resIds.add(id);
            return true;
        }
        if(boss!=null && boss.getCompanyId()!=null) {
            String bossLogin = boss.getLogin();
            Long companyId = boss.getCompanyId();
            if(companyId!=null) {
                List<String> admins = CompanyDAO.getInstance().getAdmins(companyId);
                if(admins.contains(boss.getLogin())){
                    if (Objects.equals(boss.getCompanyId(), resourceDTO.getCompanyId())) {
                        Set<Long> resIds = allowedToUserResources.computeIfAbsent(bossLogin, v -> new HashSet<>());
                        resIds.add(id);
                        return true;
                    }
                    List<Long> ids = getResourceSharesCompany(resourceDTO.getId());
                    if(ids.contains(boss.getCompanyId())){
                        Set<Long> resIds = allowedToUserResources.computeIfAbsent(bossLogin, v -> new HashSet<>());
                        resIds.add(id);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<Long> getResourceSharesCompany(long id){
        List<Long> ids = new ArrayList<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "select s.company_id from share s where resource_id = ? ");
            ps.setLong(1, id);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                Long companyId = rs.getObject(1, Long.class);
                if(companyId != null ) {
                    ids.add(companyId);
                }
            }
            con.commit();
        }catch (Exception ex) {
            log.error("Can't get document", ex);
            try {
                con.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                con.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        return ids;
    }

    /**
     * получение одного ресурса под видом автора
     * @param id
     * @return
     */
    public ResourceDTO get(long id){
        final ResourceDTO[] resource = {null};
        Connection con = getConnection(); //sharing connection/readonly
        try {
            CountDownLatch latch = new CountDownLatch(2);
            pool.execute(() -> {
                try {
                    PreparedStatement ps = con.prepareStatement(
                            "SELECT r.id, r.hash, r.src, r.name, r.type, r.size, r.time, r.author, r.status , r.tags , r.signed, r.company_id " +
                                    "FROM resource r " +
                                    "WHERE r.id = ?");
                    ps.setLong(1, id);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        resource[0] = new ResourceDTO(rs);
                    }
                    latch.countDown();
                }catch (Exception ex){
                    log.error("error get resource ", ex);
                }
            });
            Map<String, Integer> shares = new HashMap<>();
            pool.execute(() -> {
                try {
                    PreparedStatement ps = con.prepareStatement(
                            "SELECT s.user_login, s.status " +
                                    "FROM share s " +
                                    "WHERE s.resource_id = ?");
                    ps.setLong(1, id);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        String shareLogin = rs.getString(1);
                        int shareStatus = rs.getInt(2);
                        if (shareLogin != null) {
                            shares.put(shareLogin, shareStatus);
                        }
                    }
                    latch.countDown();
                }catch (Exception ex){
                    log.error("error get resource ", ex);
                }
            });
            latch.await();
            if (resource[0] != null) {
                resource[0].setShares(shares);
            }
            con.commit();
            } catch (Exception ex) {
                log.error("Can't get document", ex);
                try {
                    con.rollback();
                } catch (SQLException sqe) {
                    sqe.printStackTrace();
                }
            } finally {
                try {
                    con.close();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        return resource[0];
    }

    /**
     * подпись для тех кто пришел по спец ссылке!
     * @param id
     * @param sign
     * @return
     */
    public List<String> addSign(String initiator, Long id, List<String> sign) {
        Connection c = getConnection();
        String docName;
        String hash;
        List<String> signs = new ArrayList<>();
        try {
            PreparedStatement ps = c.prepareStatement(
                            "SELECT r.name, r.hash, r.author, r.signs " +
                            "FROM resource AS r " +
                            "LEFT JOIN share s ON (s.resource_id = r.id) " +
                            "WHERE r.id = ? ");
            ps.setLong(1, id);

            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                c.rollback();
                log.info("return 1");
                return null;
            }
            docName = rs.getString(1);
            hash = rs.getString(2);
            Array arr = rs.getArray(4);
            if (arr != null) {
                signs = new ArrayList<>(Arrays.asList((String[]) arr.getArray()));
            }
            Map<String,X509Certificate> newSigns = new HashMap<>();

            for (String newSign : sign) {
                try {
                    Map<String,X509Certificate> clearedSigns = CryptoManager.getUniqueCms(signs, newSign, Base64.getUrlDecoder().decode(hash));
                    if (clearedSigns!=null){
                        for (String s : clearedSigns.keySet()){
                            newSigns.put(s, clearedSigns.get(s));
                        }
                        signs.addAll(clearedSigns.keySet());
                    }
                } catch (Exception ignored) {

                }
            }

            if (newSigns.size() == 0) {
                c.rollback();
                log.info("return 2");
                return signs;
            }

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String s : newSigns.keySet()) {
                log.info("sign");
                ps = c.prepareStatement("UPDATE resource AS r SET signs = array_append(r.signs,?::TEXT) WHERE r.id = ? AND ( NOT r.signs && ? OR r.signs ISNULL )");
                ps.setString(1, s);
                ps.setLong(2, id);
                ps.setArray(3, c.createArrayOf("text", new String[]{s}));
                ps.executeUpdate();

                ps = c.prepareStatement("INSERT INTO sign_record (resource_id, time, hash, login, egrpou, company, inn) VALUES (?,?,?,?,?,?,?)");
                ps.setLong(1, id);
                ps.setLong(2, (new Date()).getTime());

                md.reset();
                md.update(s.getBytes("UTF-8"));
                ps.setString(3, Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest()));
                ps.setString(4, initiator);
                X509Certificate cert = newSigns.get(s);
                String edrpou = CryptoManager.getEdrpou(cert);
                if (edrpou!=null){
                    Long egrpouLong = Long.valueOf(edrpou);
                    ps.setLong(5, egrpouLong);
                } else {
                    ps.setLong(5, 0);
                }
                String companyName  = CryptoManager.getCompanyName(cert);
                ps.setString(6, companyName);
                String inn = CryptoManager.getInn(cert);
                if (inn!=null){
                    Long innLong = Long.valueOf(inn);
                    ps.setLong(7, innLong);
                } else {
                    ps.setLong(7, 0);
                }
                ps.executeUpdate();
            }

            //добавить отсылку автору ресурса
            if (initiator.lastIndexOf("@")!=initiator.length()-1) {
                initiator = initiator.substring(0, initiator.length()-1);
            }
            ps = c.prepareStatement("SELECT s.user_login FROM share AS s JOIN users u ON (u.login = s.user_login) WHERE s.resource_id = ? AND s.status < " + ShareDTO.STATUS_IN_TRASH);
            ps.setLong(1, id);
            rs = ps.executeQuery();
            while (rs.next()) {
                EmailNotifier.getInstance().notifyEmail(EventType.SIGN, initiator, rs.getString(1), id, docName, "Невідомій (за ініціативою " + initiator + " )");
            }
            ps = c.prepareStatement("select r.author from resource r where r.id = ? and r.status < " + ResourceDTO.STATUS_DELETED);
            ps.setLong(1, id);
            rs = ps.executeQuery();
            if (rs.next()) {
                EmailNotifier.getInstance().notifyEmail(EventType.SIGN, initiator, rs.getString(1), id, docName, "Невідомій (за ініціативою " + initiator + " )");
            }

            c.commit();
        } catch (Exception e) {
            log.error("Can't add sign to resources id = {} for login = {}", id, initiator);
            log.error("error:",e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return signs;
    }

    /**
     * используется в конце регистрации для создания списки друзей пользователя
     * @param user
     * @return
     */
    public UserDTO collectUserFriends(UserDTO user) {
        Connection con = getConnection();
        try{
            String userLogin = user.getLogin();
            PreparedStatement ps = con.prepareStatement(
                    "SELECT u.login, u.full_name " +
                    "FROM users u " +
                    "JOIN resource r ON (r.author = u.login) " +
                    "JOIN share s ON (s.resource_id = r.id) " +
                    "WHERE s.user_login = ? ");
            ps.setString(1, userLogin);
            ResultSet rs = ps.executeQuery();
            Map<String, String> friendsList = new HashMap<>();
            while(rs.next()){
                String login = rs.getString(1);
                if(!login.equals(userLogin)) {
                    String fullName = rs.getString(2);
                    friendsList.put(login, fullName);
                }
            }
            user.setFriends(friendsList);
            ps = con.prepareStatement("UPDATE users AS u SET friends = array_append(u.friends,?::TEXT) WHERE u.login = ? AND ( NOT u.friends && ? OR u.friends ISNULL )");
            PreparedStatement finalPs = ps;
            friendsList.keySet().forEach(e-> {
                try {
                    finalPs.setString(1, e);
                    finalPs.setString(2, user.getLogin());
                    finalPs.setArray(3, con.createArrayOf("text", new String[]{e}));
                    finalPs.executeUpdate();
                }catch (Exception ex){
                    log.error("error update user friends",ex);
                }
            });
            con.commit();
        } catch (Exception e) {
            log.error("error:",e);
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
        return user;
    }

    public int deleteUserResources(Connection con, UserDTO user) throws SQLException {
        PreparedStatement ps = con.prepareStatement("DELETE FROM resource where author = ? ");
        ps.setString(1, user.getLogin());
        return ps.executeUpdate();
    }

    public List<Long> checkUserResourcesVisible(UserDTO user, List<Long> docID) {
        List<Long> existingResources = new ArrayList<>();
        Connection con = getConnection();
        try{
            String in = docID.stream().map(e->"?").collect(Collectors.joining(",","(",")"));
            //check is user resources
            PreparedStatement userResources = con.prepareStatement(
                    "SELECT r.id " +
                    "FROM resource r " +
                    "WHERE r.author = ? AND r.company_id = ? AND r.id IN "+in +" AND r.status <20");
            int i = 1;
            userResources.setString(i++, user.getLogin());
            if(user.getCompanyId()==null) {
                userResources.setNull(i++, Types.BIGINT);
            }else{
                userResources.setLong(i++, user.getCompanyId());
            }
            for (Long resourceId : docID) {
                userResources.setLong(i++, resourceId);
            }
            ResultSet rs = userResources.executeQuery();
            while(rs.next()){
                existingResources.add(rs.getLong(1));
            }
            rs.close();
            userResources.close();
            PreparedStatement shareResources = con.prepareStatement(
                    "SELECT s.resource_id " +
                    "FROM share s " +
                    "WHERE s.user_login = ? AND s.company_id = ? AND s.resource_id IN "+in);
            i = 1;
            shareResources.setString(i++, user.getLogin());
            if(user.getCompanyId()==null) {
                shareResources.setNull(i++, Types.BIGINT);
            }else{
                shareResources.setLong(i++, user.getCompanyId());
            }
            for (Long resourceId : docID) {
                shareResources.setLong(i++, resourceId);
            }
            rs = shareResources.executeQuery();
            while(rs.next()){
                existingResources.add(rs.getLong(1));
            }
            rs.close();
            shareResources.close();
            con.commit();
        } catch (Exception e) {
            log.error("error:",e);
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
        return existingResources;
    }

    class TmpHolder{
        String login;
        long resourceId;
    }

    public void fixDeletedShare(){
        Connection con = getConnection();
        try{
            List<TmpHolder> data = new ArrayList<>();
            PreparedStatement ps = con.prepareStatement(
                    "SELECT rc.owner, rc.id " +
                    "FROM resource_cache rc " +
                    "  LEFT JOIN share s ON (s.user_login = rc.owner AND s.resource_id = rc.id) " +
                    "WHERE NOT rc.created AND s.resource_id IS NULL ");
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                TmpHolder tmpHolder = new TmpHolder();
                tmpHolder.login = rs.getString(1);
                tmpHolder.resourceId  = rs.getLong(2);
                data.add(tmpHolder);
            }
            rs.close();
            ps.close();
            log.info("found {} broken entity", data.size());
            ps = con.prepareStatement("DELETE FROM resource_cache WHERE resource_cache.owner = ? AND id = ? AND NOT created");
            for (TmpHolder datum : data) {
                ps.setString(1, datum.login);
                ps.setLong(2, datum.resourceId);
                ps.executeUpdate();
            }
            ps.close();
            con.commit();
        } catch (Exception e) {
            log.error("error:",e);
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
    }
}