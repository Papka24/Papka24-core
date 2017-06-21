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

import com.google.gson.Gson;
import redis.clients.jedis.Jedis;
import ua.papka24.server.Main;
import ua.papka24.server.api.DTO.EmailDTO;
import ua.papka24.server.db.redis.RedisCluster;

import java.util.*;

public class EmailQueryRedisManager extends RedisCluster {

    private static final String nodeName = Main.property.getProperty("nodeName","unknown");
    private static final String JSON = "json";
    private static final String NODA = "noda";
    private static final String TIME = "time";
    private static final Gson gson = new Gson();
    private static final int POP_SIZE = 10;
//    private static final long TIME_DIFF = 10L * 60 * 1000;
    private static final long TIME_DIFF = 4L * 60 * 1000;
    private static boolean ENABLE;

    public static final String LIST = "emails";

    private EmailQueryRedisManager(){
        ENABLE = true;
    }
    private static class Singleton {
        private static final EmailQueryRedisManager HOLDER_INSTANCE = new EmailQueryRedisManager();

    }

    public static EmailQueryRedisManager getInstance() {
        return EmailQueryRedisManager.Singleton.HOLDER_INSTANCE;
    }

    /**
     * добавление письма в очередь на сервере.
     * @param emailDTO
     */
    public boolean add(EmailDTO emailDTO){
        PreparedEmail preparedEmail = new PreparedEmail(emailDTO);
        return add(preparedEmail);
    }

    public boolean add(PreparedEmail preparedEmail){
        boolean res = false;
        try(Jedis con = getConnection()){
            con.hset(preparedEmail.hashCode,JSON,  preparedEmail.emailJson);
            con.hset(preparedEmail.hashCode,NODA,  nodeName);
            con.hset(preparedEmail.hashCode,TIME,  preparedEmail.time);
            con.sadd(LIST,preparedEmail.hashCode);
            res = true;
        }catch (Exception ex){
            log.error("error while add email to server query",ex);
        }
        return res;
    }

    /**
     * удаление письма из очереди сервера
     * @param emailDTO
     */
    public boolean remove(EmailDTO emailDTO){
        PreparedEmail preparedEmail = new PreparedEmail(emailDTO);
        return remove(preparedEmail);
    }

    private boolean remove(PreparedEmail preparedEmail){
        boolean res = false;
        try(Jedis con = getConnection()){
            con.srem(LIST,preparedEmail.hashCode);
            con.hdel(preparedEmail.hashCode,JSON, NODA, TIME);
            res = true;
        }catch (Exception ex){
            log.error("error remove email from server query",ex);
        }
        return res;
    }

    public List<EmailDTO> get(){
        return get(POP_SIZE);
    }

    private Set<String> getElements(Jedis con, int count){
        Set<String> emailsHash = new HashSet<>();
        for(int i=0;i<count;i++){
            String hash = con.spop(LIST);
            if(hash!=null) {
                emailsHash.add(hash);
            }else{
                break;
            }
        }
        return emailsHash;
    }

    private long getEmailQuerySize(){
        long size = -1;
        try(Jedis con = getConnection()){
            size = con.scard(LIST);
        }catch (Exception ex){
            log.error("error remove email from server query",ex);
        }
        return size;
    }

    public static long getRedisEmailQuerySize(){
        if(ENABLE){
            return EmailQueryRedisManager.getInstance().getEmailQuerySize();
        }else{
            return -1;
        }
    }

    public List<EmailDTO> get(final int count){
        List<EmailDTO> emailDTOs = new ArrayList<>();
        try(Jedis con = getConnection()){
            Set<String> emailsHash = getElements(con,count);
            Set<String> returnedEmails = new HashSet<>();
            for(String emailHash : emailsHash){
                Map<String, String> emailFields = con.hgetAll(emailHash);
                PreparedEmail preparedEmail = new PreparedEmail(emailHash,emailFields);
                long createTime = Long.valueOf(emailFields.get(TIME));
                long currentTime = System.currentTimeMillis();
                if(currentTime-createTime>TIME_DIFF){
                    //забираем в свою обработку
                    remove(preparedEmail);
                    EmailDTO emailDTO = gson.fromJson(preparedEmail.emailJson, EmailDTO.class);
                    add(emailDTO);
                    emailDTOs.add(emailDTO);
                }else{
                    //иначе помещаем в подготавлиавемый список для возврата
                    returnedEmails.add(emailHash);
                }
            }
            if(returnedEmails.size()>0){
                String[] array = returnedEmails.toArray(new String[returnedEmails.size()]);
                con.sadd(LIST, array);
            }
        }catch(Exception ex){
            log.error("error get email",ex);
        }
        return emailDTOs;
    }

    private static class PreparedEmail{
        String emailJson;
        String time;
        String hashCode;

        PreparedEmail(EmailDTO emailDTO) {
            this.emailJson = gson.toJson(emailDTO);
            this.time = String.valueOf(new Date().getTime());
            this.hashCode = String.valueOf(emailDTO.hashCode());
        }

        PreparedEmail(String hashCode, Map<String,String> fields){
            this.emailJson = fields.get(JSON);
            this.time = fields.get(TIME);
            this.hashCode = hashCode;
        }

        @Override
        public String toString() {
            return "PreparedEmail{" + "emailJson='" + emailJson + '\'' +
                    ", time='" + time + '\'' +
                    ", hashCode='" + hashCode + '\'' +
                    '}';
        }
    }
}
