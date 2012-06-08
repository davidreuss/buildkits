-- Warning: migration functions currently expect semicolons only to be
-- used as separators.

CREATE EXTENSION hstore;

CREATE TABLE buildpacks (
     name varchar PRIMARY KEY,
     tarball bytea NOT NULL,
     attributes hstore
   );

CREATE TABLE kits (
     kit varchar PRIMARY KEY,
     buildpack_name varchar NOT NULL,
     position integer NOT NULL
   );
