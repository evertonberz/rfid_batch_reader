CREATE TABLE hit (
    idhit bigserial,
    timelimit integer,
    power integer,
    frequency integer,
    epc character varying(120),
    testname character varying(30),
    protocol integer,
    antenna integer,
    "timestamp" character varying(20),
    current_datetime timestamp without time zone
);
