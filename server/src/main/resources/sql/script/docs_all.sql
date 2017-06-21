SELECT r.id, r.hash, r.src, r.name, r.type, r.size,
  case r.author when ? then r.time else s.time end as time,
  r.author,
  case r.author when ? then r.status else s.status end as status,
  case r.author when ? then r.tags else s.tags end as tags,
  case r.author when ? then r.signed else s.status%10=3 end as signed,
  case r.author when ? then r.company_id else s.company_id end as company_id,
  s.user_login, s.status, r.status
FROM resource r
  LEFT JOIN share s ON (r.id = s.resource_id AND NOT r.author = ?)
WHERE (r.author = ? OR s.user_login = ?) AND r.id in ( with msel as
(
    SELECT
      DISTINCT r.id,
      case r.author when ? then r.time else s.time end as time
    FROM resource r LEFT JOIN share s ON (r.id = s.resource_id )
    WHERE (
      (
        (r.author = ? AND r.status < 10 ) AND (r.company_id = ? <#if isMy> OR r.company_id ISNULL </#if>) <#if tags> AND ((r.tags & ? ) > 0) </#if>
         <#if dateFrom> AND ( r.time >= ?) </#if> <#if dateTo> AND ( r.time <= ?) </#if>
      ) OR (
        (s.user_login = ? AND s.status < 10 ) AND (s.company_id = ? <#if isMy> OR s.company_id ISNULL </#if> ) <#if tags> AND ((s.tags & ? ) > 0) </#if>
         <#if dateFrom> AND ( s.time >= ?) </#if> <#if dateTo> AND ( s.time <= ?) </#if>
      )
    ) <#if searchQuery> AND (r.name like ? OR r.author like ? OR s.user_login like ? )</#if>
      <#if signedStatus??>
        <#if signedStatus>
          AND ((s.user_login = ? AND s.status%10=3 ) OR (r.author = ? AND r.signed))
        <#else>
          AND ((s.user_login = ? AND not s.status%10=3 ) OR (r.author = ? AND (NOT r.signed OR r.signed ISNULL )))
        </#if>
      </#if>
    ORDER BY time DESC
)
select id from msel LIMIT ? OFFSET ?
)
ORDER BY time DESC;