-- Warning: migration functions currently expect semicolons only to be
-- used as separators.

CREATE EXTENSION hstore;

CREATE TABLE buildpacks (
     name varchar PRIMARY KEY,
     attributes hstore
   );

CREATE TABLE kits (
     name varchar PRIMARY KEY,
     buildpacks hstore
   );

CREATE TABLE ratings (
     id serial PRIMARY KEY,
     rater varchar,
     buildpack varchar
   );
