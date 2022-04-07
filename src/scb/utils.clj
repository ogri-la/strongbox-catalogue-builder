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

(defn strip-url-param
  [url param]
  (let [params (dissoc (url-params url) param)
        query-string (->> params
                          (mapv (fn [[k v]] (str (name k) "=" v)))
                          (clojure.string/join "="))]
    (-> url
        clj-http.client/parse-url
        (assoc :query-string query-string)
        clj-http.client/unparse-url)))

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

(defn-spec safe-subs (s/nilable string?)
  "similar to `subs` but can handle `nil` input and a `max` value larger than (or less than) length of given string `x`."
  [^String x (s/nilable string?), ^Integer maxval int?]
  (when x
    (subs x 0 (min (count x) (if (neg? maxval) 0 maxval)))))

(defn-spec game-version-to-game-track ::sp/game-track
  "'1.13.2' => ':classic', '8.2.0' => 'retail'"
  [game-version string?]
  (let [prefix (safe-subs game-version 2)]
    (case prefix
      ;; 1.x.x == classic (vanilla)
      "1." :classic
      ;; 2.x.x == classic (burning crusade)
      "2." :classic-tbc
      ;; 3.x.x == classic (wrath of the lich king) (probably)
      :retail)))
