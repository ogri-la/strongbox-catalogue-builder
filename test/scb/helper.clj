(ns scb.helper
  (:require
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [scb
    [core :as core]
    [utils :as utils]]
   [clj-http.fake :refer [with-global-fake-routes-in-isolation]]
   [me.raynes.fs :as fs]))

(def fixture-dir (-> "test/fixtures" fs/absolute fs/normalized str))

(defn fixture-path
  [filename]
  (str (fs/file fixture-dir filename)))

(defn temp-state-dir
  "each `deftest` is executed in a new and self-contained location, accessible as fs/*cwd*.
  `(testing ...` sections share the same fixture. beware of cache hits."
  [f]
  (let [;; /tmp/scb-test.1642030630883-1255099235
        temp-dir-path (-> "scb-test." fs/temp-dir str utils/expand-path)]
    (try
      (with-redefs [core/state-dir (constantly temp-dir-path)]
        (fs/with-cwd temp-dir-path
          (debug "created temp working directory" fs/*cwd*)
          (f)))
      (finally
        (warn "deleting dir" temp-dir-path)
        (fs/delete-dir temp-dir-path)))))

(defn no-http
  [f]
  (with-global-fake-routes-in-isolation {}
    (f)))

(defmacro with-instrumentation-off
  [& form]
  `(try
     (utils/instrument false)
     ~@form
     (finally
       (utils/instrument true))))

(defmacro with-running-app+opts
  [opts & form]
  `(try
     (core/start)
     ~@form
     (println "calling stop")
     (core/stop)
     (finally
       (when (core/started?)
         (core/stop)))))

(defmacro with-running-app
  [& form]
  `(with-running-app+opts {} ~@form))
