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

package ua.papka24.server.security;

import org.bouncycastle.asn1.ocsp.OCSPResponseStatus;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import ua.papka24.server.Main;
import ua.papka24.server.db.dto.OCSPStatusInfoDTO;
import ua.privatbank.cryptonite.CryptoniteX;

import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.util.*;

public class OCSPService {
    private static Map<String, String> ocspCertMap;

    static {
        ocspCertMap = new HashMap<>();
        try {
            ocspCertMap.put("3004751DEF2C78AE010000000100000049000000", "http://acsk.privatbank.ua/services/ocsp/");   // - АЦСК ПАТ КБ «ПРИВАТБАНК»
            ocspCertMap.put("3004751DEF2C78AE010000000100000061000000", "http://ca.informjust.ua/services/ocsp/");     // - АЦСК органів юстиції України
            ocspCertMap.put("3004751DEF2C78AE010000000100000040000000", "http://ca.ksystems.com.ua/services/ocsp/");   // - АЦСК ТОВ "КС"
            ocspCertMap.put("3004751DEF2C78AE010000000100000055000000", "http://ca.informjust.ua/services/ocsp/");     // - АЦСК Держінформ'юсту
            ocspCertMap.put("3004751DEF2C78AE010000000100000046000000", "http://csk.uss.gov.ua/services/ocsp/");       // - АЦСК ДП "УСС"
            ocspCertMap.put("3004751DEF2C78AE010000000100000053000000", "http://ca.mil.gov.ua/services/ocsp/");        // - Акредитований центр сертифікації ключів Збройних Сил
            ocspCertMap.put("3004751DEF2C78AE01000000010000004D000000", "http://acskidd.gov.ua/services/ocsp/");       // - Акредитований центр сертифікації ключів ІДД ДФС
            ocspCertMap.put("3004751DEF2C78AE010000000100000001000000", "http://czo.gov.ua/services/ocsp/");           // - Центральний засвідчувальний орган
            ocspCertMap.put("3004751DEF2C78AE010000000100000015000000", "http://csk.uz.gov.ua/services/ocsp/");        // - ЦСК Укрзалізниці
            ocspCertMap.put("3004751DEF2C78AE01000000010000005F000000", "http://masterkey.ua/services/ocsp/");         // - АЦСК "MASTERKEY" ТОВ "АРТ-МАСТЕР"
            ocspCertMap.put("3004751DEF2C78AE01000000010000000F000000", "http://masterkey.ua/services/ocsp/");         // - ЦСК "MASTERKEY" ТОВ "АРТ-МАСТЕР"
            ocspCertMap.put("0188B6", "http://masterkey.ua/services/ocsp/");                                           // - ЦСК "MASTERKEY" ТОВ "АРТ-МАСТЕР"
            ocspCertMap.put("01887A", "http://masterkey.ua/services/ocsp/");                                           // - ЦСК "MASTERKEY" ТОВ "АРТ-МАСТЕР"
            ocspCertMap.put("3004751DEF2C78AE010000000100000030000000", "http://csk.ukrsibbank.com/services/ocsp/"); // - АЦСК Публічного акціонерного товариства "УкрСиббанк"
            ocspCertMap.put("3004751DEF2C78AE010000000100000008000000", "http://uakey.com.ua:2560/services/ocsp/");    // - ТОВ "Центр сертифікації ключів "Україна"
            ocspCertMap.put("3004751DEF2C78AE010000000100000043000000", "http://uakey.com.ua:2560/services/ocsp/");    // - АЦСК ТОВ "Центр сертифікації ключів "Україна"

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, String> getOcspCertMap() {
        return ocspCertMap;
    }

    public static OCSPStatusInfoDTO getStatus(Certificate userCert) {
        try {
            CertificateCache certCache = CertificateCache.getInstance();

            /* Получаем сертификат рута из кеша по его SubjKeyId. */
            byte[] rootSubjKeyId = CertificateUtils.getAuthKeyId(userCert);
            Certificate rootCert = certCache.getCertBySubjKeyId(rootSubjKeyId);
            if (rootCert == null) {
                Main.log.warn("root cert not found. rootSubjKeyId: {}", DatatypeConverter.printHexBinary(rootSubjKeyId));
                throw new Exception("root cert not found");
            }

            String url = ocspCertMap.get(DatatypeConverter.printHexBinary(rootCert.getSerialNumber().getValue().toByteArray()));
            if (url == null) {
                url = CertificateUtils.getOcspUrl(userCert);
                if (url == null) {
                    throw new Exception("Url for corresponding OCSP service not found! Root SN: "
                            + DatatypeConverter.printHexBinary(rootCert.getSerialNumber().getValue().toByteArray())
                            + ", subject: " + rootCert.getSubject().toString());
                }
            }

            byte[] userCertSN = userCert.getSerialNumber().getValue().toByteArray();
            List<byte []> serialNumbers = new ArrayList<byte[]>();
            serialNumbers.add(userCertSN);

            /* Генерируется OCSP запрос. */
            byte[] ocspRequest = CryptoniteX.generateOCSPRequest(null, rootCert.getEncoded(),
                    null, true, serialNumbers);

            /* Отправка OCSP запроса. */
            byte[] ocspResponseBytes = ServerUtils.postRequest(url, ocspRequest);

            OCSPResp ocspResponse = new OCSPResp(ocspResponseBytes);

            if (ocspResponse.getStatus() == OCSPResponseStatus.SUCCESSFUL) {
                BasicOCSPResp basicOCSPResponse = (BasicOCSPResp) ocspResponse.getResponseObject();
                List<Certificate> certsByRespID = null;
                RespID respID = null;
                String ocspRespSign = OCSPStatusInfoDTO.RespSign.NO_CERT_FOR_VERIFY;
                if (basicOCSPResponse != null) {
                    respID = basicOCSPResponse.getResponderId();
                    certsByRespID = certCache.getCertByResponderID(respID);
                    if (certsByRespID != null) {
                        ocspRespSign = OCSPStatusInfoDTO.RespSign.INVALID;
                        for (Certificate ocspCert : certsByRespID) {
                            if (CryptoniteX.ocspResponseVerify(ocspResponseBytes, ocspCert.getEncoded())) {
                                ocspRespSign = OCSPStatusInfoDTO.RespSign.VALID;
                                break;
                            }
                        }
                    }
                }

                if (!ocspRespSign.equals(OCSPStatusInfoDTO.RespSign.VALID)) {
                    /* Если сертификата OCSP сервера не нашлось в кеше, то ищем его в OCSP ответе. */
                    X509CertificateHolder[] ocspResponseCerts = basicOCSPResponse.getCerts();
                    if (ocspResponseCerts != null) {
                        certsByRespID = CertificateUtils.getCertByResponderID(respID, ocspResponseCerts);
                        for (Certificate ocspCert : certsByRespID) {
                            byte [] ocspCertEncoded = ocspCert.getEncoded();
                            if (CryptoniteX.ocspResponseVerify(ocspResponseBytes, ocspCertEncoded)) {
                                if (CryptoniteX.certificateIsOcspExtKeyUsage(ocspCertEncoded)) {
                                    if (CryptoniteX.certVerify(ocspCertEncoded, rootCert.getEncoded())) {
                                        CertificateCache.saveCert(ocspCert);
                                        ocspRespSign = OCSPStatusInfoDTO.RespSign.VALID;
                                        Main.log.warn("OCSP certificate not found in cache, but found in answer. Certificate saved in cache. ocspCert bytes: {}.", Base64.getEncoder().encodeToString(ocspCert.getEncoded()));
                                        break;
                                    } else {
                                        byte[] ocspRootSubjKeyId = CertificateUtils.getAuthKeyId(ocspCert);
                                        Certificate ocspRootCert = certCache.getCertBySubjKeyId(ocspRootSubjKeyId);

                                        if (CryptoniteX.certVerify(ocspCertEncoded, ocspRootCert.getEncoded())) {
                                            CertificateCache.saveCert(ocspCert);
                                            ocspRespSign = OCSPStatusInfoDTO.RespSign.VALID;
                                            Main.log.warn("OCSP certificate not found in cache, but found in answer. Attention!!! Root of the OCSP cert not same as root of the user cert. Certificate saved in cache. ocspCert bytes: {}, userCert bytes: {}.", Base64.getEncoder().encodeToString(ocspCert.getEncoded()), Base64.getEncoder().encodeToString(userCert.getEncoded()));
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!ocspRespSign.equals(OCSPStatusInfoDTO.RespSign.VALID)) {
                    Main.log.warn("OCSP certificate not found. Root cert: {} OCSP cert respID: {}",
                            Base64.getEncoder().encodeToString(rootCert.getEncoded()),
                            Base64.getEncoder().encodeToString(respID.toASN1Primitive().getEncoded()));
                }

                SingleResp[] singleResponses = basicOCSPResponse.getResponses();
                if (singleResponses != null && singleResponses.length > 0) {
                    SingleResp singleResponse = singleResponses[0];
                    /* null = GOOD */
                    if (singleResponse.getCertStatus() == CertificateStatus.GOOD) {
                        return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.GOOD, null, null);
                    } else if (singleResponse.getCertStatus() instanceof RevokedStatus) {
                        RevokedStatus revokedStatus = (RevokedStatus) singleResponse.getCertStatus();
                        Date revocationDate = revokedStatus.getRevocationTime();
                        Long revocationTime = revocationDate.getTime() / 1000;
                        if (revokedStatus.hasRevocationReason()) {
                            long reason = revokedStatus.getRevocationReason();
                            if (reason == CRLReason.unspecified) {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.UNSPECIFIED);
                            } else if (reason == CRLReason.keyCompromise) {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.KEY_COMPROMISE);
                            } else if (reason == CRLReason.cACompromise) {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.CA_COMPROMISE);
                            } else if (reason == CRLReason.affiliationChanged) {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.AFFILIATION_CHANGED);
                            } else if (reason == CRLReason.superseded) {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.SUPERSEDED);
                            } else if (reason == CRLReason.cessationOfOperation) {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.CESSATION_OF_OPERATION);
                            } else if (reason == CRLReason.certificateHold) {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.CERTIFICATE_HOLD);
                            } else if (reason == CRLReason.removeFromCRL) {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.REMOVE_FROM_CRL);
                            } else if (reason == CRLReason.privilegeWithdrawn) {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.PRIVILEGE_WITHDRAWN);
                            } else if (reason == CRLReason.aACompromise) {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.AA_COMPROMISE);
                            } else {
                                return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                        OCSPStatusInfoDTO.RevocationReason.UNKNOWN);
                            }
                        } else {
                            return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.REVOKED, revocationTime,
                                    null);
                        }
                    } else {
                        return new OCSPStatusInfoDTO(ocspRespSign, OCSPStatusInfoDTO.CertStatus.UNKNOWN, null,
                                null);
                    }
                } else {
                    return new OCSPStatusInfoDTO(OCSPStatusInfoDTO.RespStatus.SUCCESSFUL);
                }
            } else {
                if (ocspResponse.getStatus() == OCSPResponseStatus.MALFORMED_REQUEST) {
                    return new OCSPStatusInfoDTO(OCSPStatusInfoDTO.RespStatus.MALFORMED_REQUEST);
                } else if (ocspResponse.getStatus() == OCSPResponseStatus.INTERNAL_ERROR) {
                    return new OCSPStatusInfoDTO(OCSPStatusInfoDTO.RespStatus.INTERNAL_ERROR);
                } else if (ocspResponse.getStatus() == OCSPResponseStatus.TRY_LATER) {
                    return new OCSPStatusInfoDTO(OCSPStatusInfoDTO.RespStatus.TRY_LATER);
                } else if (ocspResponse.getStatus() == OCSPResponseStatus.SIG_REQUIRED) {
                    return new OCSPStatusInfoDTO(OCSPStatusInfoDTO.RespStatus.SIG_REQUIRED);
                } else if (ocspResponse.getStatus() == OCSPResponseStatus.UNAUTHORIZED) {
                    return new OCSPStatusInfoDTO(OCSPStatusInfoDTO.RespStatus.UNAUTHORIZED);
                } else {
                    return new OCSPStatusInfoDTO(OCSPStatusInfoDTO.RespStatus.UNKNOWN);
                }
            }

        } catch (Exception e) {
            try {
                Main.log.warn("Cant check OCSP for cert {}", Base64.getEncoder().encodeToString(userCert.getEncoded()), e);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return new OCSPStatusInfoDTO(OCSPStatusInfoDTO.RespStatus.UNKNOWN);
        }
    }
}
