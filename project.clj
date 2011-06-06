(defproject twitter-example "1.0.0-SNAPSHOT"
  :description "example clojure / compojure twitter application"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/clojure-contrib  "1.2.0"]
                 [clojure-twitter/clojure-twitter "1.2.5"]
                 [compojure "0.6.3"]
                 [clj-oauth "1.2.10-SNAPSHOT"]
                 [clj-json "0.3.2"]
                 [ring-json-params "0.1.3"]
                 [sandbar "0.4.0-SNAPSHOT"]
                 [ring/ring-core "0.3.8"]
                 [ring/ring-jetty-adapter "0.3.8"]]
  :dev-dependencies [[lein-ring "0.4.0"]
                     [midje "1.1.1"]]
  :ring {:handler twitter-example.core/app})
