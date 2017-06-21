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

import java.sql.ResultSet;
import java.sql.SQLException;


public class EmployeeDTO {

    public static final long ROLE_UNKNOWN = -1L;
    public static final long ROLE_ADMIN  = 0L;
    public static final long ROLE_WORKER = 1L;
    public static final int STATUS_INVITED = 0;
    public static final int STATUS_ACCEPTED = 1;
    public static final int STATUS_REJECTED = 2;
    public static final int STATUS_FIRED    = 3;

    private String login;
    private Long companyId;
    private long role;
    private long startDate;
    private long stopDate;
    private long status;
    private String initiator;
    private String removeInitiator;

    public EmployeeDTO(String login, Long companyId, long role, long startDate, long stopDate, long status, String initiator) {
        this.login = login;
        this.companyId = companyId;
        this.role = role;
        this.startDate = startDate;
        this.stopDate = stopDate;
        this.status = status;
        this.initiator = initiator;
    }

    public EmployeeDTO(ResultSet rs) throws SQLException {
        this.companyId = rs.getLong(1);
        this.login = rs.getString(2);
        this.role = rs.getLong(3);
        this.startDate = rs.getLong(4);
        this.stopDate = rs.getLong(5);
        this.status = rs.getLong(6);
        this.initiator = rs.getString(7);
        this.removeInitiator = rs.getString(8);
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public long getRole() {
        return role;
    }

    public void setRole(long role) {
        this.role = role;
    }

    public void setStatus(long status) {
        this.status = status;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public long getStopDate() {
        return stopDate;
    }

    public void setStopDate(long stopDate) {
        this.stopDate = stopDate;
    }

    public long getStatus() {
        return status;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EmployeeDTO{");
        sb.append("login='").append(login).append('\'');
        sb.append(", companyId=").append(companyId);
        sb.append(", role=").append(role);
        sb.append(", startDate=").append(startDate);
        sb.append(", stopDate=").append(stopDate);
        sb.append(", status=").append(status);
        sb.append('}');
        return sb.toString();
    }
}