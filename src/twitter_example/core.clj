(ns twitter-example.core
  (:use compojure.core
    [compojure.response :only [resource]]
    ring.middleware.json-params
    ring.middleware.session
    sandbar.stateful-session
    [ring.adapter.jetty :only [run-jetty]]
    clojure.contrib.except
    [clojure.contrib.def :only [defn-memo]])
  (:require [compojure.route :as route]
    [compojure.handler :as handler]
    [clj-json.core :as json]
    [oauth.client :as oauth]
    [clojure.java.io :as io]
    [ring.util.response :as response]
    twitter))

(defn make-consumer
  "construct a Twitter consumer"
  [key secret]
  (oauth/make-consumer key secret
    "https://api.twitter.com/oauth/request_token"
    "https://api.twitter.com/oauth/access_token"
    "https://api.twitter.com/oauth/authorize"
    :hmac-sha1))

(def oauth-response-path "/twitter_oauth_response")

(defn-memo config
  "configuration constructed from environment variables - memoized!"
  []
  (let [env (System/getenv)
        key (get env "TWITTER_KEY")
        secret (get env "TWITTER_SECRET")
        host (get env "CALLBACK_HOST")
        port (get env "CALLBACK_PORT")
        callback (str "http://" host ":" port oauth-response-path)]
    (throw-if-not (and key secret host port) "Not all required environment variables set")
    {:consumer (make-consumer key secret)
     :callback callback}))

(defn consumer
  "twitter consumer from global config"
  [] (:consumer (config)))

(defn oauth-response-callback
  "the url on our site that Twitter should redirect users back to"
  [] (:callback (config)))

(defn twitter-request-token
  "fetch request token from twitter to start the oauth authorization dance"
  []
  (oauth/request-token (consumer) (oauth-response-callback)))

(defn callback-uri
  "callback uri (on Twitter) to which we send a user to start authorization, once we have a request token"
  [request-token]
  (oauth/user-approval-uri (consumer)
    (:oauth_token request-token)))

(defn access-token-response
  "get access token from twitter once twitter has redirected back to the app"
  [request-token verifier]
  (oauth/access-token (consumer)
    request-token
    verifier))

(defn json-response
  "return a JSON formatted Ring response"
  [data]
  (-> (response/response (json/generate-string data))
    (response/content-type "application/json")))

(defn filtered-tweet
  "filter out just the parts of the tweet wanted by the UI"
  [tweet]
  (let [{text :text {screen_name :screen_name} :user} tweet]
    {:screen_name screen_name :tweet text}))

(defroutes main-routes
  (GET "/" [] (resource "public/index.html"))
  (GET oauth-response-path [oauth_token oauth_verifier]
    (let [request-token (session-get :request-token)
          resp (access-token-response request-token oauth_verifier)]
      (session-put! :twitter-oauth resp)
      (response/redirect "/")))
  (GET "/auth/tweets.json" {oauth :twitter-oauth}
    (twitter/with-oauth (consumer) (:oauth_token oauth) (:oauth_token_secret oauth)
      (json-response {:name (:screen_name oauth)
                      :tweets (map filtered-tweet (twitter/home-timeline))})))
  (GET "/auth/status.json" {oauth :twitter-oauth}
    (json-response
      {:name (:screen_name oauth)}))
  (route/resources "/")
  (route/not-found "Page not found"))

(defn wrap-oauth
  "middelware wrapper for twitter oauth credential check
   only checks requests starting with '/auth'
   if signed in, stores credentials in :twitter-oauth and continues
   if not signed in, returns a 401 error, including the callback URL needed to sign in,
   so clients can prompt the user to start the OAuth dance!"
  [handler]
  (fn [request]
    (if (re-matches #"/auth/.*" (:uri request))
      (if-let [oauth (session-get :twitter-oauth)]
        (handler (assoc request :twitter-oauth oauth))
        (let [request-token (twitter-request-token)
              auth-url (callback-uri request-token)]
          (session-put! :request-token request-token)
          (-> (json-response {:authUrl auth-url})
            (response/status 401))))
      (handler request))))

(comment ; this implementation doesn't work at the moment - problems with symbols in nested routes.
  (defn wrap-oauth-not-working
    "middelware wrapper for twitter oauth credential check
     only checks requests starting with '/auth'
     if signed in, stores credentials in :twitter-oauth and continues
     if not signed in, returns a 401 error, including the callback URL needed to sign in,
     so clients can prompt the user to start the OAuth dance!"
    [handler]
    (routes
      (ANY "/auth/*" request
        (if-let [oauth (session-get :twitter-oauth)]
          (handler (assoc request :twitter-oauth oauth))
          (let [request-token (twitter-request-token)
                auth-url (callback-uri request-token)]
            (session-put! :request-token request-token)
            (-> (json-response {:authUrl auth-url})
              (response/status 401)))))
      handler)))

(def app
  (-> (handler/site main-routes)
    (wrap-oauth)
    (wrap-stateful-session)
    (wrap-json-params)))

(defn -main []
  (let [port (Integer/parseInt (get (System/getenv) "PORT" "8080"))]
    (config)  ; force memoization of config variables - fail early!
    (run-jetty app {:port port})))