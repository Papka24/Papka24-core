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

package ua.papka24.server.db.scylla.profile;

import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Session;
import ua.papka24.server.db.scylla.ScyllaCluster;


public class ProfilerDAO extends ScyllaCluster {
    private ProfilerDAO() {
    }

    private static class Singleton {
        private static final ProfilerDAO HOLDER_INSTANCE = new ProfilerDAO();
    }

    public static ProfilerDAO getInstance() {
        return ProfilerDAO.Singleton.HOLDER_INSTANCE;
    }

    public boolean saveProfilerInfo(long time, String method, String type, String args, long execTime) {
        boolean res = false;
        try {
            Session session = getSession();
            BoundStatement bound = preparedStatementMap.get("profiler.save").bind()
                    .setLong(0, time)
                    .setString(1, method)
                    .setString(2, type)
                    .setString(3, args)
                    .setLong(4, execTime);
            session.executeAsync(bound);
            res = true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return res;
    }

    public boolean saveRequestLog(long time, String login, String request, String url) {
        boolean res = false;
        try {
            Session session = getSession();
            BoundStatement bound = preparedStatementMap.get("request_log.insert").bind()
                    .setLong(0, time)
                    .setString(1, login)
                    .setString(2, request)
                    .setString(3, url);
            session.executeAsync(bound);
            res = true;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return res;
    }
}
