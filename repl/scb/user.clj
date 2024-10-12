(ns scb.user
  (:refer-clojure :exclude [test])
  (:require
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [clojure.test]
   [gui.diff :refer [with-gui-diff]]
   [scb
    [main :as main]
    [utils :as utils]
    [http :as http]
    [core :as core]])
  (:import
   [java.util.concurrent LinkedBlockingQueue]))

(def ns-list [:core :utils
              :tags
              :wowi :github])

(defn test
  [& [ns-kw fn-kw]]
  (core/stop)
  ;;(clojure.tools.namespace.repl/refresh) ;; reloads all namespaces, including strongbox.whatever-test ones
  (utils/instrument true) ;; always test with spec checking ON

  (try
    ;; note! remember to update `cloverage.clj` with any new bindings
    (with-redefs [core/testing? true
                  http/delay-between-requests 0
                  ;;http/*default-pause* 1 ;; ms
                  ;;http/*default-attempts* 1
                  ;; don't pause while testing. nothing should depend on that pause happening.
                  ;; note! this is different to `joblib/tick-delay` not delaying when `joblib/*tick*` is unbound.
                  ;; tests still bind `joblib/*tick*` and run things in parallel.
                  ;;joblib/tick-delay joblib/*tick*
                  ;;main/spec? true
                  ;;cli/install-update-these-in-parallel cli/install-update-these-serially
                  ;;core/check-for-updates core/check-for-updates-serially
                  ;; for testing purposes, no addon host is disabled
                  ;;catalogue/host-disabled? (constantly false)
                  ]
      ;;(core/reset-logging!)

      (core/init-logging)

      (timbre/with-merged-config {:min-level :debug
                                  :appenders {:println {:min-level :debug}
                                              :spit {:enabled? false}}}
        (if ns-kw
          (if-not (some #{ns-kw} ns-list)
            (error "unknown test file:" ns-kw)
            (with-gui-diff
              (if fn-kw
                ;; `test-vars` will run the test but not give feedback if test passes OR test not found
                ;; slightly better than nothing
                (clojure.test/test-vars [(resolve (symbol (str "scb." (name ns-kw) "-test") (name fn-kw)))])
                (clojure.test/run-all-tests (re-pattern (str "scb." (name ns-kw) "-test"))))))
          (clojure.test/run-all-tests #"scb\..*-test"))))
    (finally
      ;; use case: we run the tests from the repl and afterwards we call `restart` to start the app.
      ;; `stop` inside `restart` will be outside of `with-redefs` and still have logging `:min-level` set to `:debug`
      ;; it will dump a file and yadda yadda.
      ;;(core/reset-logging!)
      nil)))

(comment "this is probably the 'proper' way to do it, but it means I can't target specific tests/ns"
         (defn -test
           []
           (try
             (with-redefs [;;core/testing? true
                  ;;http/*default-pause* 1 ;; ms
                  ;;http/*default-attempts* 1
                           ]
               (with-gui-diff
                 (clojure.test/run-all-tests #"scb\..*-test")))
             (finally
               nil)))

         (defn test
           []
           (core/stop)
           (clojure.tools.namespace.repl/refresh :after 'scb.user/-test)) ;; reloads all namespaces, including strongbox.whatever-test ones
         )

;; ---

#_(defn wowi-html-landing
    []
    (clojure.pprint/pprint (->> "test/fixtures/wowinterface--landing.html" fs/absolute fs/normalized str wowi/to-html wowi/parse-category-group-page)))

#_(defn wowi-html-listing-page
    []
    (let [html-snippet (->> "test/fixtures/wowinterface--listing.html" fs/absolute fs/normalized str slurp)
          downloaded-item {:url "https://www.wowinterface.com/downloads/index.php?cid=100&sb=dec_date&so=desc&pt=f&page=1"
                           :label "The Burning Crusade Classic"
                           :response {:headers {}
                                      :body html-snippet}}]
      (wowi/parse-category-listing downloaded-item)))

#_(defn wowi-html-addon-detail
    []
    (clojure.pprint/pprint
     (->> "test/fixtures/wowinterface--addon-detail--multiple-downloads--no-tabber.html" fs/absolute fs/normalized str wowi/to-html wowi/parse-addon-detail-page)))

#_(defn wowi-html-addon-detail-2
    []
    (clojure.pprint/pprint
     (wowi/parse-addon-detail-page
      {:url "https://www.wowinterface.com/downloads/info24155"
       :response {:body (->> "test/fixtures/wowinterface--addon-detail--multiple-downloads--tabber.html"
                             fs/absolute fs/normalized str slurp)}})))

#_(defn wowi-api-addon-list
    []
    (->> (wowi/parse-api-file-list {:url wowi/api-file-list
                                    :response (http/download wowi/api-file-list {})})
         :parsed
         (take 100)))

#_(defn wowi-api-addon-detail
    []
    (let [resp (wowi/parse-api-addon-detail {:url "https://api.mmoui.com/v4/game/WOW/filedetails/5119.json"
                                             :response (http/download "https://api.mmoui.com/v4/game/WOW/filedetails/5119.json" {})})]
      (clojure.pprint/pprint resp)
      (println "-----")
      resp))

;; ---

(def stop core/stop)
(def start core/start)
(def restart core/restart)

(defn status
  []
  (if-not (core/started?)
    (warn "start app first: `(core/start)`")
    (run! (fn [q-kw]
            (println (format "%s items in %s" (.size ^LinkedBlockingQueue (core/get-state q-kw)) q-kw)))
          core/queue-list)))

#_(defn to-addon-summary
    [source source-id]
    (core/to-addon-summary (core/find-read-addon-data source source-id)))

(defn parse-url-content
  "downloads and parses the given URL, bypasses queues.
  allows us to grab the exception object with `*e`"
  [url]
  (let [resp {:url url
              :response (http/download url {})}]
    (core/parse-content resp)))
