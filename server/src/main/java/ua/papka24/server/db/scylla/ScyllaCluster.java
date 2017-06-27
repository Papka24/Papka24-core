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

package ua.papka24.server.db.scylla;

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.policies.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.Main;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;



public abstract class ScyllaCluster {
    protected static final Logger log = LoggerFactory.getLogger("ScyllaDB");
    private static volatile Cluster cluster;
    private static Session session;
    private final String scyllaPath;
    private final String scyllaPort;
    private final String scyllaKeyspace;
    protected static Map<String,PreparedStatement> preparedStatementMap;
    private static final Lock lock = new ReentrantLock();

    protected ScyllaCluster(){
        System.out.println("cluster begin");
        scyllaPath = Main.property.getProperty("scylla.path");
        scyllaPort = Main.property.getProperty("scylla.port");
        scyllaKeyspace = Main.property.getProperty("scylla.keyspace");
        lock.lock();
        try {
            if (cluster == null) {
                System.out.println("cluster create");
                cluster = init();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            lock.unlock();
        }
        System.out.println("cluster end");
    }

    public ScyllaCluster(String scyllaPath, String scyllaPort, String scyllaKeyspace){
        System.out.println("cluster begin");
        this.scyllaPath = scyllaPath;
        this.scyllaPort = scyllaPort;
        this.scyllaKeyspace = scyllaKeyspace;
        System.out.printf("cluster parameters: %s, %s\n", scyllaPath, scyllaPort);
        lock.lock();
        try {
            if (cluster == null) {
                System.out.println("cluster create");
                cluster = init();
            }
        }catch (Exception ex){
            ex.printStackTrace();
        }finally {
            lock.unlock();
        }
        System.out.println("cluster end");
    }

    private Cluster init(){
        Cluster cluster;
        if (scyllaPort != null) {
            cluster = Cluster.builder()
                    .addContactPointsWithPorts(new InetSocketAddress(scyllaPath, Integer.valueOf(scyllaPort)))
                    .withRetryPolicy(new OwnRetryPolicy())
                    .build();
        } else {
            cluster = Cluster.builder()
                    .addContactPoint(scyllaPath)
                    .withRetryPolicy(new OwnRetryPolicy())
                    .build();
        }
        session = KeySpaceUtil.getKeySpaceSession(scyllaKeyspace,cluster);
        createTables(session);
        if(preparedStatementMap==null){
            preparedStatementMap = preparedStatement(session);
        }
        return cluster;
    }

    protected Session getSession() {
        if (session == null || session.isClosed() || cluster == null || cluster.isClosed()) {
            if (cluster == null || cluster.isClosed()) {
                cluster = init();
                return session;
            }
            if(session==null || session.isClosed()){
                session = KeySpaceUtil.getKeySpaceSession(scyllaKeyspace,cluster);
            }
        }
        return session;
    }


    private void createTables(Session session){
        try {
            Properties tablesInfo = SchemaDBUtil.createTablesInfo();
            tablesInfo.values().forEach(e-> session.execute((String)e));
        }catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public static boolean isActive(){
        return cluster!=null && !cluster.isClosed();
    }

    private Map<String,PreparedStatement> preparedStatement(Session session){
        Map<String,PreparedStatement> map = new HashMap<>();
        try{
            if(session!=null){
                Properties statementInfo = SchemaDBUtil.getStatementInfo();
                for (Map.Entry<Object, Object> stat : statementInfo.entrySet()) {
                    String keyName = (String) stat.getKey();
                    String statement = (String) stat.getValue();
                    map.put(keyName, session.prepare(statement));
                }
            }
        }catch (Exception ex){
            ex.printStackTrace();
        }
        return map;
    }

    private static class OwnRetryPolicy implements RetryPolicy{

        @Override
        public RetryDecision onReadTimeout(Statement statement, ConsistencyLevel cl, int requiredResponses, int receivedResponses, boolean dataRetrieved, int nbRetry) {
            return RetryDecision.ignore();
        }

        @Override
        public RetryDecision onWriteTimeout(Statement statement, ConsistencyLevel cl, WriteType writeType, int requiredAcks, int receivedAcks, int nbRetry) {
            return RetryDecision.ignore();
        }

        @Override
        public RetryDecision onUnavailable(Statement statement, ConsistencyLevel cl, int requiredReplica, int aliveReplica, int nbRetry) {
            return RetryDecision.ignore();
        }

        @Override
        public RetryDecision onRequestError(Statement statement, ConsistencyLevel cl, DriverException e, int nbRetry) {
            return RetryDecision.ignore();
        }

        @Override
        public void init(Cluster cluster) {
            System.out.println("RetryPolicy.init");
        }

        @Override
        public void close() {
        }
    }
}