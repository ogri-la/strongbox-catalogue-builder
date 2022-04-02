(ns scb.utils
  (:require
   [flatland.ordered.map :as omap]
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [me.raynes.fs :as fs]
   [clojure.spec.alpha :as s]
   [clojure.data.json]
   [orchestra.spec.test :as st]
   [clj-http.client]
   [orchestra.core :refer [defn-spec]]
   [clojure.pprint]
   [clojure.set]
   [clojure.string]
   [scb
    [specs :as sp]]))

(defn instrument
  "if `flag` is true, enables spec checking instrumentation, otherwise disables it."
  [flag]
  (if flag
    (do
      (st/instrument)
      (info "instrumentation is ON"))
    (do
      (st/unstrument)
      (info "instrumentation is OFF"))))

(defn nilable
  "converts a false-y `x` to `nil`, else `x`"
  [x]
  (cond
    (not x) nil ;; covers nil and false
    (and (coll? x) (empty? x)) nil
    (and (string? x) (clojure.string/blank? x)) nil

    ;; ...

    :else x))

(defn temp-dir
  []
  (System/getProperty "java.io.tmpdir"))

(defn-spec expand-path ::sp/file
  "given a path, expands any 'user' directories, relative directories and symbolic links"
  [path ::sp/file]
  (-> path fs/expand-home fs/normalized fs/absolute str))

(defn select-vals
  "like `get` on `m` but for each key in `ks`. preserves nils."
  [m ks]
  (mapv #(get m %) ks))

(defn-spec to-int (s/or :ok int?, :error nil?)
  "given any value `x`, converts it to an integer or returns `nil` if it can't be converted."
  [x any?]
  (if (int? x)
    x
    (try (Integer/valueOf (str x))
         (catch NumberFormatException nfe
           nil))))

(defn pprint
  [x]
  (with-out-str (clojure.pprint/pprint x)))

;; amalloy:
;; - https://stackoverflow.com/questions/6591604/how-to-parse-url-parameters-in-clojure#answer-6591708
(defn request-to-keywords
  [req]
  (into {} (for [[_ k v] (re-seq #"([^&=]+)=([^&]+)" req)]
             [(keyword k) v])))

(defn url-params
  [url]
  (some-> url clj-http.client/parse-url :query-string request-to-keywords))

(defn ^Integer str-to-int
  [^String x]
  (Integer/parseInt x))

(defn prefix-keys
  [m p]
  (let [rename-map
        (into {}
              (map (fn [k]
                     [k (->> k name (str p "/") keyword)]) (keys m)))]
    (clojure.set/rename-keys m rename-map)))

(defn keyword-name
  "like `(name :foo/bar)` but preserves namespaces.
  the reverse `(keyword \"foo/bar\")` will preserve the namespace."
  [kw]
  (str (.-sym ^clojure.lang.Keyword kw)))

(defn from-json
  [x]
  (some-> x (clojure.data.json/read-str :key-fn keyword)))

(defn-spec to-json string?
  [x any?]
  (with-out-str (clojure.data.json/pprint x :escape-slash false, :key-fn keyword-name)))

(defn-spec order-map map?
  [m map?]
  (into (omap/ordered-map) (sort m)))
