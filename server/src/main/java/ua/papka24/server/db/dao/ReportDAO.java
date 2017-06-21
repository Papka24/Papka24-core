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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;


public class ReportDAO extends DAO {
    private static final Logger log = LoggerFactory.getLogger(ReportDAO.class);

    private ReportDAO(){}

    private static class Singleton {
        private static final ReportDAO HOLDER_INSTANCE = new ReportDAO();
    }

    public static ReportDAO getInstance() {
        return ReportDAO.Singleton.HOLDER_INSTANCE;
    }

    public List<InnEgrpouHolder> getExtrAddrId(long from, long to){
        Connection con = getConnection();
        List<InnEgrpouHolder> result = null;
        try{
            Map<Long, List<Long>> resourceEgrpou = new HashMap<>();
            PreparedStatement getJurList = con.prepareStatement(
                    "SELECT resource_id, array_agg(egrpou) " +
                    "FROM sign_record " +
                    "WHERE time >=? AND time <? AND egrpou IS NOT NULL AND NOT egrpou = 0 " +
                    "GROUP BY resource_id");
            getJurList.setLong(1, from);
            getJurList.setLong(2, to);
            ResultSet rs = getJurList.executeQuery();
            while(rs.next()){
                long resourceId = rs.getLong(1);
                List<Long> egrpouList = null;
                Array array = rs.getArray(2);
                if(array!=null){
                    egrpouList = Arrays.asList((Long[])array.getArray());
                }
                resourceEgrpou.put(resourceId, egrpouList);
            }
            rs.close();
            String where = resourceEgrpou.keySet().stream().map(e->"?").collect(Collectors.joining(",","(",")"));
            PreparedStatement innPs = con.prepareStatement(
                    "SELECT resource_id, inn " +
                    " FROM sign_record " +
                    " WHERE time >= ? AND time < ? " +
                    " AND NOT inn = 0 AND egrpou = 0 AND resource_id IN "+where +
                    " GROUP BY resource_id, inn ");
            innPs.setLong(1, from);
            innPs.setLong(2, to);
            int i = 3;
            for (Long resourceId : resourceEgrpou.keySet()) {
                innPs.setLong(i++, resourceId);
            }
            rs = innPs.executeQuery();
            result = new ArrayList<>();
            while(rs.next()){
                long resourceId = rs.getLong(1);
                long inn = rs.getLong(2);
                List<Long> egrpous = resourceEgrpou.get(resourceId);
                if(egrpous!=null){
                    for(Long egrpou : egrpous){
                        InnEgrpouHolder innEgrpouHolder = new InnEgrpouHolder(inn, egrpou);
                        if(!result.contains(innEgrpouHolder)) {
                            result.add(innEgrpouHolder);
                        }
                    }
                }
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

    public static class InnEgrpouHolder{
        public long inn;
        public long egrpou;

        InnEgrpouHolder(long inn, Long egrpou) {
            this.inn = inn;
            this.egrpou = egrpou;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            InnEgrpouHolder that = (InnEgrpouHolder) o;
            return inn == that.inn &&
                    egrpou == that.egrpou;
        }

        @Override
        public int hashCode() {
            return Objects.hash(inn, egrpou);
        }
    }
}
