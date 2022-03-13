(ns scb.user
  (:require
   [clojure.test]
   [gui.diff :refer [with-gui-diff]]
   [scb.core :as core]))

(comment "user interface to catalogue builder")

(def ns-list [:main :core])

(defn test
  [& [ns-kw fn-kw]]
  (core/stop)
  ;;(clojure.tools.namespace.repl/refresh) ;; reloads all namespaces, including strongbox.whatever-test ones
  ;;(utils/instrument true) ;; always test with spec checking ON

  (try
    ;; note! remember to update `cloverage.clj` with any new bindings
    (with-redefs [core/testing? true
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

      (if ns-kw
        (if (some #{ns-kw} ns-list)
          (with-gui-diff
            (if fn-kw
              ;; `test-vars` will run the test but not give feedback if test passes OR test not found
              ;; slightly better than nothing
              (clojure.test/test-vars [(resolve (symbol (str "scb." (name ns-kw) "-test") (name fn-kw)))])
              (clojure.test/run-all-tests (re-pattern (str "scb." (name ns-kw) "-test")))))
          (println "unknown test file:" ns-kw))
        (clojure.test/run-all-tests #"scb\..*-test")))
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
(defn download-url
  "adds a url to the queue to be downloaded."
  [url serialisation]
  (core/put-item (core/get-state :download-queue) [url serialisation])
  nil)
