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

package ua.papka24.server.db.dao.helper;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import ua.papka24.server.db.dto.FilterDTO;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;


public class SearchPreparedStatementV1 extends SearchPreparedStatement{

    private Configuration cfg;
    private static final Map<String,Object> noBoss = new HashMap<String,Object>(){{put("isMy",Boolean.TRUE);}};
    private static final Map<String,Object> yesBoss = new HashMap<String,Object>(){{put("isMy",Boolean.FALSE);}};

    private SearchPreparedStatementV1(){
        cfg = new Configuration(Configuration.VERSION_2_3_23);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setClassForTemplateLoading(this.getClass(), "/sql/script");
        cfg.setLogTemplateExceptions(false);
    }

    private static class Singleton {
        private static final SearchPreparedStatementV1 HOLDER_INSTANCE = new SearchPreparedStatementV1();
    }

    public static SearchPreparedStatementV1 getInstance() {
        return SearchPreparedStatementV1.Singleton.HOLDER_INSTANCE;
    }

    public PreparedStatement getTrashAll(Connection con, String login, Long companyId, boolean isBoss, FilterDTO filter) throws IOException, TemplateException, SQLException {
        Template template = cfg.getTemplate("trash_all.sql");
        PreparedStatement ps = processTemplate(template, con, isBoss, filter, companyId);
        ps = fillStart(ps, login);
        ps = getAll(ps, login, companyId, filter);
        return ps;
    }

    public PreparedStatement getStd(Connection con, String login, Long companyId, boolean isBoss, FilterDTO filter) throws IOException, TemplateException, SQLException {
        Template template = cfg.getTemplate("std.sql");
        PreparedStatement ps = processTemplate(template, con, isBoss, filter, companyId);
        ps = fillStart(ps, login);
        ps = getAll(ps, login, companyId, filter);
        return ps;
    }

    private int fillAllBlock(PreparedStatement ps, int i, String login, Long companyId, FilterDTO filter) throws SQLException {
        ps.setString(i++, login);
        if(companyId==null){
            ps.setNull(i++, Types.BIGINT);
        }else{
            ps.setLong(i++, companyId);
        }
        if (filter!=null && filter.getTagFilter() != null) {
            ps.setLong(i++, 1 << filter.getTagFilter());
        }
        if(filter!=null) {
            Long dateFrom = filter.getDateFrom();
            Long dateTo = filter.getDateTo();
            if (dateFrom != null && dateTo != null) {
                ps.setLong(i++, dateFrom);
                ps.setLong(i++, dateTo);
            } else if (dateFrom != null) {
                ps.setLong(i++, dateFrom);
            } else if (dateTo != null) {
                ps.setLong(i++, dateTo);
            }
        }
        return i;
    }

    private PreparedStatement getAll(PreparedStatement ps, String login, Long companyId, FilterDTO filter) throws SQLException {
        int i = 10;
        //r
        i = fillAllBlock(ps, i, login, companyId, filter);
        //s
        i = fillAllBlock(ps, i, login, companyId, filter);
        i = setSearchQuery(filter, ps, i);
        i = setIsSignedBlock(filter, i, ps, login);
        ps = setOffsetLimit(filter, i, ps);
        return ps;
    }

    private int setIsSignedBlock(FilterDTO filter, int i, PreparedStatement ps, String login) throws SQLException {
        if (filter != null && filter.isSigned() != null) {
            ps.setString(i++, login);
            ps.setString(i++, login);
        }
        return i;
    }

    private int setSearchQuery(FilterDTO filter, PreparedStatement ps, int i) throws SQLException {
        if(filter==null){
            return i;
        }
        String searchQuery = filter.getSearchQuery();
        if(searchQuery !=null && !searchQuery.isEmpty()){
            searchQuery = "%" + searchQuery + "%";
            ps.setString(i++, searchQuery);
            ps.setString(i++, searchQuery);
            ps.setString(i++, searchQuery);
        }else{
            return i;
        }
        return i;
    }

    private PreparedStatement setOffsetLimit(FilterDTO filter, int i, PreparedStatement ps) throws SQLException {
        long limit = 1000;
        long offset = 0;
        if(filter!=null && filter.getLimit()!=null && (filter.getPage()!=null || filter.getOffset()!=null)){
            limit = filter.getLimit();
            if(filter.getOffset()!=null){
                offset = filter.getOffset();
            }
            else if(filter.getPage()!=null){
                offset = filter.getPage() * filter.getLimit();
            }else{
                offset = 0;
            }
        }
        ps.setLong(i++,  limit);
        ps.setLong(i,  offset);
        return ps;
    }

    private PreparedStatement getMeMy(PreparedStatement ps, String login, Long companyId, FilterDTO filter) throws IOException, TemplateException, SQLException {
        int i = 10;
        i = fillAllBlock(ps, i, login, companyId, filter);
        i = setSearchQuery(filter, ps, i);
        ps = setOffsetLimit(filter, i, ps);
        return ps;
    }

    public PreparedStatement getTrashMy(Connection con, String login, Long companyId, boolean isBoss, FilterDTO filter) throws IOException, TemplateException, SQLException {
        Template template = cfg.getTemplate("trash_my.sql");
        PreparedStatement ps = processTemplate(template, con, isBoss, filter, companyId);
        ps = fillStart(ps, login);
        ps = getMeMy(ps, login, companyId, filter);
        return ps;
    }

    public PreparedStatement getTrashMe(Connection con, String login, Long companyId, boolean isBoss, FilterDTO filter) throws IOException, TemplateException, SQLException {
        Template template = cfg.getTemplate("trash_me.sql");
        PreparedStatement ps = processTemplate(template, con, isBoss, filter, companyId);
        ps = fillStart(ps, login);
        ps = getMeMy(ps, login, companyId, filter);
        return ps;
    }

    public PreparedStatement getDocAll(Connection con, String login, Long companyId, boolean isBoss, FilterDTO filter) throws IOException, TemplateException, SQLException {
        Template template = cfg.getTemplate("docs_all.sql");
        PreparedStatement ps = processTemplate(template, con, isBoss, filter, companyId);
        ps = fillStart(ps, login);
        ps = getAll(ps, login, companyId, filter);
        return ps;
    }

    public PreparedStatement getDocMy(Connection con, String login, Long companyId, boolean isBoss, FilterDTO filter) throws IOException, TemplateException, SQLException {
        Template template = cfg.getTemplate("docs_my.sql");
        PreparedStatement ps = processTemplate(template, con, isBoss, filter, companyId);
        ps = fillStart(ps, login);
        ps = getMeMy(ps, login, companyId, filter);
        return ps;
    }

    public PreparedStatement getDocMe(Connection con, String login, Long companyId, boolean isBoss, FilterDTO filter) throws IOException, TemplateException, SQLException {
        Template template = cfg.getTemplate("docs_me.sql");
        PreparedStatement ps = processTemplate(template, con, isBoss, filter, companyId);
        ps = fillStart(ps, login);
        ps = getMeMy(ps, login, companyId, filter);
        return ps;
    }

    private PreparedStatement fillStart(PreparedStatement ps, String login) throws SQLException {
        for(int i = 1; i <= 9; i++){
            ps.setString(i, login);
        }
        return ps;
    }
}
