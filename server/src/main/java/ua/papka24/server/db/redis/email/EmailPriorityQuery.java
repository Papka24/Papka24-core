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

package ua.papka24.server.db.redis.email;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.api.DTO.EmailDTO;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;


class EmailPriorityQuery implements CustomPriorityQuery{

    private static final Logger log = LoggerFactory.getLogger(EmailPriorityQuery.class);
    private final BlockingQueue<EmailDTO> successMarked = new LinkedBlockingQueue<>();
    private static ExpiringMap<String, PriorityBlockingQueue<EmailDTO>> expiredMap = ExpiringMap.builder()
            .expirationPolicy(ExpirationPolicy.CREATED).expiration(3, TimeUnit.MINUTES).build();
    private static final String QUERY_KEY = "query";

    EmailPriorityQuery(){
        Thread helper = new Thread(new Helper());
        helper.setDaemon(true);
        helper.start();
    }

    private PriorityBlockingQueue<EmailDTO> getQuery(){
        if(expiredMap==null){
            expiredMap = ExpiringMap.builder()
                    .expirationPolicy(ExpirationPolicy.CREATED).expiration(4, TimeUnit.MINUTES).build();
        }
        return expiredMap.computeIfAbsent(QUERY_KEY, k -> new PriorityBlockingQueue<>());
    }

    @Override
    public boolean add(EmailDTO emailDTO) {
        EmailQueryRedisManager.getInstance().add(emailDTO);
        return getQuery().add(emailDTO);
    }

    @Override
    public boolean process(EmailDTO emailDTO){
        boolean delresult =  EmailQueryRedisManager.getInstance().remove(emailDTO);
        if(!delresult){
            successMarked.add(emailDTO);
        }
        return delresult;
    }

    @Override
    public boolean isEmpty() {
        return getQuery().isEmpty();
    }

    @Override
    public int size() {
        if(expiredMap!=null){
            if(expiredMap.get(QUERY_KEY)!=null){
                return expiredMap.get(QUERY_KEY).size();
            }
        }
        return -1;
    }

    @Override
    public EmailDTO poll() {
        EmailDTO emailDTO = getQuery().poll();
        if(emailDTO ==null){
            getQuery().addAll(EmailQueryRedisManager.getInstance().get());
            return getQuery().poll();
        }else{
            return emailDTO;
        }
    }

    private class Helper implements Runnable{

        @Override
        public void run() {
            while(true) {
                try {
                    EmailDTO emailDTO;
                    while( (emailDTO = successMarked.poll()) !=null){
                        boolean delresult =  EmailQueryRedisManager.getInstance().remove(emailDTO);
                        if(!delresult){
                            successMarked.add(emailDTO);
                            break;
                        }
                    }
                    getQuery().addAll(EmailQueryRedisManager.getInstance().get());
                } catch (Exception ex) {
                    log.error("error:", ex);
                }finally {
                    try{TimeUnit.MINUTES.sleep(4);}catch(Exception ex){/*doh*/}
                }
            }
        }
    }
}
