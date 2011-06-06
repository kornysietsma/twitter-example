(ns twitter-example.core-test
  (:use
    midje.sweet
    twitter-example.core
    compojure.core
    sandbar.stateful-session
    [compojure.response :only [resource]])
  (:require
    [clj-json.core :as json]
    [clojure.java.io :as io]))

(def fixture-path "test/twitter_example/fixtures/")

(def single-tweet-fixture
  (first (json/parsed-seq (io/reader (str fixture-path "single_tweet.json")) true)))

(facts "about filtering tweets"
  (filtered-tweet {}) => {:screen_name nil, :tweet nil}
  (filtered-tweet {:text "tweet", :user {:screen_name "tweety"}}) => {:screen_name "tweety", :tweet "tweet"}
  (filtered-tweet single-tweet-fixture) => {:screen_name "izaloko", :tweet "qu por qu kieres saver como poner pablito"})

(facts "about the main routes"
  "Bare request returns index file"
  (main-routes {:uri "/" :request-method :get})
  => (contains {:status 200
                :body "stuff"})
  (provided
    (resource "public/index.html") => "stuff")

  "On redirect from twitter oauth, tokens are stored in session and user is redirected to '/'"
  (main-routes {:uri "/twitter_oauth_response"
                :request-method :get
                :params {:oauth_verifier "verifier" :oauth_token "token"}})
  => (contains {:status 302 :headers {"Location" "/"}})
  (provided
    (session-get :request-token) => ...request_token...
    (access-token-response ...request_token... "verifier") => ...oauth_response...
    (session-put! :twitter-oauth ...oauth_response...) => anything))


; These are really integration tests - wrap-oauth is wired in to the app at startup, so can't easily be mocked out
(facts "about wrap-oauth middleware"
  "wrap-oauth provides twitter information to later handlers if it is available in the session"
  (app {:uri "/auth/status.json" :request-method :get})
  => (contains {:status 200 :body (json/generate-string {:name "freddy"})})
  (provided
    (session-get :twitter-oauth) => {:screen_name "freddy"})

  "wrap-oauth returns a 401 error containing a twitter redirect url if no twitter information is in the session"
  (app {:uri "/auth/status.json" :request-method :get})
  => (contains {:status 401 :body (json/generate-string {:authUrl "the_url"})})
  (provided
    (session-get :twitter-oauth) => nil
    (twitter-request-token) => ...request_token...
    (callback-uri ...request_token...) => "the_url"))
