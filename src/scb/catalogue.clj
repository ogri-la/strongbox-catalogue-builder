(ns scb.catalogue
  (:require
   [flatland.ordered.map :as omap]
   [scb
    [utils :as utils]
    [core :as core]
    [specs :as sp]]
   [clojure.spec.alpha :as s]
   [me.raynes.fs :as fs]
   ;;[taoensso.timbre :as log :refer [debug info warn error spy]]
   [orchestra.core :refer [defn-spec]]))

(defn-spec format-catalogue-data-for-output :catalogue/catalogue
  "returns a correctly formatted, ordered, catalogue given a list of addons and a datestamp.
  addon maps are converted to an `ordered-map` for diffing."
  [addon-list :addon/summary-list, datestamp :catalogue/datestamp] ;;::sp/ymd-dt]
  (let [addon-list (mapv #(into (omap/ordered-map) (sort %))
                         (sort-by :name addon-list))]
    {:spec {:version 2}
     :datestamp datestamp
     :total (count addon-list)
     :addon-summary-list addon-list}))

;;

(defn-spec marshall-catalogue :catalogue/catalogue
  "reads addon data from the state directory, has the right ns parse it and generates a full catalogue."
  []
  (let [;; todo: filter by host here to reduce processing time
        file-list (fs/list-dir (core/paths :state-path))
        parse-file (fn [path]
                     (-> path slurp utils/from-json core/to-catalogue-addon))
        addon-list (pmap parse-file file-list)]
    (format-catalogue-data-for-output addon-list)))
