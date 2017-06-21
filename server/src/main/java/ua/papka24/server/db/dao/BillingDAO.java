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

import org.apache.http.util.TextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dto.ResourceMiniDTO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.db.dto.billing.BillingEgpouInnHolder;
import ua.papka24.server.db.dto.billing.BillingResponseDTO;
import ua.papka24.server.db.dto.billing.UploadTrend;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;


public class BillingDAO extends DAO {

    private static final Logger log = LoggerFactory.getLogger(BillingDAO.class);

    public enum Schema{
        INIT        (0),
        SIGN        (1),
        MAX         (2),
        HAND        (3),
        PREFER      (4);

        Schema(int code){
            this.code = code;
        }

        public int code;
    }

    private BillingDAO(){}

    private static class Singleton {
        private static final BillingDAO HOLDER_INSTANCE = new BillingDAO();
    }

    public static BillingDAO getInstance() {
        return BillingDAO.Singleton.HOLDER_INSTANCE;
    }

    public void setPreferEgrpou(String login, Long egrpou) {
        Connection con = getConnection();
        try{
            String company;
            PreparedStatement ps = con.prepareStatement("SELECT company FROM sign_record WHERE egrpou = ? LIMIT 1;");
            ps.setLong(1, egrpou);
            ResultSet resultSet = ps.executeQuery();
            if(resultSet.next()){
                company = resultSet.getString(1);
            }else{
                company = "";
            }
            ps = con.prepareStatement(
            "WITH upd AS ( " +
                    "UPDATE billing_prefer_egrpou SET egrpou = ? WHERE login = ? RETURNING egrpou " +
                    ")" +
                    "INSERT INTO billing_prefer_egrpou(login, egrpou, company, time) SELECT ?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM upd) "
            );
            ps.setLong(1, egrpou);
            ps.setString(2, login);
            ps.setString(3, login);
            ps.setLong(4, egrpou);
            ps.setString(5, company);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();

            ps.close();
            ps = con.prepareStatement(
                    "UPDATE billing_resources SET egrpou = ?, schema = 3, commit_time = ? WHERE author = ? AND inn IS NULL AND egrpou IS NUll");
            ps.setLong(1, egrpou);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, login);
            ps.executeUpdate();

            con.commit();
        } catch (Exception ex) {
            log.error("BillingDAO error:", ex);
            ex.printStackTrace();
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

    public List<ResourceMiniDTO> getResources(List<String> egrpouLogins, long from, long to) {
        List<ResourceMiniDTO> resourceMiniDTOS = new ArrayList<>();
        Connection con = getConnection();
        try{
            String where = egrpouLogins.stream().map(e->"?").collect(Collectors.joining(",","(",")"));
            PreparedStatement ps = con.prepareStatement(
                    "SELECT id, name, author, time FROM resource WHERE time>=? AND time < ? AND author in "+where);
            ps.setLong(1, from);
            ps.setLong(2, to);
            int i = 3;
            for (String egrpouLogin : egrpouLogins) {
                ps.setString(i++, egrpouLogin);
            }
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                long id = rs.getLong(1);
                String name = rs.getString(2);
                String author = rs.getString(3);
                long time = rs.getLong(4);
                resourceMiniDTOS.add(new ResourceMiniDTO(id, name, author, time));
            }
            con.commit();
        } catch (Exception ex) {
            log.error("BillingDAO error:", ex);
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
        return resourceMiniDTOS;
    }

    public List<ResourceMiniDTO> getShareResources(List<String> logins, long from, long to) {
        List<ResourceMiniDTO> resourceMiniDTOS = new ArrayList<>();
        Connection con = getConnection();
        try{
            String where = logins.stream().map(e->"?").collect(Collectors.joining(",","(",")"));
            PreparedStatement ps = con.prepareStatement(
                    "SELECT r.id, r.name, r.author, s.time " +
                        "FROM share s " +
                        "JOIN resource r ON (s.resource_id = r.id) " +
                        "WHERE s.time >= ? AND s.time < ? AND s.initiator IN "+where);
            ps.setLong(1, from);
            ps.setLong(2, to);
            int i = 3;
            for (String egrpouLogin : logins) {
                ps.setString(i++, egrpouLogin);
            }
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                long id = rs.getLong(1);
                String name = rs.getString(2);
                String author = rs.getString(3);
                long time = rs.getLong(4);
                resourceMiniDTOS.add(new ResourceMiniDTO(id, name, author, time));
            }
            con.commit();
        } catch (Exception ex) {
            log.error("BillingDAO error:", ex);
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
        return resourceMiniDTOS;
    }

    public List<ResourceMiniDTO> getSignResources(List<String> logins, long from, long to) {
        List<ResourceMiniDTO> resourceMiniDTOS = new ArrayList<>();
        Connection con = getConnection();
        try{
            String where = logins.stream().map(e->"?").collect(Collectors.joining(",","(",")"));
            PreparedStatement ps = con.prepareStatement(
                    "SELECT r.id, r.name, r.author, s.time " +
                            "FROM sign_record s " +
                            "JOIN resource r ON (s.resource_id = r.id) " +
                            "WHERE s.time >= ? AND s.time < ? AND s.login IN "+where);
            ps.setLong(1, from);
            ps.setLong(2, to);
            int i = 3;
            for (String egrpouLogin : logins) {
                ps.setString(i++, egrpouLogin);
            }
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                long id = rs.getLong(1);
                String name = rs.getString(2);
                String author = rs.getString(3);
                long time = rs.getLong(4);
                resourceMiniDTOS.add(new ResourceMiniDTO(id, name, author, time));
            }
            con.commit();
        } catch (Exception ex) {
            log.error("BillingDAO error:", ex);
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
        return resourceMiniDTOS;
    }

    public boolean changeUserBlockState(String login, long state){
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("update users set blocked = ? where login = ? ");
            ps.setLong(1, state);
            ps.setString(2, login);
            res = ps.executeUpdate() > 0;
            con.commit();
        }catch (Exception ex){
            log.error("Can't user changeUserBlockState ", ex);
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

    public boolean changeEgrpouBlockState(long egrpou, long state){
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("insert into blocked(id,type) values (?,?)");
            ps.setLong(1, egrpou);
            ps.setLong(2, state);
            res = ps.executeUpdate() > 0;
            con.commit();
        }catch (Exception ex){
            log.error("Can't user changeCompanyBlockState ", ex);
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

    public boolean removeUserBlockState(String login){
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("update users set blocked = 0 where login = ? ");
            ps.setString(1, login);
            res = ps.executeUpdate() > 0;
            con.commit();
        }catch (Exception ex){
            log.error("Can't user removeBlockState ", ex);
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

    public boolean removeEgrpouBlockState(long egrpou){
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("delete from blocked where id = ?");
            ps.setLong(1, egrpou);
            res = ps.executeUpdate() > 0;
            con.commit();
        }catch (Exception ex){
            log.error("Can't user removeBlockState ", ex);
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

    public List<Long> checkBlockedState(String login){
        List<Long> blockedEgrpou = new ArrayList<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("select blocked from users where login = ? ");
            ps.setString(1, login);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                long r = rs.getLong(1);
                if(r!=0) {
                    blockedEgrpou.add(r);
                }
            }
            rs.close();
            if(blockedEgrpou.size()==0){
                ps = con.prepareStatement(
                        "SELECT DISTINCT s.egrpou " +
                                "FROM sign_record s " +
                                "JOIN blocked b ON (b.id = s.egrpou) " +
                                "WHERE login = ? ");
                ps.setString(1, login);
                rs = ps.executeQuery();
                while(rs.next()){
                    blockedEgrpou.add(rs.getLong(1));
                }
            }
            con.commit();
        }catch (Exception ex){
            log.error("Can't check user blockedState", ex);
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
        return blockedEgrpou;
    }

    public Map<String, Long> getPreferEgropou() {
        Map<String, Long> dist = new HashMap<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT login, egrpou FROM billing_prefer_egrpou");
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                dist.put(rs.getString(1), rs.getLong(2));
            }
            con.commit();
        }catch(Exception ex){
            log.error("Can't getPreferEgropou", ex);
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
        return dist;
    }

    public void truncate() {
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("TRUNCATE TABLE billing_resources;");
            ps.executeUpdate();
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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

    public boolean create(ResourceDTO resource){
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO billing_resources(resource_id, author, time, name) VALUES (?, ?, ?, ?);");
            ps.setLong(1, resource.getId());
            ps.setString(2, resource.getAuthor());
            ps.setLong(3, resource.getTime());
            ps.setString(4, resource.getName());
            res = ps.executeUpdate() > 0;
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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

    public boolean setEgrpou(long resourceId, long egrpou, String companyName, Schema schema){
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE billing_resources SET egrpou = ?, schema = ?, commit_time = ?, company = ? WHERE resource_id = ?");
            ps.setLong(1, egrpou);
            ps.setInt(2, schema.code);
            ps.setLong(3, System.currentTimeMillis());
            if(companyName==null){
                ps.setNull(4, Types.VARCHAR);
            }else{
                ps.setString(4, companyName);
            }
            ps.setLong(5, resourceId);
            res = ps.executeUpdate() > 0;
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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

    public boolean setInn(long resourceId, Long inn, String companyName, Schema schema) {
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE billing_resources SET inn = ?, schema = ?, commit_time = ?, company = ? WHERE resource_id = ?");
            ps.setLong(1, inn);
            ps.setInt(2, schema.code);
            ps.setLong(3, System.currentTimeMillis());
            if(companyName==null){
                ps.setNull(4, Types.VARCHAR);
            }else{
                ps.setString(4, companyName);
            }
            ps.setLong(5, resourceId);
            res = ps.executeUpdate() > 0;
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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

    public void copyResources() {
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO billing_resources(resource_id, author, time, name) " +
                            "SELECT id, author, TIME, NAME FROM resource; ");
            ps.executeUpdate();
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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

    public void updateResourcesInfo() {
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT resource_id, author " +
                            "FROM billing_resources WHERE egrpou IS NULL OR inn IS NULL; ");
            Map<Long,String> billingResource = new HashMap<>();
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                billingResource.put(rs.getLong(1), rs.getString(2));
            }
            rs.close();
            ps.close();
            ps = con.prepareStatement(
                    "SELECT egrpou, inn, company " +
                        "FROM sign_record WHERE resource_id = ? AND login = ?; ");
            Map<Long, BillingEgpouInnHolder> holder = new HashMap<>();
            for (Long resourceId : billingResource.keySet()) {
                ps.setLong(1, resourceId);
                ps.setString(2, billingResource.get(resourceId));
                rs = ps.executeQuery();
                while(rs.next()){
                    Long egrpou = rs.getObject(1, Long.class);
                    Long inn = rs.getObject(2, Long.class);
                    String company = rs.getString(3);
                    BillingEgpouInnHolder item = new BillingEgpouInnHolder(egrpou, inn, company);
                    holder.compute(resourceId, (key, billingEgpouInnHolder)->{
                        BillingEgpouInnHolder itm = new BillingEgpouInnHolder();
                        if(billingEgpouInnHolder == null){
                            itm = item;
                        }else {
                            if (billingEgpouInnHolder.egrpou == null) {
                                itm.egrpou = egrpou;
                            } else {
                                if (egrpou > billingEgpouInnHolder.egrpou) {
                                    itm.egrpou = egrpou;
                                } else {
                                    itm.egrpou = billingEgpouInnHolder.egrpou;
                                }
                            }
                            if (billingEgpouInnHolder.inn == null) {
                                itm.inn = inn;
                            } else {
                                if (inn > billingEgpouInnHolder.inn) {
                                    itm.inn = inn;
                                } else {
                                    itm.inn = billingEgpouInnHolder.inn;
                                }
                            }
                            itm.company = billingEgpouInnHolder.company;
                        }
                        return itm;
                    });
                }
            }
            PreparedStatement updateBillingResourceEgrpou = con.prepareStatement(
                    "UPDATE billing_resources " +
                            "SET egrpou = ?, company = ?, schema = 0, commit_time = ? WHERE resource_id = ? ");
            PreparedStatement updateBillingResourceInn = con.prepareStatement(
                    "UPDATE billing_resources " +
                            "SET inn = ?, company = ?, schema = 0, commit_time = ? WHERE resource_id = ? ");
            long time = System.currentTimeMillis();
            for (Long resourceId : holder.keySet()) {
                BillingEgpouInnHolder item = holder.get(resourceId);
                if(item.egrpou==null) {
                    updateBillingResourceEgrpou.setNull(1, Types.BIGINT);
                }else{
                    updateBillingResourceEgrpou.setLong(1, item.egrpou);
                }
                updateBillingResourceEgrpou.setString(2, item.company);
                updateBillingResourceEgrpou.setLong(3, time);
                updateBillingResourceEgrpou.setLong(4, resourceId);
                updateBillingResourceEgrpou.executeUpdate();

                if(item.inn==null) {
                    updateBillingResourceInn.setNull(1, Types.BIGINT);
                }else{
                    updateBillingResourceInn.setLong(1, item.inn);
                }
                updateBillingResourceInn.setString(2, item.company);
                updateBillingResourceInn.setLong(3, time);
                updateBillingResourceInn.setLong(4, resourceId);
                updateBillingResourceInn.executeUpdate();
            }
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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

    public void calcEgrpouByPeriod(long from, long to){
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT author " +
                            "FROM billing_resources " +
                            "WHERE time >= ? AND time < ? " +
                                "AND egrpou IS NULL AND inn IS NULL " +
                            "GROUP BY author");
            ps.setLong(1, from);
            ps.setLong(2, to);
            List<String> logins = new ArrayList<>();
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                logins.add(rs.getString(1));
            }
            rs.close();
            ps.close();
            if(logins.isEmpty()){
                return;
            }
            String where = logins.stream().map(e->"?").collect(Collectors.joining(",","(",")"));
            String preferSelect = "SELECT login, egrpou, company FROM billing_prefer_egrpou WHERE login IN " + where;
            ps = con.prepareStatement(preferSelect);
            int count = 1;
            Map<String, BillingEgpouInnHolder> loginMaxEgrpou = new HashMap<>();
            for (String login : logins) {
                ps.setString(count++, login);
            }
            rs = ps.executeQuery();
            while(rs.next()){
                loginMaxEgrpou.putIfAbsent(rs.getString(1), new BillingEgpouInnHolder(rs.getLong(2), null, rs.getString(3)));
            }
            rs.close();
            ps.close();
            logins = logins.stream().filter(login->!loginMaxEgrpou.containsKey(login)).collect(Collectors.toList());
            where = logins.stream().map(e->"?").collect(Collectors.joining(",","(",")"));
            String select = "SELECT login, egrpou, count(resource_id) cnt " +
                    "FROM sign_record" +
                    "  WHERE login in " + where +
                    " GROUP BY login, egrpou " +
                    "ORDER BY cnt DESC ";
            log.info("select:{}", select);
            ps = con.prepareStatement(select);
            count = 1;
            for (String login : logins) {
                ps.setString(count++, login);
            }
            rs = ps.executeQuery();
            while(rs.next()){
                loginMaxEgrpou.putIfAbsent(rs.getString(1), new BillingEgpouInnHolder(rs.getLong(2), null, null));
            }
            rs.close();
            ps.close();
            ps = con.prepareStatement("SELECT company FROM sign_record WHERE egrpou = ? LIMIT 1;");
            for (BillingEgpouInnHolder holder : loginMaxEgrpou.values()) {
                if(TextUtils.isEmpty(holder.company)){
                    ps.setLong(1, holder.egrpou);
                    ResultSet resultSet = ps.executeQuery();
                    if(resultSet.next()){
                        holder.company = resultSet.getString(1);
                    }
                }
            }
            ps.close();

            ps = con.prepareStatement(
                    "UPDATE billing_resources SET egrpou = ?, schema = 2, commit_time = ?, company = ? WHERE author = ? AND egrpou IS NULL AND inn IS NULL AND time>=? AND time<?");
            long time = System.currentTimeMillis();
            for (String login : loginMaxEgrpou.keySet()) {
                BillingEgpouInnHolder holder = loginMaxEgrpou.get(login);
                Long egrpou = holder.egrpou;
                ps.setLong(1, egrpou);
                ps.setLong(2, time);
                ps.setString(3, holder.company==null?"":holder.company);
                ps.setString(4, login);
                ps.setLong(5, from);
                ps.setLong(6, to);
                ps.executeUpdate(); //batch?
            }
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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

    public void checkAndSetEgrpouPrefer(long resourceId, String author) {
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT egrpou, company FROM billing_prefer_egrpou WHERE login = ?");
            ps.setString(1, author);
            ResultSet rs = ps.executeQuery();
            Long preferEgrpou;
            String company;
            if(rs.next()){
                preferEgrpou = rs.getObject(1, Long.class);
                company = rs.getString(2);
            }else{
                preferEgrpou = null;
                company = null;
            }
            rs.close();
            ps.close();
            if(preferEgrpou != null){
                ps = con.prepareStatement(
                        "UPDATE billing_resources SET egrpou = ?, schema = 4, commit_time = ?, company = ? WHERE resource_id = ?;");
                ps.setLong(1, preferEgrpou);
                ps.setLong(2, System.currentTimeMillis());
                if(company == null){
                    ps.setNull(3, Types.VARCHAR);
                }else{
                    ps.setString(3, company);
                }
                ps.setLong(4, resourceId);
                ps.executeUpdate();
            }
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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

    public Map<Long, BillingResponseDTO> getEgrpouListJur(long from, long to) {
        Map<Long, BillingResponseDTO> data = new HashMap<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT bs.egrpou, count(bs.resource_id), string_agg(DISTINCT bs.author, ','), string_agg(DISTINCT bs.company, ',') " +
                            "FROM billing_resources bs " +
                            "WHERE bs.time >= ? AND bs.time<? AND not inn = 0 and not egrpou = 0" +
                            "GROUP BY bs.egrpou");
            ps.setLong(1, from);
            ps.setLong(2, to);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                long egrpou = rs.getLong(1);
                BillingResponseDTO item = new BillingResponseDTO();
                item.egrpou = egrpou;
                item.count = rs.getLong(2);
                item.logins = Arrays.asList(rs.getString(3).split(","));
                item.company = rs.getString(4);
                data.put(egrpou, item);
            }
            rs.close();
            ps.close();
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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
        return data;
    }

    public Map<Long, BillingResponseDTO> getEgrpouListFiz(long from, long to) {
        Map<Long, BillingResponseDTO> data = new HashMap<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT bs.inn, count(bs.resource_id), string_agg(DISTINCT bs.author, ','), string_agg(DISTINCT bs.company, ',') " +
                            "FROM billing_resources bs " +
                            "WHERE bs.time >= ? AND bs.time<? AND NOT inn = 0 AND egrpou = 0" +
                            "GROUP BY bs.inn");
            ps.setLong(1, from);
            ps.setLong(2, to);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                long inn = rs.getLong(1);
                BillingResponseDTO item = new BillingResponseDTO();
                item.egrpou = -inn;
                item.inn = inn;
                item.count = rs.getLong(2);
                item.logins = Arrays.asList(rs.getString(3).split(","));
                item.company = rs.getString(4);
                data.put(item.egrpou, item);
            }
            rs.close();
            ps.close();
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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
        return data;
    }

    public List<BillingResponseDTO> getEgrpouListUnk(long from, long to) {
        List<BillingResponseDTO> data = new ArrayList<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT bs.egrpou, count(bs.resource_id), string_agg(DISTINCT bs.author, ','), string_agg(DISTINCT bs.company, ',') " +
                            "FROM billing_resources bs " +
                            "WHERE bs.time >= ? AND bs.time<? AND inn IS NULL AND egrpou IS NULL " +
                            "GROUP BY bs.egrpou");
            ps.setLong(1, from);
            ps.setLong(2, to);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                BillingResponseDTO item = new BillingResponseDTO();
                item.count = rs.getLong(2);
                item.logins = Arrays.asList(rs.getString(3).split(","));
                item.company = rs.getString(4);
                data.add(item);
            }
            rs.close();
            ps.close();
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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
        return data;
    }

