-- BD version 9 --
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS resource CASCADE;
DROP TABLE IF EXISTS share CASCADE;
DROP TABLE IF EXISTS sign_info CASCADE;
DROP TABLE IF EXISTS spam CASCADE;
DROP TABLE IF EXISTS companies CASCADE;
DROP TABLE IF EXISTS employees CASCADE;

-- Table: users
CREATE TABLE if NOT EXISTS users
(
  login            VARCHAR (255) PRIMARY KEY,
  description      TEXT,
  full_name        TEXT,
  reg_info         TEXT,
  password_digest  VARCHAR (43) NOT NULL,
  security_descr   BIGINT       NOT NULL,
  friends          TEXT[],
  spam_mode        INT,
  create_time      BIGINT,
  heavy            BOOLEAN DEFAULT FALSE,
  auth_code        TEXT DEFAULT NULL,
  company_id       BIGINT DEFAULT NULL,
  blocked          BIGINT DEFAULT 0,
  egrpou           TEXT[]
)
WITH (OIDS = FALSE);
ALTER TABLE users
  OWNER TO papka24;
CREATE INDEX i_users_ctime ON users(create_time);

-- Table: resource
CREATE TABLE if NOT EXISTS resource
(
  id      BIGSERIAL PRIMARY KEY,
  hash    CHAR (43) NOT NULL,
  src     TEXT,
  name    TEXT,
  type    INT,
  size    INT,
  time    BIGINT,
  author  VARCHAR(255) NOT NULL,
  status  INT,
  signed  BOOLEAN,
  signs   TEXT[],
  tags    BIGINT,
  company_id BIGINT,
  bot     BOOLEAN
)
WITH (OIDS = FALSE);
ALTER TABLE resource
  OWNER TO papka24;
-- Index: resource_index
CREATE INDEX resource_index_hash
ON resource USING BTREE (hash COLLATE pg_catalog."default");
-- Index: resource_index
DROP INDEX IF EXISTS resource_index_author;
CREATE INDEX resource_index_author
ON resource USING BTREE (author COLLATE pg_catalog."default");
CREATE INDEX resource_index_status on resource USING BTREE (status);
CREATE INDEX resource_index_tags on resource USING BTREE (tags);

-- Table: share
CREATE TABLE if NOT EXISTS share
(
  user_login  VARCHAR(255) NOT NULL,
  resource_id BIGINT       NOT NULL,
  status      INT,
  time        BIGINT,
  tags        BIGINT,
  spam_mode   INT DEFAULT 0,
  company_id  BIGINT,
  initiator   TEXT,
  comment     TEXT
)
WITH (OIDS=FALSE);
ALTER TABLE share
  OWNER TO papka24;
-- Index: resource_index
DROP INDEX IF EXISTS share_index_user;
CREATE INDEX share_index_user
ON share USING BTREE (user_login COLLATE pg_catalog."default");
DROP INDEX IF EXISTS share_index_res_id;
CREATE INDEX share_index_res_id
ON share USING BTREE (resource_id);
CREATE INDEX share_index_status on share USING BTREE (status);
CREATE INDEX share_index_tags on share USING BTREE (tags);

-- Table: sign_info
CREATE TABLE if NOT EXISTS sign_info
(
  hash        TEXT PRIMARY KEY,
  status      INT,
  sign_info   TEXT NOT NULL,
  ocsp_info   TEXT NOT NULL,
  time        BIGINT
)
WITH (OIDS=FALSE);
ALTER TABLE sign_info
  OWNER TO papka24;
-- Index: sign_info_index
DROP INDEX IF EXISTS share_index_hash;
CREATE INDEX share_index_hash
ON sign_info USING BTREE (hash);

-- Table: spam
CREATE TABLE if NOT EXISTS spam
(
  user_login  VARCHAR(255) NOT NULL,
  resource_id BIGINT,
  time        BIGINT,
  type        INT
)
WITH (OIDS=FALSE);
ALTER TABLE spam
  OWNER TO papka24;
