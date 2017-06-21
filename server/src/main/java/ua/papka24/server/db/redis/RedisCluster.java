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

package ua.papka24.server.db.redis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import ua.papka24.server.Main;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public abstract class RedisCluster {

    protected static final Logger log = LoggerFactory.getLogger("redis");
    private static final Lock lock = new ReentrantLock();
    private static JedisPool pool = init();

    protected RedisCluster(){
    }

    private static JedisPool init(){
        lock.lock();
        try {
            if(pool==null || pool.isClosed()){
                String redisPath = Main.property.getProperty("redis.path");
                String redisPort = Main.property.getProperty("redis.port");
                if (redisPort == null) {
                    return new JedisPool(new JedisPoolConfig(), redisPath);
                } else {
                    return new JedisPool(new JedisPoolConfig(), redisPath, Integer.valueOf(redisPort));
            }
        }
        }finally {
            lock.unlock();
        }
        return null;
    }

     public Jedis getConnection() {
        try {
            if(pool==null || pool.isClosed()){
                if(pool!=null){
                    pool.destroy();
                }
                pool = init();
            }
            return pool.getResource();
        } catch (Exception ex) {
            log.error("getRedisConnectionError:",ex);
            throw ex;
        }
    }

    public static boolean isActive() {
        return pool != null && !pool.isClosed();
    }
}