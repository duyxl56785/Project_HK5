-- order_sql (run in BEGIN READ ONLY / ROLLBACK)
SELECT json_build_object(
      'database',current_database(),'postgres_version',version(),
      'flyway',(select json_agg(x order by x.installed_rank) from (select installed_rank,version,script,checksum,success from flyway_schema_history) x),
      'orders',(select coalesce(json_agg(x order by x.id),'[]') from (select id,customer_id,status,total::text,created_at,payment_method from orders) x),
      'seller_orders',(select coalesce(json_agg(x order by x.id),'[]') from (select id,order_id,store_id,seller_id,status,cod_status,gross::text,platform_fee::text,version from seller_orders) x),
      'items',(select coalesce(json_agg(x order by x.id),'[]') from (select id,seller_order_id,product_id,sku,quantity,unit_price_snapshot::text,line_total::text from order_items) x),
      'attempts',(select coalesce(json_agg(x order by x.id),'[]') from (select id,customer_id,fingerprint,state,order_id from checkout_attempts) x),
      'drafts',(select coalesce(json_agg(x order by x.checkout_attempt_id),'[]') from (select checkout_attempt_id,snapshot_version,payload_sha256,encode(sha256(convert_to(payload,'UTF8')),'hex')=payload_sha256 as checksum_matches,created_at from checkout_order_drafts) x),
      'journals',(select coalesce(json_agg(x order by x.operation_id),'[]') from (select operation_id,checkout_attempt_id,operation_type,canonical_lines,payload_fingerprint,state,send_attempts,last_error_code from inventory_operation_journal) x),
      'outbox',(select coalesce(json_agg(x order by x.id),'[]') from (select id,topic,aggregate_id,published_at,attempts,dead,encode(sha256(convert_to(payload,'UTF8')),'hex') as payload_sha256 from outbox_events) x));

-- inventory_sql (run in BEGIN READ ONLY / ROLLBACK)
SELECT json_build_object(
      'database',current_database(),'postgres_version',version(),
      'flyway',(select json_agg(x order by x.installed_rank) from (select installed_rank,version,script,checksum,success from flyway_schema_history) x),
      'balances',(select coalesce(json_agg(x order by x.sku),'[]') from (select sku,available_quantity as available,reserved_quantity as reserved,version from inventory_item) x),
      'operations',(select coalesce(json_agg(x order by x.operation_id),'[]') from (select operation_id,operation_type,request_hash from inventory_operation) x));