-- Index: spam_index
DROP INDEX IF EXISTS spam_index_user;
CREATE INDEX spam_index_user
ON spam USING BTREE (user_login);

-- CLEAR resource DELETE FROM share WHERE resource_id IN (SELECT r.id FROM resource AS r JOIN share AS s ON (r.id = s.resource_id) WHERE s.user_login = r.author);

-- Table: subscribers
create table if NOT EXISTS subscribers(
  sub_id  SERIAL PRIMARY KEY,
  id VARCHAR(255) not null,
  url TEXT not null,
  time BIGINT not null,
  author VARCHAR(255),
  type integer DEFAULT 1,
  event_type text[]
)
WITH (OIDS = FALSE);
ALTER TABLE subscribers
  OWNER TO papka24;
-- index: subscribers_index_id
drop index if EXISTS subscribers_index_id;
create INDEX subscribers_index_id
on subscribers USING BTREE (id COLLATE pg_catalog."default");

-- Table: companies
CREATE TABLE if NOT EXISTS companies
(
  id               BIGSERIAL PRIMARY KEY,
  egrpou           BIGINT,
  name             TEXT,
  info             TEXT,
  created_cert     TEXT,
  contacts         TEXT DEFAULT NULL
)
WITH (OIDS = FALSE);
ALTER TABLE companies
  OWNER TO papka24;

-- Table: employees
CREATE TABLE if NOT EXISTS employees
(
  company_id        BIGINT,
  login             VARCHAR (255),
  role              BIGINT,
  start_date        BIGINT,
  stop_date         BIGINT,
  status            BIGINT,
  initiator         VARCHAR (255),
  remove_initiator  TEXT
)
WITH (OIDS = FALSE);
ALTER TABLE employees
  OWNER TO papka24;
--index: employees_index_company_id
drop index if EXISTS employees_index_company_id;
create index employees_index_company_id
on employees(company_id);

-- Table: sign_record
DROP TABLE IF EXISTS sign_record;
CREATE TABLE if NOT EXISTS sign_record
(
  resource_id      BIGINT,
  time             BIGINT,
  login            TEXT,
  hash             TEXT,
  egrpou           BIGINT,
  company          TEXT,
  inn              BIGINT
)
WITH (OIDS = FALSE);
ALTER TABLE sign_record
  OWNER TO papka24;
-- Index: sign_record_index_resource_id
DROP INDEX IF EXISTS sign_record_index_resource_id;
CREATE INDEX sign_record_index_resource_id
ON sign_record (resource_id);
DROP INDEX IF EXISTS sign_record_egrpou_index;
CREATE INDEX sign_record_egrpou_index ON sign_record USING BTREE(egrpou);

-- Table: friends_catalog
CREATE TABLE if NOT EXISTS friends_catalog
(
  login      TEXT,
  egrpou     BIGINT,
  name       TEXT,
  logins     TEXT[]
)
WITH (OIDS = FALSE);
ALTER TABLE friends_catalog
  OWNER TO papka24;
-- Index: friends_catalog_index_login
DROP INDEX IF EXISTS friends_catalog_index_login;
CREATE INDEX friends_catalog_index_login
ON friends_catalog USING BTREE (login COLLATE pg_catalog."default");

create table blocked(
  id BIGINT not null,
  type BIGINT not null,
  reason TEXT
)
WITH (OIDS = FALSE);;
ALTER TABLE users
  OWNER TO papka24;;
create index blocked_id_index
  on blocked USING BTREE (id);;

--table spam mode
create table if not EXISTS spam_mode(
  login text PRIMARY KEY,
  mode int NOT NULL DEFAULT 0
)
WITH (OIDS=FALSE);
ALTER TABLE spam_mode
  OWNER TO papka24;

--table diff
create table if not EXISTS diff_sync(
  login text,
  date  BIGINT NOT NULL,
  PRIMARY KEY (login)
)
WITH (OIDS = FALSE);
ALTER TABLE diff_sync
  OWNER TO papka24;

