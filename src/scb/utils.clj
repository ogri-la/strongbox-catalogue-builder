(ns scb.utils
  (:require
   [me.raynes.fs :as fs]
   [clojure.spec.alpha :as s]

   [orchestra.core :refer [defn-spec]]
   [scb
    [specs :as sp]]))

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

(defn url-params
  [url]
  {})
