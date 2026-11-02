import os, subprocess, json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parent
PROJECT='tv3-live-145-20261101'
ENV=dict(os.environ)
for key in ['DOCKER_HOST','DOCKER_CONTEXT','DOCKER_TLS','DOCKER_TLS_VERIFY','DOCKER_CERT_PATH']:ENV.pop(key,None)
DOCKER=['docker','--host=unix:///var/run/docker.sock']
def owned_container(service):
    ids=subprocess.check_output(DOCKER+['ps','-aq','--filter','label=com.docker.compose.project='+PROJECT,'--filter','label=com.docker.compose.service='+service],env=ENV,text=True).split()
    assert len(ids)==1
    obj=json.loads(subprocess.check_output(DOCKER+['inspect',ids[0]],env=ENV,text=True))[0]
    assert obj['Config']['Labels']['com.docker.compose.project.working_dir']=='/workspace/tv3-pr145-live-acceptance/docker'
    return obj
def query(db,sql):
    pg=owned_container('postgres')
    r=subprocess.run(DOCKER+['exec','-i',pg['Id'],'psql','-X','-qAt','-v','ON_ERROR_STOP=1','-U','hk5','-d',db],input='BEGIN READ ONLY;\n'+sql+';\nROLLBACK;\n',env=ENV,text=True,capture_output=True)
    if r.returncode:raise RuntimeError('Read-only snapshot failed for '+db+': '+r.stderr)
    return json.loads(r.stdout.strip())
def snapshot(directory):
    directory=Path(directory);directory.mkdir(parents=True,exist_ok=True)
    order_sql="""SELECT json_build_object(
      'database',current_database(),'postgres_version',version(),
      'flyway',(select json_agg(x order by x.installed_rank) from (select installed_rank,version,script,checksum,success from flyway_schema_history) x),
      'orders',(select coalesce(json_agg(x order by x.id),'[]') from (select id,customer_id,status,total::text,created_at,payment_method from orders) x),
      'seller_orders',(select coalesce(json_agg(x order by x.id),'[]') from (select id,order_id,store_id,seller_id,status,cod_status,gross::text,platform_fee::text,version from seller_orders) x),
      'items',(select coalesce(json_agg(x order by x.id),'[]') from (select id,seller_order_id,product_id,sku,quantity,unit_price_snapshot::text,line_total::text from order_items) x),
      'attempts',(select coalesce(json_agg(x order by x.id),'[]') from (select id,customer_id,fingerprint,state,order_id from checkout_attempts) x),
      'drafts',(select coalesce(json_agg(x order by x.checkout_attempt_id),'[]') from (select checkout_attempt_id,snapshot_version,payload_sha256,encode(sha256(convert_to(payload,'UTF8')),'hex')=payload_sha256 as checksum_matches,created_at from checkout_order_drafts) x),
      'journals',(select coalesce(json_agg(x order by x.operation_id),'[]') from (select operation_id,checkout_attempt_id,operation_type,canonical_lines,payload_fingerprint,state,send_attempts,last_error_code from inventory_operation_journal) x),
      'outbox',(select coalesce(json_agg(x order by x.id),'[]') from (select id,topic,aggregate_id,published_at,attempts,dead,encode(sha256(convert_to(payload,'UTF8')),'hex') as payload_sha256 from outbox_events) x))"""
    inventory_sql="""SELECT json_build_object(
      'database',current_database(),'postgres_version',version(),
      'flyway',(select json_agg(x order by x.installed_rank) from (select installed_rank,version,script,checksum,success from flyway_schema_history) x),
      'balances',(select coalesce(json_agg(x order by x.sku),'[]') from (select sku,available_quantity as available,reserved_quantity as reserved,version from inventory_item) x),
      'operations',(select coalesce(json_agg(x order by x.operation_id),'[]') from (select operation_id,operation_type,request_hash from inventory_operation) x))"""
    for db,sql in [('order_db',order_sql),('inventory_db',inventory_sql)]:
        (directory/(db+'.json')).write_text(json.dumps(query(db,sql),ensure_ascii=False,indent=2)+'\n')
    (directory/'PRIVACY.txt').write_text('Read-only PostgreSQL oracle. No shipping fields, passwords, tokens, idempotency keys, raw draft/outbox payloads or account email addresses are exported. Synthetic test identifiers and money/stock facts only.\n')
if __name__=='__main__':snapshot(sys.argv[1]);print('Read-only snapshot saved:',sys.argv[1])
