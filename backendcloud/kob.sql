create table bot
(
    id          int auto_increment
        primary key,
    user_id     int not null,
    title       varchar(200),
    description varchar(500),
    content     varchar(10000),
    rating      int default 1200,
    create_time datetime,
    modify_time datetime,
    constraint table_name_id_uindex
        unique (id)
);

create table record
(
    id          int auto_increment
            primary key,
    a_id        int,
    a_sx        int,
    a_sy        int,
    b_id        int,
    b_sx        int,
    b_sy        int,
    a_steps     varchar(1500),
    b_steps     varchar(1500),
    map         varchar(1500),
    loser       varchar(15),
    create_time datetime,
    constraint record_id_uindex
    unique (id)
);

create table user
(
    id       int auto_increment
    primary key,
    username varchar(100),
    password varchar(100),
    rating   int default 1200,
    photo    varchar(1000),
    constraint user_id_uindex
    unique (id)
);