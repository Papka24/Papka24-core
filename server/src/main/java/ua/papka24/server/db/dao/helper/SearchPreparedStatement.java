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

import freemarker.template.Template;
import freemarker.template.TemplateException;
import ua.papka24.server.db.dto.FilterDTO;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

abstract class SearchPreparedStatement {

    private static final Map<String,Object> noBoss = new HashMap<String,Object>(){{put("isMy",Boolean.TRUE);}};
    private static final Map<String,Object> yesBoss = new HashMap<String,Object>(){{put("isMy",Boolean.FALSE);}};

    PreparedStatement processTemplate(Template template, Connection con, boolean isBoss, FilterDTO filter, Long companyId) throws IOException, TemplateException, SQLException {
        StringWriter outString = new StringWriter();
        Map<String, Object> prop;
        if(isBoss) {
            prop = new HashMap<String,Object>(){{putAll(yesBoss);}};
        }else{
            prop = new HashMap<String,Object>(){{putAll(noBoss);}};
        }
        prop.put("dateTo", false);
        prop.put("dateFrom", false);
        prop.put("searchQuery",false);
        prop.put("tags", false);
        prop.put("companyId", companyId == null);
        if(filter!=null) {
            if(filter.getTagFilter()!=null){
                prop.put("tags", true);
            }else{
                prop.put("tags", false);
            }
            if (filter.dateFrom != null && filter.dateTo != null) {
                prop.put("dateFrom", true);
                prop.put("dateTo", true);
            } else if (filter.dateFrom != null) {
                prop.put("dateFrom", true);
            } else if (filter.dateTo != null) {
                prop.put("dateTo", true);
            }
            if(filter.isSigned()!=null){
                prop.put("signedStatus", filter.isSigned());
            }
            if(filter.getSearchQuery()!=null && !filter.getSearchQuery().isEmpty()){
                prop.put("searchQuery",true);
            }
            if(filter.getContractor() != null){
                prop.put("contractor", filter.getContractor());
            }
        }
        template.process(prop, outString);
        String result = outString.toString();
        //LoggerFactory.getLogger("test").info("SQL:\n{}", result);
        outString.close();
        return con.prepareStatement(result);
    }
}
