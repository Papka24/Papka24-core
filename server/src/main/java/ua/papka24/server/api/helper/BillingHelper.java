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
import ua.papka24.server.db.dao.BillingDAO;
import ua.papka24.server.db.dto.ResourceDTO;
import ua.papka24.server.db.dto.ResourceMiniDTO;
import ua.papka24.server.db.dto.billing.BillingResponseDTO;
import ua.papka24.server.db.dto.billing.UploadTrend;
import ua.papka24.server.service.billing.BillingQueryManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class BillingHelper {

    private static final Logger log = LoggerFactory.getLogger(BillingHelper.class);
    private BillingHelper(){}

    private static class Singleton {
        private static final BillingHelper HOLDER_INSTANCE = new BillingHelper();
    }

    public static BillingHelper getInstance() {
        return BillingHelper.Singleton.HOLDER_INSTANCE;
    }

    public void create(ResourceDTO resource){
        BillingDAO.getInstance().create(resource);
        BillingQueryManager.addToCheckQuery(resource.getId(), resource.getAuthor());
    }

    public void setPreferEgrpou(String login, Long egrpou) {
        BillingDAO.getInstance().setPreferEgrpou(login, egrpou);
    }

    public Map<String, Long> getPreferEgrpou(){
        return BillingDAO.getInstance().getPreferEgropou();
    }

    public List<ResourceMiniDTO> getDetailsByLogin(String login, long from, long to, String type) {
        switch(type){
            default:
            case "c":{
                return BillingDAO.getInstance().getResources(Collections.singletonList(login), from, to);
            }
            case "s":{
                return BillingDAO.getInstance().getShareResources(Collections.singletonList(login), from, to);
            }
            case "i":{
                return BillingDAO.getInstance().getSignResources(Collections.singletonList(login), from, to);
            }
        }
    }

    public static void truncate() {
        BillingDAO.getInstance().truncate();
    }

    public static void init(){
        truncate();
        BillingDAO.getInstance().copyResources();
        BillingDAO.getInstance().updateResourcesInfo();

        ZonedDateTime zdt = ZonedDateTime.now();
        zdt = zdt.withDayOfMonth(1);
        LocalDateTime ldt = zdt.toLocalDate().atStartOfDay();
        long currentMonthStart = ldt.toInstant(ZoneOffset.from(zdt)).toEpochMilli();
        log.info("currentMonthStart : {}", currentMonthStart);

        zdt = ZonedDateTime.of(2016, 8, 1, 0, 0, 0, 0, ZoneId.systemDefault());
        ldt = zdt.toLocalDate().atStartOfDay();
        long monthStart = ldt.toInstant(ZoneOffset.from(zdt)).toEpochMilli();

        long monthStop = zdt.plusMonths(1).toLocalDate().atStartOfDay().toInstant(ZoneOffset.from(zdt)).toEpochMilli();
        log.info("monthStop : {}", monthStop);
        while(monthStart<currentMonthStart){
            log.info("start month : {}", monthStart);
            log.info("stop month : {}", monthStop);
            BillingDAO.getInstance().calcEgrpouByPeriod(monthStart, monthStop);
            monthStart = monthStop;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(monthStop);
            cal.add(Calendar.MONTH, 1);
            monthStop = cal.getTimeInMillis();
            log.info("nextMonth :{} ", monthStop);
        }
    }

    public List<BillingResponseDTO> getByEgrpou(long from, long to) throws InterruptedException {
        final Map<Long, BillingResponseDTO>[] resultJur = new Map[]{new HashMap<>()};
        final Map<Long, BillingResponseDTO>[] resultFiz = new Map[]{new HashMap<>()};
        final List<BillingResponseDTO>[] resultUnk = new List[]{new ArrayList<>()};
        final Map<Long, BillingResponseDTO>[] resultHz = new Map[]{new HashMap<>()};
        CountDownLatch latch = new CountDownLatch(4);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        pool.execute(()->{
            resultJur[0] = BillingDAO.getInstance().getEgrpouListJur(from, to);
            latch.countDown();
        });
        pool.execute(()->{
            resultFiz[0] = BillingDAO.getInstance().getEgrpouListFiz(from, to);
            latch.countDown();
        });
        pool.execute(()->{
            resultUnk[0] = BillingDAO.getInstance().getEgrpouListUnk(from, to);
            latch.countDown();
        });
        pool.execute(()->{
            resultHz[0] = BillingDAO.getInstance().getEgrpouListHz(from, to);
            latch.countDown();
        });
        latch.await();
        int initialCapacity = (resultFiz[0].size() + resultJur[0].size() + resultHz[0].size()) + 1;
        Map<Long, BillingResponseDTO> result = new HashMap<>(initialCapacity, 1);
        result.putAll(resultJur[0]);
        result.putAll(resultFiz[0]);
        result.putAll(resultHz[0]);
        result = BillingDAO.getInstance().checkEgrpouBlock(result);
        List<BillingResponseDTO> collect = new ArrayList<>(result.values());
        collect.addAll(resultUnk[0]);
        return collect;
    }

    public List<BillingResponseDTO> getByLogin(long from, long to) {
        return BillingDAO.getInstance().getLoginList(from, to);
    }

    public List<ResourceMiniDTO> getResourceDetailsByEGRPOU(Long egrpou, long from, long to) {
        return BillingDAO.getInstance().getResourceDetailsByEGRPOU(egrpou, from, to);
    }

    public List<ResourceMiniDTO> getResourceDetailsByInn(Long inn, long from, long to) {
        return BillingDAO.getInstance().getResourceDetailsByInn(inn, from, to);
    }

    public List<ResourceMiniDTO> getResourceDetailsByLogin(String login, long from, long to, String type) {
        return BillingDAO.getInstance().getResourceDetailsByLogin(login, from, to);
    }

    public List<ResourceMiniDTO> getNullDetailsByEGRPOU(long from, long to) {
        return BillingDAO.getInstance().getNullDetailsByEGRPOU(from, to);
    }

    public List<UploadTrend> getUploadLoginsTrends(long from, long to){
        return BillingDAO.getInstance().getUploadLoginTrends(from, to);
    }

    public void saveLoginToCatalogPb(String login, String initiator) {
        BillingDAO.getInstance().saveLoginToCatalogPb(login, initiator);
    }

    public List<BillingResponseDTO> getTopNCompanies(long number, long from, long to) {
        return BillingDAO.getInstance().getTopNCompanies(number, from, to);
    }

    public long getUserBilling(String login, Long from, Long to) {
        return BillingDAO.getInstance().getUserBilling(login, from, to);
    }

    public Map<Long, Long> getUserCompanyBilling(String login, long from, long to){
        return BillingDAO.getInstance().getUserCompanyBilling(login, from, to);
    }
}