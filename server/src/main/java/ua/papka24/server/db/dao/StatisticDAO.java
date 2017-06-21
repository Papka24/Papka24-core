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

import ua.papka24.server.db.dto.statistic.StatisticCount;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;


public class StatisticDAO extends DAO {
    private StatisticDAO(){}

    private static class Singleton {
        private static final StatisticDAO HOLDER_INSTANCE = new StatisticDAO();
    }

    public static StatisticDAO getInstance() {
        return StatisticDAO.Singleton.HOLDER_INSTANCE;
    }

    public boolean saveDayStatistic(int year, String day, Long userTrends, Long docTrends, Long shareTrends, Long signTrends){
        boolean res = false;
        Connection con = getConnection();
        try{
            String date = year+day;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO statistic_trends (day, users, docs, shares, signs, day_start) VALUES (?,?,?,?,?,?) ");
            ps.setString(1, date);
            if(userTrends!=null){
                ps.setLong(2, userTrends);
            }else{
                ps.setNull(2, Types.BIGINT);
            }
            if(docTrends!=null){
                ps.setLong(3, docTrends);
            }else{
                ps.setNull(3, Types.BIGINT);
            }
            if(shareTrends!=null){
                ps.setLong(4, shareTrends);
            }else{
                ps.setNull(4, Types.BIGINT);
            }
            if(signTrends!=null){
                ps.setLong(5, signTrends);
            }else{
                ps.setNull(5, Types.BIGINT);
            }
            ps.setLong(6, sdf.parse(date).getTime());
            res = ps.executeUpdate() > 0;
            con.commit();
        } catch (Exception e) {
            log.error("error",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public StatisticCount getStatisticCount(){
        StatisticCount sc = null;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT resource_count, users_count, share_count, sign_count FROM statistic");
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                sc = new StatisticCount(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4));
            }else{
                sc = new StatisticCount();
            }
            con.commit();
        } catch (Exception e) {
            log.error("error",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return sc;
    }

       /**
     * возврат trends по архиву (без учета текущего дня)
     * @param startTime
     * @return
     */
    public Map<String, StatisticCount> getStatisticTrends(Long startTime){
        Map<String, StatisticCount> result = new TreeMap<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT day, users, docs, shares, signs FROM statistic_trends WHERE day_start >= ? ");
            ps.setLong(1, startTime);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                StatisticCount sc = new StatisticCount(rs.getLong(3), rs.getLong(2), rs.getLong(4), rs.getLong(5));
                result.put(rs.getString(1), sc);
            }
            con.commit();
        } catch (Exception e) {
            log.error("error",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    private void proc(Map<String, StatisticCount> archiveData, Map<String, Long> trends, int type){
        Calendar cal = Calendar.getInstance();
        String year  = String.valueOf(cal.get(Calendar.YEAR));
        for (String date : trends.keySet()) {
            Long count = trends.getOrDefault(date, 0L);
            date = year + date;
            StatisticCount statisticCount = archiveData.get(date);
            if(statisticCount==null){
                StatisticCount sc = new StatisticCount();
                archiveData.put(date, sc);
                statisticCount = sc;
            }
            switch (type){
                case 0 :{
                    statisticCount.resourceCount += count;
                    break;
                }
                case 1 :{
                    statisticCount.userCount += count;
                    break;
                }
                case 2 :{
                    statisticCount.shareCount += count;
                    break;
                }
                case 3 :{
                    statisticCount.signCount += count;
                    break;
                }
            }
        }
    }

    public void updateCounters(long resourcesCount, long usersCount, long sharesCount, long signCount) {
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "UPDATE statistic SET resource_count = resource_count + ?, " +
                            "share_count = share_count + ?, " +
                            "sign_count = sign_count + ?, " +
                            "users_count = users_count + ? ");
            ps.setLong(1, resourcesCount);
            ps.setLong(2, sharesCount);
            ps.setLong(3, signCount);
            ps.setLong(4, usersCount);
            ps.executeUpdate();
            con.commit();
        } catch (Exception e) {
            log.error("error",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
    }

    public void prepareTables() {
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("truncate table statistic");
            ps.executeUpdate();
            ps = con.prepareStatement("truncate table statistic_trends");
            ps.executeUpdate();
            ps = con.prepareStatement("INSERT INTO statistic VALUES (0, 0, 0, 0)");
            ps.executeUpdate();
            con.commit();
        } catch (Exception e) {
            log.error("error",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
    }
}