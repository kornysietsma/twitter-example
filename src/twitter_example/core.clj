(ns twitter-example.core
  (:use compojure.core
    [compojure.response :only [resource]]
    ring.middleware.json-params
    ring.middleware.stacktrace
    ring.middleware.session
    sandbar.stateful-session
    [ring.util.response :only [redirect]]
    )
  (:require [compojure.route :as route]
    [compojure.handler :as handler]
    [clj-json.core :as json]
    [oauth.client :as oauth]
    [clojure.java.io :as io]
    twitter
    ))

(defn file-as-json [path]
  (first (json/parsed-seq (io/reader (io/resource path)) true))
  )

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

; request token must be fetched from Twitter - currently at startup, good candidate for doing lazily
(def request-token
  (do
    (println "requesting request token from Twitter")
    (oauth/request-token consumer oauth-response-callback)))

; callback url is built using the retrieved request token
(def callback-uri
  (oauth/user-approval-uri consumer
    (:oauth_token request-token)))

; access token for a user can be found once they've danced the OAuth dance, and we have a verifier
(defn access-token-response [verifier] (oauth/access-token consumer
  request-token
  verifier))

(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

(defn filter-tweet [tweet]
  "filter out just the parts of the tweet wanted by the UI"
  {:screen-name (get-in tweet [:user :screen_name]) :tweet (:text tweet)})

(defroutes main-routes
  (GET "/" [] (resource "public/index.html"))
  (GET oauth-response-path [oauth_token oauth_verifier]
    (let [resp (access-token-response oauth_verifier)]
      (session-put! :twitter-oauth resp)
      (redirect "/")
      )
    )
  (GET "/auth/tweets.json" [:as resp]
    (let [oauth (:twitter-oauth resp)]
      (twitter/with-oauth consumer (:oauth_token oauth) (:oauth_token_secret oauth)
        (json-response {"auth" true "name" (:screen_name oauth) "tweets" (map filter-tweet (twitter/home-timeline))})
        )))
  (GET "/auth/status.json" [:as resp]
    (json-response
      (let [oauth (:twitter-oauth resp)]
        {"name" (:screen_name oauth)}
        )
      ))
  (route/resources "/")
  (route/not-found "Page not found"))

(defn with-oauth-check [handler]
  "middelware wrapper for twitter oauth credential check
   only checks requests starting with '/auth'
   if signed in, stores credentials in :twitter-oauth and continues
   if not signed in, returns a 401 error, including the callback URL needed to sign in,
   so clients can prompt the user to start the OAuth dance!"
  (fn [req]
    (if (re-matches #"/auth/.*" (:uri req))
      (if-let [oauth (session-get :twitter-oauth)]
        (handler (assoc req :twitter-oauth oauth))
        (json-response {:message "twitter not authorized" :authUrl callback-uri} 401)
        )
      (handler req)
      ))
  )

(def app
  (-> (handler/site main-routes)
    (with-oauth-check)
    (wrap-stateful-session)
    (wrap-json-params)
    (wrap-stacktrace)))
