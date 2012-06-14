# Build Kits

Select from available Build Packs to build up your own kit.

[Log in](https://buildkits.herokuapp.com) and make your selections,
then use [anvil](https://github.com/ddollar/anvil) to perform builds
with them.

## Developing Buildpacks

You can use Anvil to test buildpacks without publishing them:

    $ heroku plugins:install https://github.com/ddollar/heroku-anvil
    $ cd myproject
    $ heroku build -b /path/to/local/buildpack
    Generating app manifest... done
    Uploading new files... done, 0 files needed
    Generating buildpack manifest... done
    Uploading new files... done, 0 files needed
    Launching build process... done 
    Recreating app from manifest... done 
    Fetching buildpack... done 
    Detecting buildpack... done, Clojure 
    Fetching cache... empty 
    Compiling app... 
      Installing Leiningen
      Downloading: leiningen-1.7.1-standalone.jar
      Writing: lein script
      Running: LEIN_NO_DEV=y lein deps
    [...]
    Creating slug... done 
    Uploading slug... done 
    Success, slug is https://anvil.herokuapp.com/slugs/bbbde9d0-b650-11e1-9829-659a6807d866.img 

Once you're happy with your buildpack, you can publish it for others
to use with the
[heroku buildpacks plugin](https://github.com/ddollar/heroku-buildpacks):

    $ heroku plugins:install https://github.com/ddollar/heroku-buildpacks
    $ cd heroku-buildpack-clojure
    $ heroku buildpacks:publish clojure
    Publishing clojure buildpack... done

## Running

    # Database first:
    $ initdb pg && postgres -D pg
    $ createdb buildkits
    $ lein run -m buildkits.db/migrate

You may need to add `/usr/lib/postgresql/$PG_VERSION/bin` to your
`$PATH` first on Debian-based systems. You'll also need a new enough
version of PostgreSQL to have HStore support.

    $ lein run -m buildkits.web

## License

Copyright © 2012 Heroku, Inc.

Distributed under the Eclipse Public License, the same as Clojure.
