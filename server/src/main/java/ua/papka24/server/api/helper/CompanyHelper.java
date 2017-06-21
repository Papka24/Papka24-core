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

package ua.papka24.server.api.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.api.Resource;
import ua.papka24.server.db.dao.CompanyDAO;
import ua.papka24.server.db.dao.ResourceDAO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.Main;
import ua.papka24.server.db.dto.FilterDTO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.security.Session;
import ua.papka24.server.utils.exception.ServerException;

import java.util.List;
import java.util.Objects;


public class CompanyHelper {

    private static final Logger log = LoggerFactory.getLogger(Resource.class);

    public static List<ResourceDTO> searchInCompany(Session managerSession, Long companyId) throws ServerException {
        UserDTO manager = managerSession.getUser();
        if (!Objects.equals(companyId, manager.getCompanyId()) || !CompanyDAO.getInstance().getAdmins(companyId).contains(manager.getLogin())) {
            throw new ServerException(423);
        }
        List<ResourceDTO> list = ResourceDAO.getInstance().searchByCompany(companyId);
        if (list == null) {
            throw new ServerException(404);
        }
        return list;
    }

    public static List<ResourceDTO> search(UserDTO user, String employee, FilterDTO filter){
        List<ResourceDTO> result;
        if (employee == null || employee.isEmpty() || (user.getCompanyDTO()!=null && !user.getCompanyDTO().hasEmployee(employee))) {
            result = ResourceDAO.getInstance().search(filter, user.getLogin(), null, user.getCompanyId());
            if(Main.property.getProperty("useSearchMirror","false").equals("false")) {
                result = ResourceDAO.getInstance().filter(filter, result);
            }
        } else {
            result = ResourceDAO.getInstance().companySearch(filter, user, employee);
        }
        return result;
    }
}
