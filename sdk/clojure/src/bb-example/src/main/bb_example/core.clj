(ns bb-example.core
  (:require
    [org.httpkit.server                               :as hk]
    [ruuter.core                                      :as ruuter]
    [starfederation.datastar.clojure.api              :as d*]
    [starfederation.datastar.clojure.adapter.http-kit :as hk-gen :refer [->sse-response on-open on-close]]

    [starfederation.datastar.clojure.api-schemas]
    [starfederation.datastar.clojure.adapter.http-kit-schemas]))


;; Tiny setup for that allows broadcasting events to several curl processes

(defonce !conns (atom #{}))

(defn long-connection [req]
  (->sse-response req
    {on-open
     (fn [sse]
       (swap! !conns conj sse)
       (d*/console-log! sse "'connected'"))
     on-close
     (fn on-close [sse status-code]
       (swap! !conns disj sse)
       (println "Connection closed status: " status-code)
       (println "remove connection from pool"))}))


(def routes
  [#_{:path "/"
      :method :get
      :reponse {:status 200
                :body "Hello ruuter"}}

   {:path"/persistent"
    :method :get
    :response long-connection}])



(defn handler [req]
  (ruuter/route routes req))

(defn broadcast-number! [n]
  (doseq [conn @!conns]
    (try
      (d*/console-log! conn (str "n: " n))
      (catch Exception e
        (println "Error: " e)))))


(defonce !server (atom nil))

(defn stop! []
  (when-let [server @!server]
    (hk/server-stop! server)
    (reset! !server nil)))

(defn start! []
  (stop!)
  (reset! !server (hk/run-server #'handler {:port 8080
                                            :legacy-return-value? false})))

;; open several clients:
;; curl -vv http://localhost:8080/persistent
(comment
  (start!)
  !conns
  (some-> !conns deref first d*/close-sse!)
  (broadcast-number! (rand-int 25)))


