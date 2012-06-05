# Build Kits

Select from available Build Packs to build up your own build kit.

## Buildpack metadata

- Name
- Description
- Author
- URL
- License

## Running

    # Database first:
    $ initdb pg && postgres -D pg
    $ createdb buildkits
    $ lein run -m buildkits.db/migrate
    $ lein run -m buildkits.db/insert-dummy-data dev-resources/buildpacks.clj # optional

You may need to add `/usr/lib/postgresql/$PG_VERSION/bin` to your
`$PATH` first on Debian-based systems. You'll also need a new enough
version of PostgreSQL to have HStore support.

    $ lein run -m buildkits.web

## License

Copyright © 2012 Heroku, Inc.

Distributed under the Eclipse Public License, the same as Clojure.
