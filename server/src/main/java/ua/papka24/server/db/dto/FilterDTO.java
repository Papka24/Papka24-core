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

package ua.papka24.server.db.dto;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import org.apache.http.util.TextUtils;

import java.io.Serializable;
import java.util.Calendar;
import java.util.List;


public class FilterDTO implements Serializable{
    public static TypeAdapter<FilterDTO> gson = new Gson().getAdapter(FilterDTO.class);

    public static final int MAX_LIMIT = 1000;
    public String searchQuery;
    public List<String> contractor;
    public String author;
    public Long dateFrom, dateTo;
    private String user;
    private String docList;
    private Long tagFilter;
    private Boolean signed;

    // Лимиты поиска
    private Integer limit = 1000;
    private Integer page = 0;
    private Integer offset = 0;

    //варианты docList
    public enum DocList{
        trash, docs, tag
    }

    public static boolean isFilterCorrect(FilterDTO filter){
        if(filter.dateTo!=null && filter.dateTo<=0){
            return false;
        }
        if(filter.dateFrom!=null && filter.dateFrom<=0){
            return false;
        }
        if(filter.dateFrom!=null && filter.dateTo!=null && filter.dateFrom>filter.dateTo){
            return false;
        }
        if(filter.limit!=null && filter.limit<=0){
            return false;
        }
        if(filter.offset!=null && filter.offset<0){
            return false;
        }
        if(filter.page!=null && filter.page<0){
            return false;
        }
        if(filter.tagFilter!=null && filter.tagFilter<0){
            return false;
        }
        if(!TextUtils.isEmpty(filter.searchQuery)) {
            filter.searchQuery = filter.searchQuery.replace('<',' ').replace('>',' ');
        }
        return true;
    }

    public Boolean isSigned() {
        return signed;
    }

    public Long getDateFrom() {
        return this.dateFrom;
    }

    public void setDateFrom(Long dateFrom) {
        this.dateFrom = dateFrom;
    }

    public Long getDateTo(){
        if(this.dateTo!=null) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(this.dateTo);
            cal.add(Calendar.DAY_OF_YEAR, 1);
            return cal.getTimeInMillis();
        }else return null;
    }

    public void setDateTo(Long dateTo) {
        this.dateTo = dateTo;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getDocList() {
        return docList;
    }

    public void setDocList(String docList) {
        this.docList = docList;
    }

    public Integer getLimit() {
        if(this.limit>MAX_LIMIT){
            return MAX_LIMIT;
        }else {
            return limit;
        }
    }

    public void setLimit(Integer limit) {
        if(limit>MAX_LIMIT){
            this.limit = MAX_LIMIT;
        }else {
            this.limit = limit;
        }
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public boolean isDateFilter(){
        return (dateFrom>0 && dateTo>0  && (dateFrom < dateTo));
    }

    public boolean isUserFilter(){
        return user != null && user.trim().length() > 0;
    }

    public boolean isPageLimitFilter(){
        return (limit & page) > 0;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public List<String> getContractor() {
        return contractor;
    }

    public void setContractor(List<String> contractor) {
        this.contractor = contractor;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Long getTagFilter() {
        return tagFilter;
    }

    public void setTagFilter(Long tagFilter) {
        this.tagFilter = tagFilter;
    }

    public Integer getOffset() {
        return offset;
    }

    public void setOffset(Integer offset) {
        this.offset = offset;
    }

    @Override
    public String toString() {
        return "FilterDTO{" + "searchQuery='" + searchQuery + '\'' +
                ", contractor=" + contractor +
                ", author='" + author + '\'' +
                ", dateFrom=" + dateFrom +
                ", dateTo=" + dateTo +
                ", user='" + user + '\'' +
                ", docList='" + docList + '\'' +
                ", tagFilter=" + tagFilter +
                ", limit=" + limit +
                ", page=" + page +
                ", offset=" + offset +
                '}';
    }
}
