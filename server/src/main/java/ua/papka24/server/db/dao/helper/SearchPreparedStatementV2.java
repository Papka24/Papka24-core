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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dto.FilterDTO;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;

public class SearchPreparedStatementV2 extends SearchPreparedStatement{

    private static final Logger log = LoggerFactory.getLogger(SearchPreparedStatementV2.class);
    private Configuration cfg;

    private SearchPreparedStatementV2(){
        cfg = new Configuration(Configuration.VERSION_2_3_25);
        cfg.setDefaultEncoding("UTF-8");
        cfg.setClassForTemplateLoading(this.getClass(), "/sql/script/v2");
        cfg.setLogTemplateExceptions(false);
    }

    private static class Singleton {
        private static final SearchPreparedStatementV2 HOLDER_INSTANCE = new SearchPreparedStatementV2();
    }

    public static SearchPreparedStatementV2 getInstance() {
        return SearchPreparedStatementV2.Singleton.HOLDER_INSTANCE;
    }

    public PreparedStatement getPreparedStatement(String docList, String authorType, Connection con, String login, Long companyId, boolean isBoss, FilterDTO filter) throws IOException, TemplateException, SQLException {
        if(StringUtils.isEmpty(authorType) && StringUtils.isEmpty(docList)){
            return getPreparedStatement(con, login, companyId, isBoss, filter);
        }
        if(StringUtils.isEmpty(authorType)){
            authorType = "all";
        }
        if(docList.equalsIgnoreCase("tag")){
            docList = "docs";
        }
        String templateName = docList.toLowerCase()+"_"+authorType.toLowerCase()+".sql";
        log.info("use template {} for user {} search", templateName, login);
        Template template = cfg.getTemplate(templateName);
        PreparedStatement ps = processTemplate(template, con, isBoss, filter, companyId);
        setValues(ps, login, companyId, isBoss, filter);
        return ps;
    }

    private PreparedStatement getPreparedStatement(Connection con, String login, Long companyId, boolean isBoss, FilterDTO filter) throws IOException, TemplateException, SQLException {
        Template template = cfg.getTemplate("std.sql");
        if(filter.getContractor()!=null) {
            filter.setContractor(filter.getContractor().stream().filter(e -> !StringUtils.isEmpty(e)).collect(Collectors.toList()));
        }
        PreparedStatement ps = processTemplate(template, con, isBoss, filter, companyId);
        setValues(ps, login, companyId, isBoss, filter);
        return ps;
    }

    private PreparedStatement setValues(PreparedStatement ps, String login, Long companyId, boolean isBoss, FilterDTO filter) throws SQLException {
        int position = 1;
        ps.setString(position++, login);
        ps.setString(position++, login);
        Long dateFrom = filter.getDateFrom();
        Long dateTo = filter.getDateTo();
        if(dateFrom != null){
            ps.setLong(position++, dateFrom);
        }
        if(dateTo != null){
            ps.setLong(position++, dateTo);
        }
        if(companyId == null){
            ps.setNull(position++, Types.BIGINT);
        }else{
            ps.setLong(position++, companyId);
        }
        if(filter.getTagFilter() != null){
            ps.setLong(position++, 1 << filter.getTagFilter());
        }
        if(!StringUtils.isEmpty(filter.getSearchQuery())){
            String searchQuery = filter.getSearchQuery();
            ps.setString(position++, "%"+searchQuery+"%");
            ps.setString(position++, "%"+searchQuery+"%");
            ps.setString(position++, "%"+searchQuery+"%");
        }
        long limit = 1000;
        long offset = 0;
        if(filter.getLimit() != null && (filter.getPage() != null || filter.getOffset() != null)){
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
        if(filter.getContractor()!=null){
            List<String> contractor = filter.getContractor();
            for (String name : contractor) {
                ps.setString(position++, "%"+name+"%");
                ps.setString(position++, "%"+name+"%");
                ps.setString(position++, "%"+name+"%");
            }
        }
        ps.setLong(position++,  limit);
        ps.setLong(position++,  offset);
        return ps;
    }
}
