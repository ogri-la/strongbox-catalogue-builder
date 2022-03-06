(ns scb.core
  (:require
   [clj-http.client :as http]
   [clojure.tools.namespace.repl :as tns :refer [refresh]])
  (:import
   [java.util.concurrent LinkedBlockingQueue]
   ))

;; --- state wrangling

(def -state-template
  {:download-queue nil
   :downloaded-content-queue nil
   :error-queue nil
   :cleanup []
   
   })

(def state nil)

(defn get-state
  [& path]
  (if (nil? state)
    (Exception. (format "app not started, failed to fetch path: %s" path))
    (get-in @state path)))

;; --- queue wrangling

(defn put-item
  [q i]
  (.add q i))

(defn take-item
  "removes and returns item from the given queue.
  returns a pair of [item, return-fn]
  call `return-fn` to return the given item to the origin queue."
  [q]
  (let [item (.take q)]
    [item #(put-item q item)]))

(defn new-queue
  []
  (LinkedBlockingQueue.))

(defn add-cleanup
  [f]
  (swap! state update :cleanup conj f))

;; --- utils

(defn err
  [x & {:keys [payload]}]
  (cond-> {:error x}
    (some? payload) (merge {:payload payload})))

(defn err?
  [x]
  (and (map? x)
       (contains? x :error)))

(defn put-err
  [e]
  (put-item (get-state :error-queue) e))

(defn download
  "downloads url and deserialises to given serialisation type.
  returns a pair of `[http-resp, response]`.
  `http-resp` is the raw http response but may be `nil` if an exception occurs.
  `response` is the deserialised output or an error struct."
  [url serialisation]
  (try
    (let [response (http/get url {:as serialisation})]
      [response, nil])
    (catch Exception e
      [nil (err e)])))

;; ---

(defn download-worker
  "pulls urls from the `:download-queue` and calls `download` with a connection pool.
  results are added to the `:downloaded-content-queue`.
  errors are added to the `:error-queue`.
  if the queue is a blocking queue, the thread will block until a new url arrives, this worker should be run in a separate thread."
  []
  (http/with-connection-pool {:timeout 5 :threads 4 :insecure? false :default-per-route 10}
    (while true
      (let [[item restore-item] (take-item (get-state :download-queue))]
        (try
          (apply download item)
          (catch Exception e
            (put-err (err e :payload item))
            (restore-item)))))))

;; ---

(defn -start
  []
  (let [new-state {:download-queue (new-queue)
                   :downloaded-content-queue (new-queue)
                   :error-queue (new-queue)}]
    (atom (merge -state-template new-state))))

(defn start
  []
  (alter-var-root #'state (constantly (-start)))
  (let [downloader (future
                     (try 
                       (download-worker)
                       (catch Exception e
                         (println "error" e))))
        ]
    (add-cleanup #(future-cancel downloader)))

  ;; todo: restore state 
        
  nil)

(defn stop
  []
  (when-not (nil? state)
    (doseq [cleanup-fn (get-state :cleanup)]
      (cleanup-fn)))

  ;; todo: freeze state

  nil)

(defn restart
  []
  (stop)
  (tns/refresh :after 'scb.core/start))

(defn -main
  [& args]
  (start))
  

