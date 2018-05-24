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
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import ua.papka24.server.db.dao.DAO;

import java.io.IOException;
import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;


public class ResourceDTO implements Serializable {

    public final static int TYPE_PDF = 0;
    public final static int TYPE_SECURED_PDF = 1;
    private final static int TYPE_IMG = 10;
    private final static int UNKNOWN = -1;

    public static int detectType(String type){
        if (type.equals("application/pdf") || type.equals("application/pkcs7-signature")){
            return  TYPE_PDF;
        } else if(type.startsWith("image/")){
            return  TYPE_IMG;
        } else {
            return UNKNOWN;
        }
    }

    public static int STATUS_CREATED = 0;
    public static int STATUS_SHARED = 5;
    public static int STATUS_SHARED_ALL = 6;
    public static int STATUS_IN_TRASH = 10;
    public static int STATUS_DELETED = 20;

    private static final long serialVersionUID = 1L;
    public static TypeAdapter<ResourceDTO> gson = new Gson().getAdapter(ResourceDTO.class);
    private long id = DAO.NULL_ID;
    private String hash;
    private long size;
    private long time;
    private int type;
    private String src;
    private String name;
    private String author;
    private int status;
    private String[] signs;
    private long tags;
    private boolean signed;
    private Map<String,Integer> shares;
    private Long companyId;
    private boolean deleteByOwner;

    public static ResourceDTO init(ResultSet rs) throws SQLException{
        ResourceDTO res = new ResourceDTO(rs);
        res.setShares(new HashMap<>());
        return res;
    }

    public ResourceDTO(long id, String src, String hash, String[] signs){
        this.id = id;
        this.src = src;
        this.hash = hash;
        this.signs = signs;
    }

    public ResourceDTO(ResultSet rs) throws SQLException{
        this.id = rs.getLong(1);
        this.hash = rs.getString(2);
        this.src = rs.getString(3);
        this.name = rs.getString(4);
        this.type = rs.getInt(5);
        this.size = rs.getInt(6);
        this.time = rs.getLong(7);
        this.author = rs.getString(8);
        this.status = rs.getInt(9);
        this.tags = rs.getLong(10);
        this.signed = rs.getBoolean(11);
        this.companyId = rs.getObject(12, Long.class);
        this.deleteByOwner = this.status >= STATUS_DELETED;
    }

    public static ResourceDTO parseSearchV2Result(ResultSet rs) throws SQLException {
        ResourceDTO resource = new ResourceDTO(rs);
        Boolean deleteByCreator = rs.getObject(15, Boolean.class);
        if(deleteByCreator != null){
            resource.setDeleteByOwner(deleteByCreator);
        }else{
            resource.setDeleteByOwner(Boolean.FALSE);
        }
        return resource;
    }

    public ResourceDTO(ResultSet rs, boolean author) throws SQLException{
        this.id = rs.getLong(1);
        this.hash = rs.getString(2);
        this.src = rs.getString(3);
        this.name = rs.getString(4);
        this.type = rs.getInt(5);
        this.size = rs.getInt(6);
        this.author = rs.getString(8);
        if(author){
            this.time = rs.getLong(7);
            this.status = rs.getInt(9);
            this.tags = rs.getLong(10);
            this.signed = rs.getBoolean(11);
            this.companyId = rs.getObject(16, Long.class);
        }else{
            this.time = rs.getLong(14);
            this.status = rs.getInt(13);
            this.tags = rs.getLong(15);
            this.companyId = rs.getObject(17, Long.class);
            this.signed = this.status % 10 < ShareDTO.STATUS_SIGNED_BY_USERS;
        }
        this.deleteByOwner = rs.getInt(9) >= STATUS_DELETED;
    }


    /**
     * Create resource object
     * @param path relative path to file
     * @param hash hash from file
     * @param name users file name
     * @param type type
     * @param size file size
     * @param author username of author
     * @throws IOException
     */
    public ResourceDTO(String hash, String path, String name, int type, long size, String author, Long companyId) throws IOException {
        this.hash = hash;
        this.src = path;
        this.name = name;
        this.type = type;
        this.size = size;
        this.author = author;
        this.status = STATUS_CREATED;
        this.tags = 0L;
        if (Objects.equals(companyId,0L)){
            this.companyId = null;
        } else {
            this.companyId = companyId;
        }
    }

    private ResourceDTO(long resourceId){
        this.id = resourceId;
    }

    public static ResourceDTO parseSearchResult(ResultSet rs) throws SQLException {
        ResourceDTO res = new ResourceDTO(rs);
        res.deleteByOwner = rs.getLong(15) >= STATUS_DELETED;
        return res;
    }

    public long getSize() {
        return size;
    }
    public String getName() {
        return name;
    }
    public Integer getType() {
        return type;
    }
    public String getSrc() {
        return src;
    }
    public String getAuthor() {
        return author;
    }
    public void setAuthor(String user) {
        this.author = user;
    }
    public String getHash() {
        return hash;
    }

    public int getStatus() {
        return status;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setSigns(String[] signs) {
        this.signs = signs;
    }

    public long getId() {
        return id;
    }

    public void setShares(Map<String, Integer> shares) {
        this.shares = shares;
    }

    public void setTags(long tags) {
        this.tags = tags;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public long getTime() {
        return time;
    }

    public Map<String, Integer> getShares() {
        return shares;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public boolean isDeleteByOwner() {
        return deleteByOwner;
    }

    public void setDeleteByOwner(boolean deleteByOwner) {
        this.deleteByOwner = deleteByOwner;
    }

    public long getTags() {
        return tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (!(o instanceof ResourceDTO)) return false;

        ResourceDTO that = (ResourceDTO) o;

        return new EqualsBuilder()
                .append(id, that.id)
                .append(size, that.size)
                .append(time, that.time)
                .append(type, that.type)
                .append(hash, that.hash)
                .append(author, that.author)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(id)
                .append(hash)
                .append(size)
                .append(time)
                .append(type)
                .append(author)
                .toHashCode();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ResourceDTO{");
        sb.append("id=").append(id);
        sb.append(", hash='").append(hash).append('\'');
        sb.append(", size=").append(size);
        sb.append(", time=").append(time);
        sb.append(", type=").append(type);
        sb.append(", src='").append(src).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append(", author='").append(author).append('\'');
        sb.append(", status=").append(status);
        sb.append(", signs=").append(Arrays.toString(signs));
        sb.append(", tags=").append(tags);
        sb.append(", signed=").append(signed);
        sb.append(", shares=").append(shares);
        sb.append(", companyId=").append(companyId);
        sb.append(", deleteByOwner=").append(deleteByOwner);
        sb.append('}');
        return sb.toString();
    }

    public static ResourceDTO emptyResource(long id) throws IOException {
        return new ResourceDTO(id);
    }

    public String[] getSigns() {
        return signs;
    }

    public void setSigned(boolean signed) {
        this.signed = signed;
    }
}


