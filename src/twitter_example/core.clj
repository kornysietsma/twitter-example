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

(defn make-consumer []
  (let [credentials (:twitterCredentials config-data)
        {:keys [key secret]} credentials]
    (println "in make-consumer - hopefully only called once!")
    (oauth/make-consumer key secret
      "https://api.twitter.com/oauth/request_token"
      "https://api.twitter.com/oauth/access_token"
      "https://api.twitter.com/oauth/authorize"
      :hmac-sha1)
    )
  )

(def consumer (atom false))

(defn local-consumer []
  (do
    (println "local consumer - cons is" @consumer)
    (or @consumer
      (reset! consumer (make-consumer)))))

(println "first" (local-consumer))
(println "second" (local-consumer))

(def our-oauth-url (str "http://" (:host config-data) ":" (:port config-data) "/twitter_oauth_response"))

(def request-token (oauth/request-token (local-consumer) our-oauth-url))

(println "request-token:" request-token)

(def callback-uri (oauth/user-approval-uri (local-consumer)
  (:oauth_token request-token)))
(println "raw uri: " (oauth/user-approval-uri (local-consumer)
  request-token))
(println "params:" (:authorize-uri (local-consumer)) " and " {:oauth_token request-token})
(println "callback: " callback-uri)

(defn access-token-response [verifier] (oauth/access-token (local-consumer)
  request-token
  verifier))


(defn json-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string data)})

(defn filter-tweet [tweet]
  {:screen-name (get-in tweet [:user :screen_name]) :tweet (:text tweet)}
  )

(defroutes main-routes
  (GET "/" [] (resource "public/index.html"))
  (GET "/twitter_oauth_response" [oauth_token oauth_verifier]
    (let [resp (access-token-response oauth_verifier)]
      (println "token:" oauth_token)
      (println "ver" oauth_verifier)
      (println "response:" resp)
      (println "storing response in session!")
      (session-put! :twitter-oauth resp)
      (redirect "/")
      )
    )
  (GET "/auth/tweets.json" [:as resp]
    (let [oauth (:twitter-oauth resp)]
      (println "can has oauth:" oauth)
      (twitter/with-oauth (local-consumer) (:oauth_token oauth) (:oauth_token_secret oauth)
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
