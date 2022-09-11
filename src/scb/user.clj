(ns scb.user
  (:require
   [orchestra.core :refer [defn-spec]]
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [net.cgrand.enlive-html :as html :refer [select]]
   [clojure.pprint]
   [clojure.test :as clj-test]
   [me.raynes.fs :as fs]
   [gui.diff :refer [with-gui-diff]]
   [scb
    [specs :as specs]
    [utils :as utils]
    [http :as http]
    [tukui :as tukui]
    [wowi :as wowi]
    [github :as github]
    [core :as core]
    [catalogue :as catalogue]])
  (:import
   [java.util.concurrent LinkedBlockingQueue]))

(comment "user interface to catalogue builder")

(def ns-list [:main :core :utils
              :wowi :tukui
              ])

(defn test
  [& [ns-kw fn-kw]]
  (core/stop)
  ;;(clojure.tools.namespace.repl/refresh) ;; reloads all namespaces, including strongbox.whatever-test ones
  ;;(utils/instrument true) ;; always test with spec checking ON

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
                (clj-test/test-vars [(resolve (symbol (str "scb." (name ns-kw) "-test") (name fn-kw)))])
                (clj-test/run-all-tests (re-pattern (str "scb." (name ns-kw) "-test"))))))
          (clj-test/run-all-tests #"scb\..*-test"))))
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
                 (clj-test/run-all-tests #"scb\..*-test")))
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

(defn wait-for-empty-queues
  "blocks the thread until all queues are empty for a few seconds"
  []
  (let [;; count items in all queues ...
        check #(apply + (mapv core/queue-size core/queue-list))]
    ;; then loop until they're 0 three times in a row
    (loop [num-checks 0
           total-queue-items (check)]
      (println (str (format "checking ... %s total queue items" total-queue-items)
                    (if (> num-checks 0) (format " (%s)" (inc num-checks)) "")))
      (when (or (< num-checks 2)
                (not (zero? total-queue-items)))
        (Thread/sleep (if (< total-queue-items 1000) 2000 5000)) ;; sleep longer when the number of items is over 1000
        (recur (if (not (zero? total-queue-items)) 0 (inc num-checks)) ;; only increment checks if the number of items has hit zero
               (check))))))

(defn clear-recent-urls
  "because processing a url may yield urls that have already been processed in the current run,
  recent-urls builds up a list of urls in state as addons are being scraped and then skips processing of already-visited urls.
  this list of urls needs to be wiped between scrapes otherwise urls will be skipped."
  []
  (swap! core/state assoc :recent-urls #{})
  nil)

(defn download-url
  "adds a url to the queue to be downloaded."
  [url]
  (clear-recent-urls)
  (core/put-item (core/get-state :download-queue) url)
  nil)

(defn parse-url-content
  "downloads and parses the given URL, bypasses queues.
  allows us to grab the exception object with `*e`"
  [url]
  (let [resp {:url url
              :response (http/download url {})}]
    (core/parse-content resp)))

(defn marshall-catalogue
  "reads addon data for each source in given `source-list` (or all known sources) and returns a single list of addons."
  [source-list]
  (let [source-map {:tukui tukui/build-catalogue
                    :wowinterface wowi/build-catalogue
                    :github github/build-catalogue}
        source-map (select-keys source-map (or source-list (keys source-map)))
        addon-list (vec (mapcat #((second %)) source-map))]
    (catalogue/format-catalogue-data-for-output addon-list (utils/datestamp-now-ymd))))

(defn write-catalogue
  "generates a catalogue for each source in `source-list` and writes the corresponding catalogue to disk.
  if `source-list` is empty/nil, then a catalogue for all known sources, a shortened and full catalogue are written to disk."
  [& [{:keys [source-list]}]]
  (let [all-catalogue-data (marshall-catalogue source-list)

        source (fn [& source-list]
                 (fn [row]
                   (some #{(:source row)} (set source-list))))

        source-map {:tukui #(catalogue/filter-catalogue (apply source specs/tukui-source-list) %)
                    :wowinterface #(catalogue/filter-catalogue (source :wowinterface) %)
                    :github #(catalogue/filter-catalogue (source :github) %)
                    :short #(catalogue/shorten-catalogue %)
                    :full identity}

        source-path-map {:tukui "tukui-catalogue.json"
                         :wowinterface "wowinterface-catalogue.json"
                         :github "github-catalogue.json"
                         :short "short-catalogue.json"
                         :full "full-catalogue.json"}

        source-order [:wowinterface :github :tukui :full :short]

        source-list (if (empty? source-list) source-order source-list)]

    (doseq [source source-list
            :let [data ((get source-map source) all-catalogue-data)
                  path (get source-path-map source)]]
      (info source)
      (catalogue/write-catalogue data (core/paths :catalogue-path path)))))

#_(defn to-addon-summary
    [source source-id]
    (core/to-addon-summary (core/find-read-addon-data source source-id)))

(defn-spec write-addon-details nil?
  "generates a `detail.json` file"
  [source :addon/source, source-id :addon/source-id]
  (let [output-path (core/paths :state-path (name source) source-id "detail.json")
        addon-data (core/to-addon-detail (core/find-read-addon-data source source-id))]
    (when addon-data
      (core/write-addon-data output-path addon-data))))

(defn-spec write-all-addon-details nil?
  "write `detail.json` for all addons"
  []
  (let [;; [[:wowinterface "5673"], [:wowinterface "5607"], ...]
        id-key (juxt (comp keyword str fs/base-name fs/parent)
                     (comp str fs/base-name))]
    (dorun (pmap #(apply write-addon-details (id-key %)) (core/state-paths-matching "*/*")))))

(defn refresh-data
  []
  ;; rm all .json files in state/
  ;; ...

  ;; remove memory of any recently scraped urls
  (clear-recent-urls)

  ;; scrape all of the html
  (download-url "https://www.wowinterface.com/addons.php")
  ;; scrape all of the api
  (download-url wowi/api-file-list))

(defn delete-cache-path
  "deletes a cache file rooted in the `:cache-http-path` directory."
  [[cache-path url]]
  (warn "deleting cache:" url)
  (utils/safe-delete-file cache-path (core/paths :cache-http-path))
  nil)

(defn delete-addon-cache
  "deletes cache files for given addon"
  [source source-id]
  (run! delete-cache-path
        (core/cache-paths-matching (re-pattern (format "info%s$|%s\\.json$" source-id source-id)))))

(defn daily-wowi-update
  "deletes the listing pages and API filelist cache,
  refreshes addon data, downloading missing cache files as necessary,
  finds addons updated in the last day using fresh data,
  deletes their cache, refreshes data again.
  remember: we can't trust the API filelist or listing pages to be complete, we have to consult both."
  []
  (run! delete-cache-path (core/cache-paths-matching #"page=|cat|filelist"))
  (refresh-data)
  (wait-for-empty-queues)

  (let [age 2 ;; days. 2 days will ensure nothing is missed

        updated-recently-from-listings
        (->> (core/state-paths-matching "wowinterface/*/listing--*")
             (group-by (comp str fs/base-name fs/parent)) ;; {"1234" [/path/to/state/1234/listing--combat-mods, ...], ...}
             vals
             (map first) ;; there will be 0 or many listing--* files, we want the first
             (remove nil?) ;; if there are zero, first will give us nils
             (map core/read-addon-data)
             (filterv (comp (partial utils/less-than-n-days-old? age) :updated-date)))

        updated-recently-from-filedetails
        (->> (wowi/parse-api-file-list {:url wowi/api-file-list
                                        :response (http/download wowi/api-file-list {})})
             :parsed
             (map :updated-date)
             (remove nil?)
             (filterv (partial utils/less-than-n-days-old? age)))

        ;; #{ [:wowinterface 1234], [:wowinterface 4321], ... }
        addon-id (juxt :source :source-id)
        updated-recently (set (into (map addon-id updated-recently-from-listings)
                                    (map addon-id updated-recently-from-filedetails)))]

    (run! (partial apply delete-addon-cache) updated-recently)
    (refresh-data) ;; shouldn't we just re-scrape those updated recently? it's cached data but still slow ...
    (wait-for-empty-queues)
    (write-all-addon-details)

    nil))

(defn daily-addon-update
  [& [{:keys [source-list]}]]
  (let [source-map {:wowinterface daily-wowi-update}
        source-map (select-keys source-map (or source-list (keys source-map)))]
    (run! #((second %)) source-map)
    ;; important! pass the original source-list and not one derived from the source-map here.
    (write-catalogue {:source-list source-list})))
