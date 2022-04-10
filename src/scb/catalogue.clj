(ns scb.catalogue
  (:require
   [flatland.ordered.map :as omap]
   [scb
    [utils :as utils]
    [core :as core]
    [specs :as sp]]
   [clojure.string]
   [clojure.spec.alpha :as s]
   [me.raynes.fs :as fs]
   [taoensso.timbre :as log :refer [debug info warn error spy]]
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

;;(defn-spec marshall-catalogue :catalogue/catalogue
(defn marshall-catalogue
  "reads addon data from the state directory, has the right ns parse it and generates a full catalogue."
  []
  (let [;; todo: filter by host here to reduce processing time
        json-file? (fn [path]
                     (and (fs/file? path)
                          (clojure.string/ends-with? path ".json")))

        file-list (->> (core/paths :state-path)
                       fs/list-dir
                       (filter json-file?)
                       (map str))

        parse-file (fn [path]
                     (try
                       (-> path core/read-addon-data core/to-catalogue-addon)
                       (catch Exception e
                         (error (format "failed to convert data at path %s to a catalogue addon: %s" path e))
                         (throw e))))
        addon-list (remove nil? (pmap parse-file file-list))]
    (format-catalogue-data-for-output addon-list (utils/datestamp-now-ymd))))

(defn validate
  "validates the given data as a `:catalogue/catalogue`, returning nil if data is invalid"
  [catalogue]
  (info "validating catalogue ...")
  (sp/valid-or-nil :catalogue/catalogue catalogue))

(defn-spec write-catalogue (s/or :ok ::sp/extant-file, :error nil?)
  "write catalogue to given `output-file` as JSON. returns path to output file"
  [catalogue-data :catalogue/catalogue, output-file ::sp/file]
  (locking (.intern ^String output-file)
    (if (some->> catalogue-data validate (utils/dump-json-file output-file))
      output-file
      (error "catalogue data is invalid, refusing to write:" output-file))))
