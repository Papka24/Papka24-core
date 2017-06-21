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

package ua.papka24.server.db.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;
import ua.papka24.server.db.dao.DAO;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;


public class UpgradeUtil extends DAO {

    private static final Logger log = LoggerFactory.getLogger(UpgradeUtil.class);

    public UpgradeUtil(){
    }

    public void upgrade() {
        File f;
        JarFile filList;
        try {
            f = new File(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            filList = new JarFile(f);
        } catch (URISyntaxException | IOException e) {
            log.warn("DB autoupdates was ignored");
            return;
        }
        long currentVersion = getCurrentDatabaseVersion();
        try (Connection con = getConnection()) {
            if (currentVersion == DAO.NULL_ID) {
                String dbVer = Main.property.getProperty("jdbc.dbver", "5");
                if (dbVer.equals("-1")) {
                    return;
                }
                Statement st = con.createStatement();
                int dbVerInt = Integer.valueOf(dbVer);
                st.execute("COMMENT ON table users is '" + dbVer + "'");
                st.close();
                currentVersion = dbVerInt;
            }

            Map<Integer, String> patchFiles = new TreeMap<>();
            Enumeration<JarEntry> entries = filList.entries();
            String patchPath = "sql/patch/";
            while (entries.hasMoreElements()) {
                final String name = entries.nextElement().getName();
                if (name.startsWith(patchPath)) {
                    if(name.length()> patchPath.length()) {
                        int sep = name.indexOf('.');
                        int patchNumber = Integer.valueOf(name.substring(10, sep));
                        if(patchNumber>currentVersion) {
                            log.info("found sql patch:{}",name);
                            patchFiles.put(patchNumber, name);
                        }
                    }
                }
            }

            Integer maxVer = 0;
            for(Integer pos : patchFiles.keySet()) {
               String patchFileName = patchFiles.get(pos);
                InputStream resourceAsStream = this.getClass().getResourceAsStream("/" + patchFileName);
                Scanner sr = new Scanner(new InputStreamReader(resourceAsStream));
                sr.useDelimiter("\\s*;;\\s*(?=([^']*'[^']*')*[^']*$)");
                Statement statement = con.createStatement();
                String line;
                while (sr.hasNext()) {
                    line = sr.next().trim();
                    if (!line.isEmpty()) {
                        log.info("execute query from patch:{}\t:{}",patchFileName,line);
                        statement.execute(line);
                        maxVer = (pos > maxVer)?pos:maxVer;
                    }
                }
                sr.close();
                resourceAsStream.close();
            }


            if(maxVer>0) {
                Statement s = con.createStatement();
                s.execute("COMMENT ON table users is '" + String.valueOf(maxVer) + "'");
                con.commit();
            }
        } catch (Exception e) {
            log.error("error upgrade database:", e);
        }
    }

    private long getCurrentDatabaseVersion(){
        long currentVersion = DAO.NULL_ID;
        try(Connection con = getConnection()){

            PreparedStatement ps = con.prepareStatement(
                    "SELECT obj_description(oid) " +
                            "FROM pg_class " +
                            "WHERE relkind = 'r' AND relname = ? ");
            ps.setString(1, "users");
            ResultSet resultSet = ps.executeQuery();
            if (resultSet.next()) {
                String version = resultSet.getString("obj_description");
                if (version != null && version.trim().length() > 0) {
                    currentVersion = Long.valueOf(version);
                }
            }
            ps.close();
        }catch (Exception ex){
            log.warn("error determine current database version:{}", ex);
        }
        return currentVersion;
    }
}