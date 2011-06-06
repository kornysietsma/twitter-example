(ns twitter-example.core-test
  (:use
    midje.sweet
    twitter-example.core
    compojure.core
    [compojure.response :only [resource]])
  (:require
    [clj-json.core :as json]
    [clojure.java.io :as io]))

(def fixture-path "test/twitter_example/fixtures/")

(def single-tweet-fixture
  (first (json/parsed-seq (io/reader (str fixture-path "single_tweet.json")) true)))

(facts "about utilities"
  (filtered-tweet {}) => {:screen-name nil, :tweet nil}
  (filtered-tweet {:text "tweet", :user {:screen_name "tweety"}}) => {:screen-name "tweety", :tweet "tweet"}
  (filtered-tweet single-tweet-fixture) => {:screen-name "izaloko", :tweet "qu por qu kieres saver como poner pablito"})

(facts "about the main routes"
  "Bare request returns index file"
  (main-routes {:uri "/" :request-method :get})
  => (contains {:status 200
                :body #"stuff"})
  (provided
    (resource "public/index.html") => "stuff")
  )