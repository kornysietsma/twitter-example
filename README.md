# twitter-example

This is a simple example program to show how to build a web application that access the Twitter API in Clojure.

It provides a single page web app, using Javascript (well, actually [coffeescript](http://jashkenas.github.com/coffee-script/)) to call JSON apis on the server -
this keeps the server quite simple, a great fit for clojure.

It uses the following libraries and programs:

* Clojure bits:
    * [Compojure](https://github.com/weavejester/compojure) - a great simple Clojure web framework
    * [Ring](https://github.com/mmcgrana/ring) - the web layer underlying Compojure
    * [Leiningen](https://github.com/technomancy/leiningen) - a popular Clojure build tool
    * [lein-ring](https://github.com/weavejester/lein-ring) - to load the app dynamically in development mode
    * [clojure-twitter](https://github.com/mattrepl/clojure-twitter) - for twitter API stuff
    * [clj-oauth](https://github.com/mattrepl/clj-oauth) - for the underlying authentication used by twitter
    * a few others as needed - for other clojure libraries, look in the main 'project.clj' file
* Front end bits:
    * [Sass](http://sass-lang.com/) for nicer CSS syntax
    * [coffeescript](http://jashkenas.github.com/coffee-script/) for a nicer JavaScript syntax
    * JQuery to talk to browsers
    * [handlebars.js](https://github.com/wycats/handlebars.js) to build client-side views
    * Modernizr, Underscore, and a few others to do various bits and pieces

Some of this is based on code and ideas from my [loosely coupled web app skeleton](https://github.com/kornysietsma/lcwa_skeleton)

## Usage

Currently this can't be run standalone - it can only be run via leiningen.

### Install dependencies

* install [Leiningen]
* run "lein deps" to install dependencies - this will download a pile of libraries, and may take some time

### Set up Twitter configuration

This app needs a valid Twitter application to run.  This is pretty easy to build yourself:

* go to https://dev.twitter.com/apps
* click "register a new app"
* enter any details you want
* make sure the application website, and the callback url, match the domain you plan to use.
    * for development, this should be "127.0.0.1" - you can't use "localhost" for some reason, Twitter block it
    * so for example, your callback url and application website should be "http://127.0.0.1/" unless you plan to host on a real host name.
* copy the file (in this project) "resources/config/config_sample.json" to "resources/config/config.json", and update:
    * the host (if not on localhost)
    * the port (if not using leiningen to host - this should really come from the app, I plan to fix this soon)
    * twitter credentials, which you can copy from the Twitter application you just created

### Run the app

* run "lein ring server" - this will build the app, deploy it to a local server on (probably) port 3000, and then display the home page in a browser

## How it works

The Twitter oauth dance is confusing if you haven't met it before.  Twitter have a good description at [https://dev.twitter.com/pages/auth]

It might help however to describe all the steps in this app that happen before a Twitter API is called:

1. At startup, the server ([core.clj](twitter-example/blob/master/src/twitter_example/core.clj)) reads configuration info from `resources/config/config.json`
1. Then the application starts, and users can load the front page - from here on, most activity is driven from the client side
1. When a user first loads the "/" page, the [index.html](twitter-example/blob/master/resources/public/index.html) page is loaded
    * this includes view templates - they aren't visible to the user, but loaded by the javascript for rendering page snippets
1. The code in [twitter-example.coffee](twitter-example/blob/master/views/coffee/twitter-example.coffee) is then loaded (as javascript) - this contains the client-side application logic
1. On page load, the TwitterExample constructor is called, which sets up view logic and calls `check_status()`
1. `check_status()` makes an ajax call to "/auth/check_status.json" on the server
    *. The server URLs are usually handled by the `defroutes` matchers half way down [core.clj](twitter-example/blob/master/src/twitter_example/core.clj) -
but in this case, the user has not yet authorised, so the middleware function `with-oauth-check` kicks in
1. `with-oauth-check` determines there is no auth information in the session, so it starts the oauth interaction with twitter
    1. It makes a call to Twitter to get a request token, based on the application credentials and our local URLs.  The request token is stored in the user's session
    1. It also determines the appropriate URL on twitter to which the user should be redirected
    1. It then returns a HTTP 401 error, with a JSON body containing a message, and an authURL value with the redirect URL
1. The client catches the 401 error and calls `auth-error()` which renders a HTML view "noauth", showing the user the message "Please authorize in Twitter", and a link to the authURL returned from the server
1. When the user clicks on the "authorize" link, they are taken to Twitter, where they can authorize - in which case they are redirected to the app, with the URL "/twitter-oauth-response" with url parameters containing extra data to confirm the user is authorized
1. The server gets the "/twitter-oauth-response" request (it's actually stored in 'oauth-response-path', matching the line `(GET oauth-response-path [oauth_token oauth_verifier]` in core.clj
1. It fetches the request token from the session, and makes another API call to Twitter to convert the request token and verifier into an access token
1. The access token is stored in the session, so future calls to "/auth" URLs won't result in a 401 error.  (note the request token is no longer needed, and should probably be thrown away at this stage)
1. The server then redirects the user back to "/", the starting page of the application
    * a more friendly application could try to store what the user originally requested, and after geting authorization, complete that request, but it's more than this little app can do
1. The client reloads the page again, as before, which calls `check_status()` which calls "/auth/check_status.json"
1. This time the user has an access token in the session, so the `with-oauth-check` middleware does nothing
1. The `defroutes` matcher for "/auth/check_status.json" matches the request, and returns a simple payload consisting of the user's twitter name
1. The client (in the `check_status()` function) renders the user's name, then calls `fetch_tweets()`
1. This results in another ajax call, this time to "/auth/tweets.json"
1. The server matches "/auth/tweets.json" and makes a Twitter API call to retrieve the user's home timeline
1. The JSON response from Twitter is filtered to remove unneeded information, and then returned to the client
1. The client renders the resulting twitter messages.

Phew!  This may look complex, but it's a pretty common workflow for applications trying to do OAuth.  It's actually somewhat simpler in clojure/coffeescript than other languages I've done this in!

## Other stuff

### editing and building CoffeeScript / Sass files
CoffeeScript and Sass are precompilers - they take .coffee and .scss files, and build javascript and CSS respectively.

You can work with the resulting javascript and CSS files if you want - they are in resources/public/javascript and resources/public/stylesheets.
But if you want to make non-trivial changes, you'll want to be able to compile the original sources.

#### Dependencies
You need to install coffeescript - this requires node.js, and explaining how is beyond the scope of this readme.  See [http://jashkenas.github.com/coffee-script/] for more.

You also need to install sass, which is a ruby library - again, beyond the scope of this readme!  See [http://sass-lang.com/] for more.

#### Compiling
You can manually compile the scss/coffee files with:
    ./precompile.sh
Or you can run a background script that compiles files whenever they change with:
    ./watch_all.sh
These only work on Unix/Mac systems - Windows users will have to work out a Windows equivalent.

## To Do
See separate TODO.markdown file

## License

Copyright (c) 2011 Kornelis Sietsma

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.