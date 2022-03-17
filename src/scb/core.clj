(ns scb.core
  (:require
   [scb
    [specs :as sp]
    ;;[wowi :as wowi]
    [utils :as utils]]
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   [scb.http :as http]
   [clj-http.client :as client]
   [me.raynes.fs :as fs]
   [clojure.tools.namespace.repl :as tns :refer [refresh]]
   [taoensso.timbre :as timbre :refer [debug info warn error spy]])
  (:import
   [java.util Vector]
   [java.lang InterruptedException]
   [java.util.concurrent LinkedBlockingQueue]))

(def testing? false)

;; --- state wrangling

(def -state-template
  {:download-queue nil
   :downloaded-content-queue nil
   :parsed-content-queue nil
   :error-queue nil
   :cleanup []})

(def queue-list [:download-queue :downloaded-content-queue :parsed-content-queue :error-queue])
(def state nil)

(def ^:dynamic *state-path* (-> (utils/temp-dir) (fs/file "scb") str)) ;; /path/to/state
(def state-file-path (->> "state.edn" (fs/file *state-path*) str)) ;; /path/to/state/state.edn
(def state-http-cache-path (->> "http" (fs/file *state-path*) str)) ;; /path/to/state/http

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
    [item #(do (debug "restoring item")
               (put-item q item))]))

(defn new-queue
  [& [c]]
  (if c
    (LinkedBlockingQueue. c)
    (LinkedBlockingQueue.)))

(defn drain-queue
  [q]
  (let [c (Vector.)]
    (.drainTo q c)
    (vec c)))

;; --- utils

(defn add-cleanup
  [f]
  (swap! state update :cleanup conj f))

(defn err
  [x & {:keys [payload exc]}]
  (cond-> {:error x}
    (some? payload) (merge {:payload payload})
    (some? exc) (merge {:exc exc})))

(defn err?
  [x]
  (and (map? x)
       (contains? x :error)))

(defn put-err
  [e]
  (put-item (get-state :error-queue) e))

(defn put-err-exc
  [exc & {:keys [payload]}]
  (put-err (err (.getMessage exc) :payload payload :exc exc)))

;; --- http

(defn -download
  "downloads given `url`.
  returns a pair of `[http-resp, error]`.
  `http-resp` is the raw http response but may be `nil` if an exception occurs.
  `error` is any exception thrown while attempting to download."
  [url & [request-opts]]
  (try
    (let [user-agent "strongbox-catalogue-builder 0.0.1 (https://github.com/ogri-la/strongbox-catalogue-builder)"
          default-request-opts {:headers {"User-Agent" user-agent}
                                :cache-root state-http-cache-path}
          request-opts (merge default-request-opts request-opts)
          response (http/download url request-opts)]
      [response, nil])
    (catch Exception e
      [nil e])))

;; --- downloading

(defn-spec download nil?
  [url (s/or :url-string ::sp/url, :url-map ::sp/url-map)]
  (let [[url label] (if (map? url) (utils/select-vals map [:url :label]) [url nil])
        [response exc] (-download url)]
    (if response
      (put-item (get-state :downloaded-content-queue) {:url url :label label :response response})
      (put-err (err (format "failed to download url '%s': %s" url (.getMessage exc)) :payload response :exc exc)))))

(defn download-worker
  "pulls urls from the `:download-queue` and calls `download` with a connection pool.
  results are added to the `:downloaded-content-queue`.
  errors are added to the `:error-queue`.
  if the queue is a blocking queue, the thread will block until a new url arrives, this worker should be run in a separate thread."
  []
  (client/with-connection-pool {:timeout 5 :threads 4 :insecure? false :default-per-route 10}
    (while true
      (let [[url restore-item] (take-item (get-state :download-queue))]
        (try
          (download url)

          ;; todo: recoverable exceptions like throttling or timeouts should use `restore-item`

          (catch Exception e
            (put-err-exc e :payload url)))))))

;; --- parsing

(defmulti parse-content
  "figure out what we downloaded and dispatch to the best parsing function"
  (fn [downloaded-item]
    (-> downloaded-item :url java.net.URL. .getHost)))

(defn parse
  [thing]
  (let [[parsed-content exc] (parse-content thing)]
    (if parsed-content
      (put-item (get-state :parsed-content-queue) parsed-content)
      (put-err (err "failed to parse item" :payload thing :exc exc)))))

(defn parser-worker
  []
  (while true
    (let [[item restore-item] (take-item (get-state :downloaded-content-queue))]
      (try
        (parse item)

        ;; under what conditions would we attempt to parse the item again?

        (catch Exception e
          (put-err-exc e :payload item))))))

;; ---

(defn drain-queues
  "drains all queues from a map of BlockingQueues into a map of vectors"
  []
  (info "draining queues")
  (into {} (mapv (fn [qn]
                   [qn (drain-queue (get-state qn))]) queue-list)))

(defn freeze-state
  [path]
  (info "freezing state:" path)
  (let [queue-state (drain-queues)]
    (spit path queue-state)))

(defn thaw-state
  [path]
  (when (fs/exists? path)
    (info "thawing state:" path)
    (let [old-state (slurp path)
          lists-to-queues (into {}
                                (mapv (fn [qn]
                                        [qn (new-queue (get old-state qn))]) queue-list))

          ;; do we want to restore errors?
          ;; freezing them is helpful to inspect errors later but what do we do with them while the app is running?
          ;; print to screen and discard? print to screen and keep?
          lists-to-queues (assoc lists-to-queues :error-queue (new-queue))]
      (swap! state merge lists-to-queues))
    nil))

(defn run-worker
  [worker-fn]
  (let [f (future
            (try
              (worker-fn)
              (catch java.lang.InterruptedException ie
                (warn "interrupted"))
              (catch Exception e
                (error e (format "uncaught exception in worker '%s': %s" worker-fn e)))
              (finally
                (info "worker is done"))))]
    (add-cleanup #(future-cancel f))
    nil))

;; --- init

(defn init-state-dirs
  []
  (run! fs/mkdirs [*state-path* state-http-cache-path]))

(defn -start
  []
  (let [new-state {:download-queue (new-queue)
                   :downloaded-content-queue (new-queue)
                   :parsed-content-queue (new-queue)
                   :error-queue (new-queue)}]
    (atom (merge -state-template new-state))))

(defn start
  []
  (info "starting")
  (alter-var-root #'state (constantly (-start)))
  (init-state-dirs)
  (thaw-state state-file-path)
  (run-worker download-worker)
  (run-worker parser-worker)
  nil)

(defn stop
  []
  (info "stopping")
  (when-not (nil? state)
    (doseq [cleanup-fn (get-state :cleanup)]
      (debug "cleaned up" cleanup-fn (cleanup-fn)))
    (freeze-state state-file-path))
  nil)

(defn restart
  []
  (info "restarting")
  (stop)
  (tns/refresh :after 'scb.core/start))

;; --- bootstrap

(defn -main
  [& args]
  (start))


