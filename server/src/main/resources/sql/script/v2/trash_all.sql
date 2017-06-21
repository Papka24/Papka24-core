SELECT rc.id, rc.hash, rc.src, rc.name, rc.type, rc.size, rc.time, rc.author, rc.status, rc.tags, rc.signed, rc.company_id, rc.created, rc.owner, rc.delete_by_creator
FROM resource_cache rc
WHERE rc.owner = ? AND (abs(hashtext(?)) % 10 = abs(hashtext(rc.owner)) %10)
     <#if companyId??>
        AND (rc.company_id = ? <#if isMy> OR rc.company_id ISNULL </#if>)
     <#else> AND (rc.company_id IS NULL)
     </#if>
      AND rc.status >= 10 AND rc.status < 20
      <#if dateFrom> AND rc.time >= ? </#if>
      <#if dateTo> AND rc.time < ? </#if>
      <#if tags> AND (rc.tags & ?) > 0 </#if>
      <#if signedStatus??>
          <#if signedStatus> AND rc.signed
          <#else> AND (NOT rc.signed OR rc.signed IS NULL)
          </#if>
      </#if>
      <#if searchQuery>
        AND ((rc.name like ?) OR (rc.author like ?) OR (select array_to_string(array_agg(user_login), '', '') like ? from share where resource_id = rc.id))
      </#if>
      <#if contractor??>
      <#list contractor as nm>
          AND ((rc.name like ?) OR (rc.author like ?) OR (select array_to_string(array_agg(user_login), '', '') like ? from share where resource_id = rc.id))
      </#list>
      </#if>
ORDER BY rc.time DESC
LIMIT ? OFFSET ?