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

package ua.papka24.server.api.DTO;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import ua.papka24.server.db.dao.CompanyDAO;
import ua.papka24.server.db.dto.EmployeeDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CompanyDTO {
    public static TypeAdapter<CompanyDTO> gson = new Gson().getAdapter(CompanyDTO.class);

    private Long companyId;
    private String name;
    private Long egrpou;
    private List<EmployeeDTO> employee = new ArrayList<>();

    public CompanyDTO(Long companyId, String companyName, List<EmployeeDTO> employee) {
        this.companyId = companyId;
        this.name = companyName;
        this.employee = employee;
    }

    public static CompanyDTO mockCompany(){
        CompanyDTO companyDTO = new CompanyDTO(Long.MIN_VALUE, "mock company", Collections.emptyList());
        companyDTO.egrpou = Long.MIN_VALUE;
        return companyDTO;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getName() {
        return name;
    }

    public List<EmployeeDTO> getEmployee() {
        return employee;
    }

    public void setEmployee(List<EmployeeDTO> employee) {
        this.employee = employee;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CompanyInfo{");
        sb.append("companyId=").append(companyId);
        sb.append(", name='").append(name).append('\'');
        sb.append(", egrpou=").append(egrpou);
        sb.append(", employee=").append(employee);
        sb.append('}');
        return sb.toString();
    }

    /**
     * проверка что пользователь когда либо ассоциировался с компанией.
     * @param user
     * @return
     */
    public boolean hasEmployee(String user) {
        return employee.stream().anyMatch(e->e.getLogin().equals(user));
    }

    /**
     * проверка что user на данный момент работает в компании
     * @param user
     * @return
     */
    public boolean haveEmployee(String user){
        if(employee == null){
            return CompanyDAO.getInstance().haveEmployee(this.companyId, user);
        }
        return employee.stream().anyMatch(e->e.getLogin().equals(user) && e.getStatus() == EmployeeDTO.STATUS_ACCEPTED);
    }

    public boolean haveBoss(String user) {
        if(employee == null){
            return CompanyDAO.getInstance().haveBoss(this.companyId, user);
        }
        return employee.stream().anyMatch(e->e.getLogin().equals(user)  && e.getRole()== EmployeeDTO.ROLE_ADMIN && e.getStatus() == EmployeeDTO.STATUS_ACCEPTED);
    }

    public void setCompanyName(String companyName) {
        this.name = companyName;
    }
}