CREATE TABLE IF NOT EXISTS delivery(
  id BIGSERIAL PRIMARY KEY,
  login TEXT,
  egrpou BIGINT,
  type INT,
  message TEXT,
  time_send BIGINT,
  time_answer BIGINT,
  result TEXT,
  egrpou_list TEXT[]
)WITH (OIDS = FALSE);
DROP INDEX IF EXISTS delivery_login_index;
CREATE INDEX delivery_login_index on delivery(login);
DROP INDEX IF EXISTS delivery_egrpou_index;
CREATE INDEX delivery_egrpou_index on delivery(egrpou);
CREATE INDEX i_delivery_time_answer on delivery(time_answer);
CREATE INDEX i_delivery_result on delivery(result);

ALTER TABLE resource
  OWNER TO papka24;

--from P24 friends list
CREATE TABLE partners_p24(
  main_egrpou TEXT,
  sm NUMERIC,
  egrpou TEXT,
  need_invite BOOLEAN,
  time BIGINT,
  initiator TEXT,
  want_invite TEXT[],
  known BOOLEAN,
  company_name TEXT
)WITH (OIDS = FALSE);
ALTER TABLE partners_p24
  OWNER TO papka24;
create index i_partners_p24_known on partners_p24(known);

--from user friend list
CREATE TABLE partners_user(
  egrpou TEXT,
  need_invite BOOLEAN,
  time BIGINT,
  initiator TEXT,
  want_invite TEXT[],
  known BOOLEAN,
  company_name TEXT
)WITH (OIDS = FALSE);
ALTER TABLE partners_user
  OWNER TO papka24;

CREATE TABLE partner_winfo(
  login TEXT,
  egrpou TEXT[],
  time BIGINT
)WITH (OIDS = FALSE);
ALTER TABLE partner_winfo
  OWNER TO papka24;

--create
CREATE TABLE statistic(
  resource_count	BIGINT,
  users_count     BIGINT,
  share_count     BIGINT,
  sign_count      BIGINT
)WITH (OIDS = FALSE);
ALTER TABLE statistic
  OWNER TO papka24;

CREATE TABLE statistic_trends(
  day       TEXT PRIMARY KEY,
  users     BIGINT,
  docs      BIGINT,
  shares    BIGINT,
  signs     BIGINT,
  day_start BIGINT
)WITH (OIDS = FALSE);
ALTER TABLE statistic_trends
  OWNER TO papka24;


CREATE TABLE billing_prefer_egrpou(
  login   TEXT,
  egrpou  BIGINT,
  company TEXT,
  time    BIGINT
)WITH (OIDS = FALSE);
ALTER TABLE billing_prefer_egrpou
  OWNER TO papka24;

CREATE TABLE billing_resources(
  resource_id BIGINT PRIMARY KEY,
  author      TEXT,
  egrpou      BIGINT,
  inn         BIGINT,
  time        BIGINT,
  name        TEXT,
  company     TEXT,
  schema      INT,
  commit_time BIGINT
)WITH (OIDS = FALSE);

CREATE TABLE catalog_pb(
  login TEXT,
  initiator TEXT,
  time      BIGINT
)WITH (OIDS = FALSE);
ALTER TABLE catalog_pb
  OWNER TO papka24;

CREATE INDEX i_billing_resources_egrpou on billing_resources(egrpou);
CREATE INDEX i_billing_resources_inn on billing_resources(inn);
CREATE INDEX i_billing_resources_time on billing_resources(time);
CREATE INDEX i_billing_prefer_egrpou_login on billing_prefer_egrpou(login);
CREATE INDEX i_billing_resources_author on billing_resources(author);

CREATE TABLE analytics_registration(
  login TEXT PRIMARY KEY,
  firstShareDate BIGINT,
  firstShareDocAuthor TEXT,
  firstCompanyRequestDate BIGINT,
  firstCompanyRequestInitiator TEXT
)WITH (OIDS = FALSE);
ALTER TABLE analytics_registration
  OWNER TO papka24;

