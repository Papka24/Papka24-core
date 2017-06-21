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

import org.apache.commons.lang3.StringUtils;
import ua.papka24.server.db.dto.EmployeeDTO;
import ua.papka24.server.api.DTO.CompanyDTO;
import ua.papka24.server.db.dto.UserDTO;
import ua.papka24.server.db.dto.reginfo.FirstCompanyInvite;

import java.sql.*;
import java.util.*;
import java.util.Date;


public class CompanyDAO extends DAO {

    private CompanyDAO(){}

    private static class Singleton {
        private static final CompanyDAO HOLDER_INSTANCE = new CompanyDAO();
    }

    public static CompanyDAO getInstance() {
        return CompanyDAO.Singleton.HOLDER_INSTANCE;
    }

    public CompanyDTO createCompany(UserDTO user, String companyName) {
        Connection c = getConnection();
        assert c != null;
        long id;
        try {
            PreparedStatement ps;

            ps = c.prepareStatement("INSERT INTO companies (name) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, companyName);
            if (ps.executeUpdate() > 0) {
                ResultSet rs = ps.getGeneratedKeys();
                rs.next();
                id = rs.getLong(1);
                ps = c.prepareStatement("INSERT INTO employees (company_id, login, role, start_date, status) VALUES (?,?,?,?,?)");
                ps.setLong(1, id);
                ps.setString(2, user.getLogin());
                ps.setLong(3, EmployeeDTO.ROLE_ADMIN);
                ps.setLong(4, new java.util.Date().getTime());
                ps.setLong(5, EmployeeDTO.STATUS_ACCEPTED);
                if (ps.executeUpdate() == 0) {
                    return null;
                }
            } else {
                return null;
            }
            c.commit();
            List<EmployeeDTO> employee = new ArrayList<>();
            EmployeeDTO empl = new EmployeeDTO(user.getLogin(),id,EmployeeDTO.ROLE_ADMIN, new java.util.Date().getTime(),0,EmployeeDTO.STATUS_ACCEPTED, null);
            employee.add(empl);
            return new CompanyDTO(id, companyName, employee);
        } catch (Exception e) {
            log.error("Can't create company {}", companyName, e);
            try {
                c.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        } finally {
            try {
                c.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return null;
    }

    public Map<String,Long> getUsersCompany(String[] usersEmail) {
        Map<String,Long> result = new HashMap<>();
        Connection con = getConnection();
        try{
            StringBuilder builder = new StringBuilder();
            for (int i = usersEmail.length; i > 0; i--) {
                builder.append("?,");
            }
            PreparedStatement ps = con.prepareStatement("SELECT u.login, u.company_id FROM users AS u WHERE u.login IN (" + builder.deleteCharAt(builder.length() - 1).toString() + ")");
            for (int i = usersEmail.length; i > 0; i--) {
                ps.setString(i, usersEmail[i - 1]);
            }
            ResultSet resultSet = ps.executeQuery();
            while(resultSet.next()){
                String login = resultSet.getString(1);
                Long companyId = resultSet.getLong(2);
                if(resultSet.wasNull()){
                    companyId = null;
                }
                result.put(login, companyId);
            }
            con.commit();
        }catch (Exception ex) {
            log.error("Can't check users exists", ex);
            try {
                con.rollback();
            } catch (SQLException sqe) {
                log.error("fail rollback connection", ex);
            }
        } finally {
            try {
                con.close();
            } catch (Exception ex) {
                log.error("fail close connection", ex);
            }
        }
        return result;
    }

    public boolean setCompanyName(UserDTO user, String companyName) {
        boolean res = false;
        Connection c = getConnection();
        try{
            PreparedStatement ps = c.prepareStatement("UPDATE companies SET name = ? WHERE id = ?");
            ps.setString(1, companyName);
            ps.setLong(2, user.getCompanyId());
            int r = ps.executeUpdate();
            res = r>0;
            c.commit();
        } catch (SQLException e) {
            log.error("Can't set company name",e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    /**
     * создание работника со статусом - invited
     * @param manager - инициатор приглашения
     * @param employeeLogin - логин сотрудника
     * @param role - роль на которую приглашают
     * @return список сотрудников компании со статусами и ролями
     */
    public boolean createEmployee(UserDTO manager, String employeeLogin, long role){
        boolean result = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("select * from employees where company_id = ? and login = ? and status = ? ");
            ps.setLong(1,manager.getCompanyId());
            ps.setString(2, employeeLogin);
            ps.setLong(3, EmployeeDTO.STATUS_INVITED);
            ResultSet rs = ps.executeQuery();
            if(!rs.next()){
                ps = con.prepareStatement("insert into employees(company_id, login, role, start_date, status, initiator) values(?,?,?,?,?,?)");
                ps.setLong(1, manager.getCompanyId());
                ps.setString(2,employeeLogin);
                ps.setLong(3,role);
                ps.setLong(4,new Date().getTime());
                ps.setLong(5, EmployeeDTO.STATUS_INVITED);
                ps.setString(6,manager.getLogin());
                int i = ps.executeUpdate();
                result = i>0;
            }
            con.commit();
        } catch (SQLException e) {
            log.error("Can't create employee",e);
            try {
                con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public List<EmployeeDTO> getCompanyEmployees(Long companyId){
        List<EmployeeDTO> employees = getCompanyEmployees(companyId, EmployeeDTO.ROLE_ADMIN);
        employees.addAll(getCompanyEmployees(companyId, EmployeeDTO.ROLE_WORKER));
        return  employees;
    }

    public List<EmployeeDTO> getCompanyEmployees(Long companyId, long role){
        List<EmployeeDTO> employees = new ArrayList<>();
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("select company_id, login, role, start_date, stop_date, status, initiator, remove_initiator from employees where company_id = ? and role = ?");
            ps.setLong(1, companyId);
            ps.setLong(2, role);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                employees.add(new EmployeeDTO(rs));
            }
            con.commit();
        }catch (SQLException e) {
            log.error("Can't get company employees",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return employees;
    }

    public boolean setStatus(String login, Long companyId, long status){
        boolean result = false;
        Connection c = getConnection();
        try{
            log.info("update status:{}:{}:{}",login, companyId, status);
            PreparedStatement ps = c.prepareStatement("update employees set status = ? where company_id = ? and login = ? and status != ?");
            ps.setLong(1,status);
            ps.setLong(2,companyId);
            ps.setString(3,login);
            ps.setLong(4,EmployeeDTO.STATUS_FIRED);
            int i = ps.executeUpdate();
            c.commit();
            result = i>0;
        } catch (SQLException e) {
            log.error("Can't save employee status",e);
            try {c.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        } finally {
            try {c.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return result;
    }

    public long getRole(String login, Long companyId){
        long role = EmployeeDTO.ROLE_UNKNOWN;
        if(companyId == null || StringUtils.isEmpty(login)){
            return role;
        }
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("select role from employees where company_id = ? and login = ? and status = ? ");
            ps.setLong(1,companyId);
            ps.setString(2,login);
            ps.setLong(3,EmployeeDTO.STATUS_ACCEPTED);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                role = rs.getLong(1);
            }
            con.commit();
        } catch (SQLException e) {
            log.error("Can't get employee role",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return role;
    }

    public boolean setRole(String login, Long companyId, long role){
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("update employees set role = ? where company_id = ? and login = ?");
            ps.setLong(1,role);
            ps.setLong(2,companyId);
            ps.setString(3,login);
            int r = ps.executeUpdate();
            res = r>0;
            con.commit();
        } catch (SQLException e) {
            log.error("Can't set employee role",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public boolean removeCompanyEmployee(String manager, Long companyId, String userLogin) {
        boolean res = false;
        if(companyId!=null) {
            Connection con = getConnection();
            try {
                //удаляем если статус что только был приглашен
                PreparedStatement ps = con.prepareStatement("DELETE FROM employees WHERE company_id = ? AND login = ? and status in (?,?) ");
                ps.setLong(1, companyId);
                ps.setString(2, userLogin);
                ps.setLong(3, EmployeeDTO.STATUS_INVITED);
                ps.setLong(4, EmployeeDTO.STATUS_REJECTED);
                res = ps.executeUpdate() > 0;
                //апдейтим если
                ps = con.prepareStatement("UPDATE employees SET stop_date = ?, status = ?, remove_initiator = ? WHERE company_id = ? and login = ? AND status = ? ");
                ps.setLong(1, new Date().getTime());
                ps.setLong(2, EmployeeDTO.STATUS_FIRED);
                ps.setString(3,manager);
                ps.setLong(4,companyId);
                ps.setString(5, userLogin);
                ps.setLong(6, EmployeeDTO.STATUS_ACCEPTED);
                res |= ps.executeUpdate()>0;
                con.commit();
            } catch (SQLException e) {
                log.error("Can't remove employee", e);
                try {
                    con.rollback();
                } catch (SQLException sqe) {
                    sqe.printStackTrace();
                }
            } finally {
                try {
                    con.close();
                } catch (SQLException sqe) {
                    sqe.printStackTrace();
                }
            }
        }
        return res;
    }

    public List<String> getAdmins(Long companyId){
        List<String> adminsList = new ArrayList<>();
        if(companyId == null){
            return adminsList;
        }
        Connection con = getConnection();
        try{
            // SELECT with status = EmployeeDTO.STATUS_ACCEPTED and role = EmployeeDTO.ROLE_ADMIN
            PreparedStatement ps = con.prepareStatement("select login from employees where company_id = ? and status = 1 and role = 0");
            ps.setLong(1,companyId);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                adminsList.add(rs.getString(1));
            }
            con.commit();
        } catch (SQLException e) {
            log.error("Can't get employee role",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return adminsList;
    }


    public boolean isGroupCreator(UserDTO manager){
        boolean res = false;
        if(manager.getCompanyId()==null){
            return false;
        }
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("select initiator from employees where company_id = ? and login = ? and role = ? and status = ? ");
            ps.setLong(1,manager.getCompanyId());
            ps.setString(2,manager.getLogin());
            ps.setLong(3,EmployeeDTO.ROLE_ADMIN);
            ps.setLong(4,EmployeeDTO.STATUS_ACCEPTED);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                String initiator = rs.getString(1);
                if(initiator==null){
                    res = true;
                }
            }
            con.commit();
        } catch (SQLException e) {
            log.error("Can't get employee role",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    /**
     * удаление группы
     * @param companyId идентификатор группы
     * @return массив. описание элементов: 0 - количество удаленных участинков. 1 - количество ресурсов возвращенных уачстникам группы.
     *  2 - количкство шарингов возвращеных участинкам. 3 - количество удаленных из группы пользователей. 4 - удалена ли группа как таковая
     */
    public int[] dropGroup(Long companyId){
        int[] res = new int[5];
        if(companyId==null){
            return new int[0];
        }
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("delete from employees where company_id = ?");
            ps.setLong(1,companyId);
            res[0] = ps.executeUpdate();
            ps.close();

            ps = con.prepareStatement("update resource set company_id = null where company_id = ? ");
            ps.setLong(1, companyId);
            res[1] = ps.executeUpdate();
            ps.close();

            ps = con.prepareStatement("update share set company_id = null where company_id = ? ");
            ps.setLong(1, companyId);
            res[2] = ps.executeUpdate();
            ps.close();

            ps = con.prepareStatement("UPDATE resource_cache SET company_id = null where company_id = ? ");
            ps.setLong(1, companyId);
            ps.executeUpdate();
            ps.close();

            ps = con.prepareStatement("update users set company_id = null where company_id = ? ");
            ps.setLong(1, companyId);
            res[3] = ps.executeUpdate();
            ps.close();

            ps = con.prepareStatement("delete from companies where id = ? ");
            ps.setLong(1, companyId);
            res[4] = ps.executeUpdate();
            ps.close();

            con.commit();
        } catch (SQLException e) {
            log.error("Can't get employee role",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public boolean returnUserDocuments(Long companyId, String login) {
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement("UPDATE resource SET company_id = null WHERE company_id = ? AND author = ? ");
            ps.setLong(1, companyId);
            ps.setString(2, login);
            ps.executeUpdate();

            ps = con.prepareStatement("UPDATE share s SET company_id = null WHERE company_id = ? AND user_login = ?");
            ps.setLong(1, companyId);
            ps.setString(2, login);
            ps.executeUpdate();

            ps = con.prepareStatement("UPDATE resource_cache SET company_id = null WHERE owner = ? and company_id = ? and (abs(hashtext(?)) % 10 = abs(hashtext(owner)) %10)");
            ps.setString(1, login);
            ps.setLong(2, companyId);
            ps.setString(3, login);
            ps.executeUpdate();

            ps = con.prepareStatement("DELETE FROM employees WHERE company_id = ? AND login = ?");
            ps.setLong(1, companyId);
            ps.setString(2, login);
            ps.executeUpdate();

            con.commit();
        } catch (Exception e) {
            log.error("error:",e);
            try {
                con.rollback();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
            return false;
        } finally {
            try {
                con.close();
            } catch (SQLException sqe) {
                sqe.printStackTrace();
            }
        }
        return true;
    }

    public UserDTO leaveCompany(UserDTO user, Long companyId){
        int result;
        Connection con = getConnection();
        assert con != null;
        try{
            PreparedStatement ps = con.prepareStatement("update users set company_id = null where login = ? and company_id = ? ");
            ps.setString(1,user.getLogin());
            ps.setLong(2, companyId);
            result = ps.executeUpdate();
            con.commit();
            if(result>0){
                user.invalidate();
            }
            con.commit();
        } catch (Exception e) {
            log.error("Can't leave user company:{}:{}", user, companyId);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return user;
    }

    /**
     * метод админский. часть функционала drop user
     * @param con
     * @param user
     * @return
     * @throws SQLException
     */
    public int[] deleteUserFromCompany(Connection con, UserDTO user) throws SQLException {
        //если компании в которой пользователь был становится пустая - удалить и ее
        int[] res = new int[2];
        PreparedStatement ps = con.prepareStatement("DELETE FROM employees WHERE login = ? ");
        ps.setString(1, user.getLogin());
        res[0] = ps.executeUpdate();
        if(user.getCompanyId()!=null) {
            ps = con.prepareStatement("SELECT count(*) FROM companies WHERE id = ? ");
            ps.setLong(1, user.getCompanyId());
            ResultSet rs = ps.executeQuery();
            boolean needDeleteCompany = false;
            if(rs.next()){
                long aLong = rs.getLong(1);
                if(!rs.wasNull() && aLong==0){
                    needDeleteCompany = true;
                }
            }
            if(needDeleteCompany){
                ps = con.prepareStatement("DELETE FROM companies WHERE id = ? ");
                ps.setLong(1, user.getCompanyId());
                res[1] = ps.executeUpdate();
            }
        }
        return res;
    }

    public boolean haveEmployee(Long companyId, String id) {
        boolean res = false;
        if(companyId == null){
            return false;
        }
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT 1 " +
                        "FROM employees " +
                        "WHERE company_id = ? AND status = 1 AND login = ? ");
            ps.setLong(1, companyId);
            ps.setString(2, id);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
                res = true;
            }
            con.commit();
        } catch (SQLException e) {
            log.error("Can't check has employee",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public boolean haveBoss(Long companyId, String login) {
        boolean res = false;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT 1 " +
                        "FROM employees " +
                        "WHERE company_id = ? AND status = 1 AND role = 0 AND login = ? ");
            ps.setLong(1, companyId);
            ps.setString(2, login);
            ResultSet rs = ps.executeQuery();
            if(rs.next()){
              res = true;
            }
            con.commit();
        } catch (SQLException e) {
            log.error("Can't check has employee",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }

    public FirstCompanyInvite getFirstCompanyInvite(String login) {
        FirstCompanyInvite res = null;
        Connection con = getConnection();
        try{
            PreparedStatement ps = con.prepareStatement(
                    "SELECT e.start_date, e.initiator " +
                            "FROM employees e " +
                            "WHERE e.login = ? " +
                            "      AND e.start_date = (SELECT min(ee.start_date) FROM employees ee WHERE ee.login = ?)");
            ps.setString(1, login);
            ps.setString(2, login);
            ResultSet rs = ps.executeQuery();
            while(rs.next()){
                res = new FirstCompanyInvite();
                res.startDate = rs.getLong(1);
                res.initiator = rs.getString(2);
            }
            con.commit();
        } catch (SQLException e) {
            log.error("Can't check has employee",e);
            try {con.rollback();} catch (SQLException sqe) {sqe.printStackTrace();}
        }finally {
            try {con.close();}catch (SQLException sqe) {sqe.printStackTrace();}
        }
        return res;
    }
}
