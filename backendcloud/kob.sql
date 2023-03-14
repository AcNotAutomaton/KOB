-- we don't know how to generate root <with-no-name> (class Root) :(
create table bot
(
    id          int auto_increment,
    user_id     int              not null,
    title       varchar(100)     null,
    description varchar(300)     null,
    content     varchar(10000)   null,
    rating      int default 1500 null,
    createtime  datetime         null,
    modifytime  datetime         null,
    constraint table_name_id_uindex
        unique (id)
)
    engine = InnoDB;

alter table bot
    add primary key (id);

create table record
(
    id         int auto_increment
        primary key,
    a_id       int           null,
    a_sx       int           null,
    a_sy       int           null,
    b_id       int           null,
    b_sx       int           null,
    b_sy       int           null,
    a_steps    varchar(1000) null,
    b_steps    varchar(1000) null,
    map        varchar(1000) null,
    loser      varchar(10)   null,
    createtime datetime      null
)
    engine = InnoDB;

create table user
(
    id       int auto_increment
        primary key,
    username varchar(100)     null,
    password varchar(100)     null,
    photo    varchar(1000)    null,
    rating   int default 1500 null
)
    engine = InnoDB;