CREATE TABLE additional_agreement(
  company_name  text,
  primary_login text,
  agreement_name  text
)WITH (OIDS = FALSE);;
ALTER TABLE additional_agreement
  OWNER TO papka24;

--36
CREATE TABLE resource_cache(
  owner   VARCHAR(255) NOT NULL,
  id      BIGINT NOT NULL,
  hash    CHAR (43) NOT NULL,
  src     TEXT,
  name    TEXT,
  type    INT,
  size    INT,
  time    BIGINT,
  author  VARCHAR(255) NOT NULL,
  status  INT,
  signed  BOOLEAN,
  tags    BIGINT,
  company_id BIGINT,
  created  BOOLEAN,
  delete_by_creator BOOLEAN,
  PRIMARY KEY (owner, id)
)WITH (OIDS = FALSE);
ALTER TABLE resource_cache
  OWNER TO papka24;

CREATE INDEX i_resource_cache_owner on resource_cache(owner);
CREATE INDEX i_resource_cache_hash on resource_cache(hash);
CREATE INDEX i_resource_cache_id on resource_cache(id);
CREATE INDEX i_resource_cache_company_id on resource_cache(company_id);
CREATE INDEX i_resource_cache_time ON resource_cache(time);
CREATE INDEX i_resource_cache_tags ON resource_cache(tags);

create table resource_cache_0 ( like resource_cache including all );
alter table resource_cache_0 inherit resource_cache;

create table resource_cache_1 ( like resource_cache including all );
alter table resource_cache_1 inherit resource_cache;

create table resource_cache_2 ( like resource_cache including all );
alter table resource_cache_2 inherit resource_cache;

create table resource_cache_3 ( like resource_cache including all );
alter table resource_cache_3 inherit resource_cache;

create table resource_cache_4 ( like resource_cache including all );
alter table resource_cache_4 inherit resource_cache;

create table resource_cache_5 ( like resource_cache including all );
alter table resource_cache_5 inherit resource_cache;

create table resource_cache_6 ( like resource_cache including all );
alter table resource_cache_6 inherit resource_cache;

create table resource_cache_7 ( like resource_cache including all );
alter table resource_cache_7 inherit resource_cache;

create table resource_cache_8 ( like resource_cache including all );
alter table resource_cache_8 inherit resource_cache;

create table resource_cache_9 ( like resource_cache including all );
alter table resource_cache_9 inherit resource_cache;

alter table resource_cache_0 add constraint partition_check check (abs(hashtext(owner)) % 10 = 0);
alter table resource_cache_1 add constraint partition_check check (abs(hashtext(owner)) % 10 = 1);
alter table resource_cache_2 add constraint partition_check check (abs(hashtext(owner)) % 10 = 2);
alter table resource_cache_3 add constraint partition_check check (abs(hashtext(owner)) % 10 = 3);
alter table resource_cache_4 add constraint partition_check check (abs(hashtext(owner)) % 10 = 4);
alter table resource_cache_5 add constraint partition_check check (abs(hashtext(owner)) % 10 = 5);
alter table resource_cache_6 add constraint partition_check check (abs(hashtext(owner)) % 10 = 6);
alter table resource_cache_7 add constraint partition_check check (abs(hashtext(owner)) % 10 = 7);
alter table resource_cache_8 add constraint partition_check check (abs(hashtext(owner)) % 10 = 8);
alter table resource_cache_9 add constraint partition_check check (abs(hashtext(owner)) % 10 = 9);

create function resource_cache_partition() returns trigger as $$
DECLARE
  parition_name text;
BEGIN
  parition_name := format( 'resource_cache_%s', abs(hashtext(NEW.owner)) % 10 );
  execute 'INSERT INTO ' || parition_name || ' VALUES ( ($1).* )' USING NEW;
  return NULL;
END;
$$ language plpgsql;

create trigger partition_resource_cache_insert before insert on resource_cache for each row execute procedure resource_cache_partition();

GRANT SELECT ON ALL TABLES IN SCHEMA public TO papka24_ro;
COMMENT ON table users is '36';