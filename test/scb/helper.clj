(ns scb.helper
  (:require
   [scb.core :as core]
   [clj-http.fake :refer [with-global-fake-routes-in-isolation]]
   [me.raynes.fs :as fs]))

(def fixture-dir (-> "test/fixtures" fs/absolute fs/normalized str))

(defn fixture-path
  [filename]
  (str (fs/file fixture-dir filename)))

(defn no-http
  [f]
  (with-global-fake-routes-in-isolation {}
    (f)))

(defmacro with-running-app+opts
  [opts & form]
  `(try
     (core/start) ;;(merge {:ui :cli} ~opts))
     ~@form
     (finally
       (println "calling stop")
       (core/stop))))

(defmacro with-running-app
  [& form]
  `(with-running-app+opts {} ~@form))
