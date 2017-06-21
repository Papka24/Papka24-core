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

package ua.papka24.server.service.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.papka24.server.db.dao.BillingDAO;
import ua.papka24.server.service.billing.dto.BillingEgrpouDTO;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


public class BillingQueryManager implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BillingQueryManager.class);
    private static final Queue<BillingEgrpouDTO> queue = new LinkedBlockingQueue<>();
    private static final Timer timer = new Timer();

    //todo завязать через редис для избежания возможности потерь записей
    public BillingQueryManager(){

    }

    public static void startTimer(){
        LocalDateTime firstDayOfMonth = LocalDateTime.now().withDayOfMonth(1);
        LocalDateTime nextMonthFirstDay = firstDayOfMonth.plusMonths(1);
        LocalDateTime startTime = nextMonthFirstDay.withHour(0).withMinute(5);
        Date startDate = Date.from(startTime.atZone(ZoneId.systemDefault()).toInstant());
        long oneMonthPeriod = ChronoUnit.MILLIS.between(firstDayOfMonth, firstDayOfMonth.plusMonths(1));
        timer.schedule(new BillingMonthProcessor(), startDate, oneMonthPeriod);
    }

    public static void addToQueueEgrpou(BillingEgrpouDTO element){
        queue.add(element);
    }

    public static void addToQueueEgrpou(Long resourceId, Long egrpou, String companyName){
        log.info("add EGRPOU to billing queue :{}:{}", resourceId, egrpou);
        BillingEgrpouDTO element = new BillingEgrpouDTO();
        element.egrpou = egrpou;
        element.resourceId = resourceId;
        element.companyName = companyName;
        queue.add(element);
    }

    public static void addToCheckQuery(Long resorceId, String author){
        BillingEgrpouDTO element = new BillingEgrpouDTO();
        element.resourceId = resorceId;
        element.author = author;
        element.needCheck = true;
    }

    public static void addToQueueInn(Long resourceId, Long inn, String companyName) {
        log.info("add INN to billing queue :{}:{}", resourceId, inn);
        BillingEgrpouDTO element = new BillingEgrpouDTO();
        element.inn = inn;
        element.resourceId = resourceId;
        element.companyName = companyName;
        queue.add(element);
    }

    @Override
    public void run() {
        BillingDAO billingDAO = BillingDAO.getInstance();
        while(true) {
            try {
                BillingEgrpouDTO element;
                while( (element = queue.poll())!=null){
                    log.info("element -> {}", element);
                    if(billingDAO == null){
                        billingDAO = BillingDAO.getInstance();
                    }
                    if(element.needCheck){
                        billingDAO.checkAndSetEgrpouPrefer(element.resourceId, element.author);
                    }else {
                        if (element.egrpou != null) {
                            billingDAO.setEgrpou(element.resourceId, element.egrpou, element.companyName, BillingDAO.Schema.SIGN);
                        } else if (element.inn != null) {
                            billingDAO.setInn(element.resourceId, element.inn, element.companyName, BillingDAO.Schema.SIGN);
                        }
                    }
                }
                TimeUnit.SECONDS.sleep(1);
            } catch (Exception ex) {
                log.error("error", ex);
            }
        }
    }

    private static class BillingMonthProcessor extends TimerTask {

        @Override
        public void run() {
            log.info("start timer BillingMonthProcessor task");
            try{
                ZonedDateTime zdt = ZonedDateTime.now();
                long to = zdt.toLocalDate().atStartOfDay().toInstant(ZoneOffset.from(zdt)).toEpochMilli();
                LocalDateTime ldt = zdt.minusMonths(1).withDayOfMonth(1).toLocalDate().atStartOfDay();
                long from = ldt.toInstant(ZoneOffset.from(zdt)).toEpochMilli();
                BillingDAO.getInstance().calcEgrpouByPeriod(from, to);
            }catch (Exception ex){
                log.error("error", ex);
            }
        }
    }
}
