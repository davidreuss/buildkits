-- Warning: migration functions currently expect semicolons only to be
-- used as separators.

CREATE EXTENSION hstore;

CREATE TABLE buildpacks (
     name varchar PRIMARY KEY,
     attributes hstore
   );

CREATE TABLE kits (
     kit varchar,
     buildpack_name varchar,
     position integer
   );
