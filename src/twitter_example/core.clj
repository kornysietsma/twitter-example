(ns twitter-example.core
  (:use compojure.core
    [compojure.response :only [resource]]
    ring.middleware.json-params
    ring.middleware.stacktrace
    ring.middleware.session
    sandbar.stateful-session)
  (:require [compojure.route :as route]
    [compojure.handler :as handler]
    [clj-json.core :as json]
    [oauth.client :as oauth]
    [clojure.java.io :as io]
    [ring.util.response :as response]
    twitter))

(defn file-as-json [path]
  (first (json/parsed-seq (io/reader (io/resource path)) true)))

(def config-data (file-as-json "config/config.json"))

; consumer is constructed locally - build at start time
(def consumer
  (let [credentials (:twitterCredentials config-data)
        {:keys [key secret]} credentials]
    (oauth/make-consumer key secret
      "https://api.twitter.com/oauth/request_token"
      "https://api.twitter.com/oauth/access_token"
      "https://api.twitter.com/oauth/authorize"
      :hmac-sha1)))

(def oauth-response-path "/twitter_oauth_response")

(def oauth-response-callback (str "http://" (:host config-data) ":" (:port config-data) oauth-response-path))

(defn twitter-request-token
  "fetch request token from twitter for a new user oauth dance"
  []
  (do
    (println "requesting request token from Twitter")
    (oauth/request-token consumer oauth-response-callback)))

(defn callback-uri
  "generate callback uri for a given request"
  [request-token]
  (oauth/user-approval-uri consumer
    (:oauth_token request-token)))

(defn access-token-response
  "get access token from twitter once twitter has redirected back to the app"
  [request-token verifier]
  (oauth/access-token consumer
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
  {:screen-name (get-in tweet [:user :screen_name]) :tweet (:text tweet)})

(defroutes main-routes
  (GET "/" [] (resource "public/index.html"))
  (GET oauth-response-path [oauth_token oauth_verifier]
    (let [request-token (session-get :request-token)
          resp (access-token-response request-token oauth_verifier)]
      (session-put! :twitter-oauth resp)
      (response/redirect "/")))
  (GET "/auth/tweets.json" {oauth :twitter-oauth}
    (twitter/with-oauth consumer (:oauth_token oauth) (:oauth_token_secret oauth)
      (json-response {"auth" true "name" (:screen_name oauth) "tweets" (map filtered-tweet (twitter/home-timeline))})))
  (GET "/auth/status.json" {oauth :twitter-oauth}
    (json-response
      {"name" (:screen_name oauth)}))
  (route/resources "/")
  (route/not-found "Page not found"))

(defn wrap-oauth
  "middelware wrapper for twitter oauth credential check
   only checks requests starting with '/auth'
   if signed in, stores credentials in :twitter-oauth and continues
   if not signed in, returns a 401 error, including the callback URL needed to sign in,
   so clients can prompt the user to start the OAuth dance!"
  [handler]
; suggested routing - not working yet!
;  (routes
;    (ANY "/auth/*" request
;      (if-let [oauth (session-get :twitter-oauth)]
;        (handler (assoc request :twitter-oauth oauth))
;        (let [request-token (twitter-request-token)
;              auth-url (callback-uri request-token)]
;          (session-put! :request-token request-token)
;          (-> (json-response {:message "twitter not authorized" :authUrl auth-url})
;            (status 401)))))
;    handler))
  (fn [request]
    (if (re-matches #"/auth/.*" (:uri request))
      (if-let [oauth (session-get :twitter-oauth)]
        (handler (assoc request :twitter-oauth oauth))
        (let [request-token (twitter-request-token)
              auth-url (callback-uri request-token)]
          (session-put! :request-token request-token)
          (-> (json-response {:message "twitter not authorized" :authUrl auth-url})
            (response/status 401))))
      (handler request))))

(def app
  (-> (handler/site main-routes)
    (wrap-oauth)
    (wrap-stateful-session)
    (wrap-json-params)))
