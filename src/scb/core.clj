(ns scb.core
  (:require
   [scb
    [specs :as sp]
    [utils :as utils]]
   [clojure.java.io]
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

;;---

(defn error*
  [msg & {:keys [payload exc]}]
  (if payload
    (timbre/error ^:meta {:payload payload} exc msg)
    (timbre/error exc msg)))

(defn state-dir
  []
  ;;(utils/temp-dir)
  (str (fs/file fs/*cwd* "state")) ;; "/path/to/strongbox-catalogue-builder/state"
  )

;; --- state wrangling

(def -state-template
  {:download-queue nil
   :downloaded-content-queue nil
   :parsed-content-queue nil
   :cleanup []
   :catalogue {}

   :recent-urls #{}
   })

(def queue-list [:download-queue :downloaded-content-queue :parsed-content-queue])
(def state nil)

(defn paths
  [& path]
  (let [state-path (state-dir)
        path-map {:state-path state-path ;; /path/to/state
                  :state-file-path (->> "state.edn" (fs/file state-path) str) ;; /path/to/state/state.edn
                  :state-http-cache-path (->> "http" (fs/file state-path) str) ;; /path/to/state/http
                  :state-log-file-path (->> "log" (fs/file state-path) str) ;; /path/to/state/log
                  }]
    (if path
      (get-in path-map path)
      path-map)))

(defn started?
  []
  (not (nil? state)))

(defn get-state
  [& path]
  (if (nil? state)
    (Exception. (format "app not started, failed to fetch path: %s" path))
    (get-in @state path)))

;; --- queue wrangling

(defn put-item
  [q i]
  (when i
    (.add ^LinkedBlockingQueue q i)))

(defn put-all
  [q l]
  (when-not (empty? l)
    (run! (partial put-item q) l)))

(defn take-item
  "removes and returns item from the given queue.
  returns a pair of [item, return-fn]
  call `return-fn` to return the given item to the origin queue."
  [q]
  (let [item (.take ^LinkedBlockingQueue q)]
    [item #(do (debug "restoring item")
               (put-item q item))]))

(defn -empty-queue?
  [q]
  (.isEmpty ^LinkedBlockingQueue q))

(defn empty-queue?
  [q-kw]
  (-empty-queue? (get-state q-kw)))

(defn new-queue
  [& [c]]
  (if c
    (LinkedBlockingQueue. c)
    (LinkedBlockingQueue.)))

(defn drain-queue
  [q]
  (let [c (Vector.)]
    (.drainTo ^LinkedBlockingQueue q c)
    (vec c)))

;; --- utils

(defn add-cleanup
  [f]
  (swap! state update :cleanup conj f))

;; --- http

(defn -download
  "downloads given `url`.
  returns a pair of `[http-resp, error]`.
  `http-resp` is the raw http response but may be `nil` if an exception occurs.
  `error` is any exception thrown while attempting to download."
  [url & [request-opts]]
  (let [user-agent "strongbox-catalogue-builder 0.0.1 (https://github.com/ogri-la/strongbox-catalogue-builder)"
        default-request-opts {:headers {"User-Agent" user-agent}
                              :cache-root (paths :state-http-cache-path)}
        request-opts (merge default-request-opts request-opts)]
    (try
      (http/download url request-opts)

      ;; todo: re-raise recoverable errors so they go back on the queue

      (catch Exception exc
        (error* (format "failed to download url '%s': %s" url (.getMessage exc))
                :exc exc
                :payload {:url url, :opts request-opts})))))

;; --- downloading

(defn-spec download nil?
  [url (s/or :url-string ::sp/url, :url-map ::sp/url-map)]
  (let [[url label] (if (map? url) (utils/select-vals url [:url :label]) [url nil])]
    (when-let [response (-download url)]
      (put-item (get-state :downloaded-content-queue) (cond-> {:url url :response response}
                                                        label (assoc :label label)))))
  nil)

(defn download-worker
  "pulls urls from the `:download-queue` and calls `download` with a connection pool.
  results are added to the `:downloaded-content-queue`.
  errors are added to the `:error-queue`.
  if the queue is a blocking queue, the thread will block until a new url arrives, this worker should be run in a separate thread."
  []
  (client/with-connection-pool {:timeout 5 :threads 4 :insecure? false :default-per-route 10}
    (while true
      (let [[url _] (take-item (get-state :download-queue))]
        (try
          (download url)

          ;; todo: recoverable exceptions like throttling or timeouts should use `restore-item`

          (catch Exception exc
            (error* (str "unhandled exception downloading url: " url) :exc exc :payload url)))))))

;; --- parsing

(defn recent-url
  [url-map]
  (let [url (cond
              (string? url-map) url-map
              (map? url-map) (:url url-map))
        ]
    (if (contains? (get-state :recent-urls) url)
      nil
      (do (swap! state update :recent-urls conj url)
          url-map))))

(defmulti parse-content
  "figure out what we downloaded and dispatch to the best parsing function"
  (fn [downloaded-item]
    (-> downloaded-item :url java.net.URL. .getHost)))

(defn parser-worker
  "takes items from `downloaded-content-queue`, parses it and adds the items in the `:result/map` to the proper queues."
  []
  (while true
    (let [[item _] (take-item (get-state :downloaded-content-queue))]
      (try
        (let [{:keys [download parsed error]} (parse-content item)]
          (put-all (get-state :download-queue) (mapv recent-url download))
          (put-all (get-state :parsed-content-queue) parsed)
          (put-all (get-state :error-queue) error))

        ;; under what conditions would we attempt to parse the item again?

        (catch Exception exc
          (error* "unhandled exception parsing content" :exc exc :payload item))))))

;; --- coercing to catalogue

;; I guess this could be the consumer of `parsed-content-queue` items, building up a catalogue in real-time ...
;; ... we had problems keeping it all in memory previously. We'd prune as we go along but ...
;; I feel slurping from the disk as neccessary is more robust right now.
(defmulti to-catalogue-addon
  "coerces addon data from a file.
  all addon data in files is guaranteed to have at least a `source` and `source-id`."
  (comp keyword :source))

;; --- retrieving

(defn -addon-data-value-fn
  "used to transform addon *values* as the json is read."
  [key val]
  (case key
    :game-track-list (mapv keyword val)
    :game-track (keyword val) ;; part of release lists
    :tag-list (mapv keyword val)
    :source (keyword val)
    :wowi/category-list (set val)

    val))

(defn-spec read-addon-data (s/or :ok :addon/part, :error nil?)
  "reads the catalogue of addon data at the given `catalogue-path`.
  supports reading legacy catalogues by dispatching on the `[:spec :version]` number."
  [path ::sp/file]
  (let [key-fn keyword
        value-fn -addon-data-value-fn ;; defined 'outside' so it can reference itself
        opts {:key-fn key-fn :value-fn value-fn}]
    (utils/json-slurp path opts)))

;; --- storing

(defn state-path
  "returns a path like `/path/to/state/wowinterface--12345.json`"
  [source source-id]
  (str (fs/file (paths :state-path) (format "%s--%s.json" (name source) source-id))))

(defn write-content-worker
  "takes parsed content and writes to the fs"
  []
  (while true
    (let [[item _] (take-item (get-state :parsed-content-queue))
          output-path (state-path (:source item) (:source-id item))
          existing-item (read-addon-data output-path)]
      (try
        (-> existing-item
            (merge item) ;; TODO! deepmerge, sets should be merged, maps merged, lists replaced
            utils/order-map
            (utils/json-spit output-path))
        (catch Exception exc
          (error* "unhandled exception storing content" :exc exc :payload item))))))

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
    (let [old-state (read-string (slurp path))
          lists-to-queues (into {}
                                (mapv (fn [qn]
                                        [qn (new-queue (get old-state qn))]) queue-list))]
      (swap! state merge lists-to-queues))
    nil))

(defn run-worker
  [worker-fn]
  (let [f (future
            (try
              (info "starting worker" worker-fn)
              (worker-fn)
              (catch java.lang.InterruptedException ie
                (warn "interrupted"))
              (catch Exception e
                (error e (format "uncaught exception in worker '%s': %s" worker-fn e)))
              (finally
                (debug "worker is done:" worker-fn))))]
    (add-cleanup #(future-cancel f))
    nil))

(defn run-many-workers
  [worker-fn num-instances]
  (if testing?
    (do (warn (format "running 1 worker during test, not %s: %s" num-instances worker-fn))
        (run-worker worker-fn))
    (dotimes [_ num-instances]
      (run-worker worker-fn))))

(defn status
  []
  (if-not (started?)
    (warn "start app first: `(core/start)`")
    (run! (fn [q-kw]
            (println (format "%s items in %s" (.size ^LinkedBlockingQueue (get-state q-kw)) q-kw)))
          queue-list)))

;; --- init

(def colour-log-map
  {:debug :blue
   :info nil
   :warn :yellow
   :error :red
   :fatal :purple
   :report :blue})

(defn custom-println-appender
  "removes the hostname from the output format string"
  [data]
  (let [{:keys [?err timestamp_ msg_ level]} data
        level-colour (colour-log-map level)
        pattern "%s [%s] %s"
        msg (force msg_)]

    (when (and ?err testing?)
      (println (timbre/stacktrace ?err)))

    (when-not (empty? msg)
      ;; looks like: "11:17:57.009 [info] checking for updates"
      (println
       (timbre/color-str level-colour
                         (format
                          pattern
                          (force timestamp_)
                          (name level)
                          msg))))))

;; https://github.com/ptaoussanis/timbre/blob/7bb3d648e1a49cf835233785fe2c07e43b2395da/src/taoensso/timbre/appenders/core.cljc#L90
(defn custom-spit-appender
  "Returns a simple `spit` file appender for Clojure."
  [fname]
  (let [lock (Object.)]
    (fn self [{:keys [vargs output_ ?meta]}]
      (let [output (force output_) ; Must deref outside lock, Ref. #330
            stacktrace-output (when-let [exc (first vargs)]
                                (when (instance? Exception exc)
                                  (timbre/stacktrace exc)))
            payload-output (if-let [p (some-> ?meta :payload)]
                             (str "payload:\n" (utils/pprint p))
                             (str "payload: nil"))]
        (locking lock
          (try
            (with-open [^java.io.BufferedWriter w (clojure.java.io/writer fname :append true)]
              (.write w ^String output)
              (.newLine w)
              (when stacktrace-output
                (.write w ^String stacktrace-output)
                (.newLine w))
              (.write w ^String payload-output)
              (.newLine w))

            (catch java.io.IOException e
              (throw e) ; Unexpected error
              )))))))

(defn init-logging
  []
  (timbre/merge-config! timbre/default-config) ;; reset
  (let [default-logging-config
        {:min-level :info

         :timestamp-opts {;;:pattern "yyyy-MM-dd HH:mm:ss.SSS"
                          :pattern "HH:mm:ss.SSS"
                          ;; default is `:utc`, `nil` sets tz to current locale.
                          :timezone nil}

         :appenders {:spit {:enabled? true
                            :async? false
                            :output-fn :inherit
                            :min-level :error
                            :fn (custom-spit-appender (paths :state-log-file-path))}
                     :println {:enabled? true
                               :async? false
                               :output-fn :inherit
                               :fn custom-println-appender}}}]

    (timbre/merge-config! default-logging-config)))

(defn init-state-dirs
  []
  (run! fs/mkdirs [(paths :state-path)
                   (paths :state-http-cache-path)]))

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
  (utils/instrument true)
  (alter-var-root #'state (constantly (-start)))
  (init-logging)
  (init-state-dirs)
  (thaw-state (paths :state-file-path))
  (run-worker download-worker)
  (run-many-workers parser-worker 5)
  (run-many-workers write-content-worker 5)
  nil)

(defn cleanup
  []
  (doseq [cleanup-fn (get-state :cleanup)]
    (debug "cleaned up" cleanup-fn (cleanup-fn))))

(defn stop
  []
  (info "stopping")
  (when-not (nil? state)
    (cleanup)
    (freeze-state (paths :state-file-path)))
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
