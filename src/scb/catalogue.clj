(ns scb.catalogue
  (:require
   [flatland.ordered.map :as omap]
   [java-time]
   [scb
    [constants :as constants]
    [utils :as utils]
    [github :as github]
    [core :as core]
    [specs :as sp]]
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
  (let [file-list
        (->> (core/state-paths-matching "wowinterface/*/*.json")
             (group-by (comp str fs/base-name fs/parent))) ;; {"1234" [/path/to/state/1234/listing--combat-mods, ...], ...}

        parse-file (fn [[_ path-list]]
                     (try
                       (->> path-list
                            (map core/read-addon-data)
                            core/to-addon-summary)
                       (catch Exception e
                         (error (format "failed to convert addon data to a catalogue addon: %s" path-list))
                         (throw e))))
        addon-list (remove nil? (pmap parse-file file-list))

        ;; github
        addon-list (into addon-list (github/build-catalogue))]

    (format-catalogue-data-for-output addon-list (utils/datestamp-now-ymd))))

(defn validate
  "validates the given `catalogue-data` as a `:catalogue/catalogue`, returning `nil` if invalid"
  [catalogue-data]
  (info "validating catalogue ...")
  (sp/valid-or-nil :catalogue/catalogue catalogue-data))

(defn-spec write-catalogue (s/or :ok ::sp/extant-file, :error nil?)
  "writes `catalogue-data` to given `output-file` as JSON. returns path to output file."
  [catalogue-data :catalogue/catalogue, output-file ::sp/file]
  (locking (.intern ^String output-file)
    (if (some->> catalogue-data validate (utils/dump-json-file output-file))
      (do (info "wrote catalogue" output-file)
          output-file)
      (error "catalogue data is invalid, refusing to write:" output-file))))

;;

(defn-spec filter-catalogue :catalogue/catalogue
  [fn fn?, catalogue :catalogue/catalogue]
  (let [new-addon-summary-list (filter fn (:addon-summary-list catalogue))]
    (-> catalogue
        (assoc-in [:total] (count new-addon-summary-list))
        (assoc :addon-summary-list new-addon-summary-list))))

(defn-spec shorten-catalogue (s/or :ok :catalogue/catalogue, :problem nil?)
  "returns a truncated version of `catalogue` where all addons considered unmaintained are removed.
  an addon is considered unmaintained if it hasn't been updated since before the given `cutoff` date."
  [catalogue :catalogue/catalogue]
  (let [maintained? (fn [addon]
                        (let [dtobj (java-time/zoned-date-time (:updated-date addon))]
                          (java-time/after? dtobj (utils/todt constants/release-of-previous-expansion))))]
    (filter-catalogue maintained? catalogue)))