    public Map<Long, BillingResponseDTO> getEgrpouListHz(long from, long to) {
        Map<Long, BillingResponseDTO> data = new HashMap<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT bs.egrpou, count(bs.resource_id), string_agg(DISTINCT bs.author, ','), string_agg(DISTINCT bs.company, ',') " +
                            "FROM billing_resources bs " +
                            "WHERE bs.time >= ? AND bs.time<? AND inn = 0  AND egrpou = 0 " +
                            "GROUP BY bs.egrpou");
            ps.setLong(1, from);
            ps.setLong(2, to);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                BillingResponseDTO item = new BillingResponseDTO();
                item.count = rs.getLong(2);
                item.logins = Arrays.asList(rs.getString(3).split(","));
                item.company = rs.getString(4);
                data.put(0L, item);
            }
            rs.close();
            ps.close();
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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
        return data;
    }

    public Map<Long, BillingResponseDTO> checkEgrpouBlock(final Map<Long, BillingResponseDTO> data){
        Connection con = getConnection();
        try{
            if(data==null || data.size() == 0){
                return data;
            }
            String where = data.keySet().stream().map(e->"?").collect(Collectors.joining(",","(",")"));
            String select = "SELECT id, type FROM blocked where id in " + where;
            PreparedStatement ps = con.prepareStatement(select);
            int i = 1;
            for (Long egrpou : data.keySet()) {
                ps.setLong(i++, egrpou);
            }
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                long egrpou = rs.getObject(1, Long.class);
                data.computeIfPresent(egrpou, (k,v)-> {v.blocked = true; return v;});
            }
            con.commit();
        }catch(Exception ex){
            log.error("error", ex);
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
        return data;
    }

    public List<BillingResponseDTO> getLoginList(long from, long to) {
        List<BillingResponseDTO> result = new ArrayList<>();
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT author, count(resource_id), string_agg(DISTINCT company, ',') " +
                            "  FROM billing_resources " +
                            " where time>=? and time<? " +
                            "GROUP BY author;");
            ps.setLong(1, from);
            ps.setLong(2, to);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                String author = rs.getString(1);
                long count = rs.getLong(2);
                String company = rs.getString(3);
                BillingResponseDTO dto = new BillingResponseDTO();
                dto.login = author;
                dto.count = count;
                dto.company = company;
                result.add(dto);
            }
            con.commit();
        } catch (Exception ex) {
            log.error("error", ex);
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
        return result;
    }

    public List<ResourceMiniDTO> getResourceDetailsByEGRPOU(Long egrpou, long from, long to) {
        List<ResourceMiniDTO> result = new ArrayList<>();
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT resource_id, name, author, time FROM billing_resources WHERE egrpou = ? AND time >= ? AND time<? AND not inn = 0");
            ps.setLong(1, egrpou);
            ps.setLong(2, from);
            ps.setLong(3, to);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                ResourceMiniDTO dto = new ResourceMiniDTO(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4));
                result.add(dto);
            }
            con.commit();
        } catch (Exception ex) {
            log.error("error", ex);
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
        return result;
    }

    public List<ResourceMiniDTO> getResourceDetailsByInn(Long inn, long from, long to) {
        List<ResourceMiniDTO> result = new ArrayList<>();
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT resource_id, name, author, time FROM billing_resources WHERE inn = ? AND time >= ? AND time<? AND egrpou = 0");
            ps.setLong(1, inn);
            ps.setLong(2, from);
            ps.setLong(3, to);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                ResourceMiniDTO dto = new ResourceMiniDTO(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4));
                result.add(dto);
            }
            con.commit();
        } catch (Exception ex) {
            log.error("error", ex);
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
        return result;
    }

    public List<ResourceMiniDTO> getResourceDetailsByLogin(String login, long from, long to) {
        List<ResourceMiniDTO> result = new ArrayList<>();
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT resource_id, name, author, time FROM billing_resources WHERE time >= ? AND time<? AND author = ? ");
            ps.setLong(1, from);
            ps.setLong(2, to);
            ps.setString(3, login);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                ResourceMiniDTO dto = new ResourceMiniDTO(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4));
                result.add(dto);
            }
            con.commit();
        } catch (Exception ex) {
            log.error("error", ex);
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
        return result;
    }

    public List<ResourceMiniDTO> getNullDetailsByEGRPOU(long from, long to) {
        List<ResourceMiniDTO> result = new ArrayList<>();
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT resource_id, name, author, time FROM billing_resources WHERE time >= ? AND time<? AND egrpou IS NULL AND inn IS NULL");
            ps.setLong(1, from);
            ps.setLong(2, to);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                ResourceMiniDTO dto = new ResourceMiniDTO(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4));
                result.add(dto);
            }
            con.commit();
        } catch (Exception ex) {
            log.error("error", ex);
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
        return result;
    }

    public List<UploadTrend> getUploadLoginTrends(long from, long to) {
        List<UploadTrend> result = new ArrayList<>();
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT extract(DAY FROM to_timestamp(time / 1000)) AS d, " +
                                "extract(MONTH FROM to_timestamp(time / 1000)) AS m, count(*) " +
                            "FROM billing_resources " +
                                "WHERE time>=? AND time <? " +
                            "GROUP BY m, d " +
                            "ORDER BY m, d;");
            ps.setLong(1, from);
            ps.setLong(2, to);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                UploadTrend ut = new UploadTrend(rs.getString(1), rs.getString(2), rs.getLong(3));
                result.add(ut);
            }
            con.commit();
        } catch (Exception ex) {
            log.error("error", ex);
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
        return result;
    }

    public void saveLoginToCatalogPb(String login, String initiator){
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("INSERT INTO catalog_pb(login, initiator, time) VALUES (?,?,?)");
            ps.setString(1, login);
            ps.setString(2, initiator);
            ps.setLong(3,System.currentTimeMillis());
            ps.executeUpdate();
            con.commit();
        } catch (Exception ex) {
            log.error("error", ex);
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

    public List<BillingResponseDTO> getTopNCompanies(long number, long from, long to) {
        List<BillingResponseDTO> result = new ArrayList<>();
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                        "SELECT bs.egrpou, count(bs.resource_id) AS count, string_agg(DISTINCT bs.author, ','), string_agg(DISTINCT bs.company, ',') " +
                                "FROM billing_resources bs " +
                                "WHERE bs.time >= ? AND bs.time<? AND NOT inn = 0 AND NOT egrpou = 0" +
                                "GROUP BY bs.egrpou " +
                                "ORDER BY count DESC " +
                                "LIMIT ?");
            ps.setLong(1, from);
            ps.setLong(2, to);
            ps.setLong(3, number);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                long egrpou = rs.getLong(1);
                BillingResponseDTO item = new BillingResponseDTO();
                item.egrpou = egrpou;
                item.count = rs.getLong(2);
                item.logins = Arrays.asList(rs.getString(3).split(","));
                item.company = rs.getString(4);
                result.add(item);
            }
            con.commit();
        } catch (Exception ex) {
            log.error("error", ex);
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
        return result;
    }

    public long getUserBilling(String login, Long from, Long to){
        long count = 0;
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "SELECT count(resource_id) " +
                        "FROM billing_resources " +
                        "WHERE author = ? AND time>= ? AND time< ? ");
            ps.setString(1, login);
            ps.setLong(2, from);
            ps.setLong(3, to);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                count = rs.getLong(1);
            }
            con.commit();
        } catch (Exception ex) {
            log.error("error", ex);
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
        return count;
    }

    public Map<Long, Long> getUserCompanyBilling(String login, long from, long to) {
        Map<Long, Long> egrpouResourcesCount = new HashMap<>();
        Connection con = getConnection();
        try {
            PreparedStatement ps = con.prepareStatement(
                    "WITH arr11 AS ( " +
                            "    SELECT egrpou " +
                            "    FROM users " +
                            "    WHERE login = ? " +
                            ") " +
                            "SELECT egrpou, count(resource_id) " +
                            "FROM billing_resources " +
                            "WHERE time>=? AND time<? AND (SELECT egrpou FROM arr11) @> array_append(NULL, egrpou::TEXT) " +
                            "  GROUP BY egrpou");
            ps.setString(1, login);
            ps.setLong(2, from);
            ps.setLong(3, to);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                egrpouResourcesCount.put(rs.getLong(1), rs.getLong(2));
            }
            con.commit();
        } catch (Exception ex) {
            log.error("error", ex);
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
        return egrpouResourcesCount;
    }
}
