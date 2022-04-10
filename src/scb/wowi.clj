(ns scb.wowi
  (:require
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   [me.raynes.fs :as fs]
   [clojure.string :refer [trim lower-case upper-case]]
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [net.cgrand.enlive-html :as html :refer [select]]
   [scb
    [tags :as tags]
    [core :as core :refer [error*]]
    [utils :as utils]
    [specs :as sp]]
   [java-time]
   [java-time.format]))

(def host "https://www.wowinterface.com")

(def api-host "https://api.mmoui.com/v4/game/WOW")

(def api-file-list "https://api.mmoui.com/v4/game/WOW/filelist.json")

(def category-group-page-list
  ["/downloads/index.php" "/addons.php" ;; landing page
   "/downloads/cat39.html" ;; Class & Role Specific
   "/downloads/cat109.html" ;; Info, Plug-in Bars

   ;; these are category group pages but won't appear in a typical scrape.
   "/downloads/cat23.html" ;; Stand-Alone addons
   "/downloads/cat28.html" ;; Compilations
   "/downloads/cat158.html" ;; WoW Classic
   "/downloads/cat144.html" ;; Utilities
   "/downloads/cat145.html" ;; Optional
   ])

(defn-spec file-list? boolean?
  [url ::sp/url]
  (= url api-file-list))

(defn-spec category-group-page? boolean?
  "returns `true` if the given `url` is a list of category groups"
  [url ::sp/url]
  ;; landing page (`index.php` and `addons.php`) and the category group pages should have no URL parameters.
  (let [params (utils/url-params url)
        filename (-> url java.net.URL. .getPath)] ;; "https://wowinterface.com/foo/addons.php?foo=bar" => "/foo/addons.php"
    (boolean
     (and (empty? params)
          (some #{filename} category-group-page-list)))))

(defn-spec category-listing-page? boolean?
  "returns `true` if the given `url` is a list of addons."
  [url ::sp/url]
  ;; check for presence of a 'page' url parameter. addons and category group pages won't be paginated. 
  (boolean (some-> url utils/url-params :page utils/str-to-int)))

(defn to-html
  [downloaded-item]
  (cond
    ;; path to file
    (and (string? downloaded-item)
         (fs/exists? downloaded-item)) (-> downloaded-item slurp html/html-snippet)

    ;; downloaded item
    (contains? downloaded-item :response) (-> downloaded-item :response :body html/html-snippet)))

(defn swallow
  [x match]
  (when-not (= x match)
    x))

(defn-spec -format-wowinterface-dt string?
  "formats a shitty US-style m/d/y date with a shitty 12 hour time component and no timezone
  into a glorious RFC3399 formatted UTC string."
  [dt string?]
  (let [;; https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
        dt (java-time/local-date-time "MM-dd-yy hh:mm a" dt) ;; "09-07-18 01:27 PM" => obj with no tz
        ;; no tz info available on site, assume utc
        dt-utc (java-time/zoned-date-time dt "UTC") ;; obj with no tz => utc obj
        fmt (get java-time.format/predefined-formatters "iso-offset-date-time")]
    (java-time/format fmt dt-utc)))

(defn-spec format-wowinterface-dt string?
  "formats a shitty US-style m/d/y date with a shitty 12 hour time component and no timezone
  into a glorious RFC3399 formatted UTC string."
  [dt string?]
  (try
    (-format-wowinterface-dt (lower-case dt)) ;; lowercase (java 11) first
    (catch Exception e ;; DateTimeParseException *isn't* being thrown here
      ;; because of some locale bs, datetime formatting is case sensitive in java 11 but
      ;; not java 8 and only in non-US locales. This is why it passes tests in CI but not locally.
      ;; -- https://stackoverflow.com/questions/38250379/java8-datetimeformatter-am-pm
      (-format-wowinterface-dt (upper-case dt))))) ;; upper-case (java 8)

(defn extract-source-id
  [a]
  ;; fileinfo.php?s=c33edd26881a6a6509fd43e9a871809c&amp;id=23145 => 23145
  (-> a :attrs :href (clojure.string/split #"&.+=") last utils/to-int))

(defn extract-source-id-2
  [s]
  (utils/str-to-int (last (re-find (re-matcher #"info(\d+)" s)))))

(defn-spec web-addon-url ::sp/url
  [source-id :addon/source-id]
  ;; 4729 => https://www.wowinterface.com/downloads/info4729
  (str host "/downloads/info" source-id))

(defn-spec api-addon-url ::sp/url
  [source-id :addon/source-id]
  (str api-host "/filedetails/" source-id ".json"))

;; ---

(defn parse-category-group-page
  "returns a mixed list of urls to category-listing pages and other category-group pages."
  [html-snippet]
  (let [cat-list (-> html-snippet (select [:div#colleft :div.subcats :div.subtitle :a]))
        final-url (fn [href]
                    ;; converts the href that looks like '/downloads/cat19.html' to '/downloads/index.php?cid=19"
                    (let [cat-id (str "index.php?cid=" (clojure.string/replace href #"\D*" "")) ;; "/downloads/cat19.html" => "19" => "index.php?cid=19"
                          sort-by "&sb=dec_date" ;; updated date, most recent to least recent
                          another-sort-by "&so=desc" ;; most to least recent. must be consistent with `sort-by` prefix
                          pt "&pt=f" ;; nfi but it's mandatory
                          page "&page=1"] ;; not necessary, and `1` is default. we'll add it here to avoid a cache miss later

                      (if (category-group-page? href)
                        ;; preserve links to category group pages so we can identify them later
                        href
                        ;; => https://www.wowinterface.com/downloads/index.php?cid=160&sb=dec_date&so=desc&pt=f&page=1
                        (str host, "/downloads/", cat-id, sort-by, another-sort-by, pt, page))))
        extractor (fn [cat]
                    {:label (-> cat :content first)
                     :url (-> cat :attrs :href final-url)})]
    (debug (format "%s categories found" (count cat-list)))
    {:download (mapv extractor cat-list)}))

(defn extract-addon-summary
  "extracts a snippet of addon information from a listing of addons"
  [snippet]
  (try
    (let [extract-updated-date #(-> % (subs 8) trim) ;; "Updated 09-07-18 01:27 PM " => "09-07-18 01:27 PM"
          anchor (-> snippet (select [[:a (html/attr-contains :href "fileinfo")]]) first)
          label (-> anchor :content first trim)]
      {:source :wowinterface
       :source-id (extract-source-id anchor)
       :name (-> label utils/slugify)
       :wowi/url (web-addon-url (extract-source-id anchor))
       :wowi/title label
       :wowi/web-updated-date (-> snippet (select [:div.updated html/content]) first extract-updated-date)
       :wowi/downloads (-> snippet (select [:div.downloads html/content]) first (clojure.string/replace #"\D*" "") utils/to-int)})
    (catch RuntimeException re
      (error* re (format "failed to scrape snippet, excluding from results: %s" (.getMessage re)) :payload snippet))))

(defn scrape-category-page-range
  "extract the number of results from the page navigation"
  [html-snippet url url-label]
  (let [page-count (some-> html-snippet
                           (select [:.pagenav [:td.alt1 html/last-of-type] :a])
                           utils/nilable
                           first
                           :attrs
                           :href
                           (clojure.string/split #"=")
                           last
                           utils/to-int)

        page-count (or page-count 1)
        page-range (range 1 (inc page-count))

        mkurl (fn [page-num]
                {:url (clojure.string/replace url #"page=\d+" (str "page=" page-num))
                 :label url-label})]
    (mapv mkurl page-range)))

(defn-spec parse-addon-detail-page :result/map
  [downloaded-item :result/downloaded-item]
  (let [html-snippet (to-html downloaded-item)
        coerce-releases
        (fn [row]
          {;; this could be guessed by splitting on the hyphen and then removing common elements between releases.
           ;; would only work on multiple downloads.
           ;; wouldn't work on single releases
           ;;:version nil 
           :download-url (let [url (str host (:href row))] ;; https://www.wowinterface.com/downloads/landing.php?s=64f6d79344812e1f152c1fcc54871e2a&fileid=5332
                           (utils/strip-url-param url :s))     ;; https://www.wowinterface.com/downloads/landing.php?fileid=5332
           :game-track (case (:title row)
                         "WoW Retail" :retail
                         "WoW Classic" :classic
                         "The Burning Crusade WoW Classic" :classic-tbc)})

        ;; the 'Version: 9.2.0.0, Classic: 9.1.5.0' string to the left of the downloads.
        ;; I haven't seen a 'TBC' version string yet ...
        version-strings
        (-> (select html-snippet [:#version html/text])
            first
            (clojure.string/split #", "))
        version-strings (mapv #(clojure.string/split % #": ") version-strings)

        infobox
        (select html-snippet #{[:.TabTab second :td] ;; handles tabber when images are present
                               [:.tomboxinner :td]}) ;; no tabber

        ;; sometimes the list of compatibile game versions is missing.
        no-compatibility? (-> infobox first html/text (= "Updated:"))
        infobox (if no-compatibility? (into [nil nil] infobox) infobox)

        [_ compatibility
         _ dt-updated
         _ dt-created ;; may be 'unknown'
         _ num-downloads
         _ num-favourites
         _ md5
         _ categories] ;; categories may be missing
        infobox

        compatibility (html/select compatibility [:div html/text])

        game-version-from-compat-string
        (fn [compat-string]
          (last (re-find (re-matcher #"\(([\d\.]+)\)" compat-string))))

        game-track-set (set (mapv (comp utils/game-version-to-game-track
                                        game-version-from-compat-string) compatibility))

        ;; "archived files"
        arc (select html-snippet [:div#other_t [:div (html/nth-of-type 3)] :tr])
        arc (rest
             (html/at arc
                      ;; the filename column has two identical links. remove the first one, it looks like a JS anchor
                      [[html/first-of-type :a]] nil
                      ;; removes (most) whitespace
                      [html/whitespace] nil))

        kv (fn [k]
             (fn [x]
               {k (html/text x)}))

        arc (html/at arc
                     ;; extract the filename and it's link from the filename column
                     [:tr [html/first-of-type :td]]
                     (fn [td]
                       (let [a (-> td :content  first :content second)]
                         {:name (-> a :content first)
                          :download-url (let [url (str host (-> a :attrs :href))]
                                          (utils/strip-url-param url :s))}))

                     ;; each transformation consumes a `:td`, so it's `first-of-type` each time
                     [:tr [html/first-of-type :td]] (kv :version)
                     [:tr [html/first-of-type :td]] (kv :size) ;; todo: convert to bytes perhaps?
                     [:tr [html/first-of-type :td]] (kv :author)
                     ;; comp'ing `kv` here seems to work as `html/text` returns text if given text :) 
                     [:tr [html/first-of-type :td]] (comp (kv :date) format-wowinterface-dt html/text))

        ;; we now have something like: [{:tag :tr, :content [{:name "..."}, {:size "..."}, ...]}, ...]
        ;; convert it into a single list of maps [{...}, {...}, ...]
        archived-files (mapv #(into {} (:content %)) arc)

        label (select html-snippet [[:meta (html/pred #(-> % :attrs :property (= "og:title")))]])
        label (-> label first :attrs :content)

        category-set (set (select categories [:a html/text]))
        tag-set (tags/category-set-to-tag-set :wowinterface category-set)

        source-id (extract-source-id-2 (:url downloaded-item))
        
        _ (when (= source-id 17796)
            (warn "addon detail")
            (warn "with cats" category-set))

        struct
        {:source :wowinterface
         :source-id source-id
         :label label
         :name (utils/slugify label)
         :game-track-set game-track-set
         :updated-date (some-> dt-updated :content first format-wowinterface-dt)
         :created-date (some-> dt-created :content first (swallow "unknown") format-wowinterface-dt)
         :tag-set tag-set
         :wowi/url (:url downloaded-item)
         :wowi/compatibility compatibility
         :wowi/web-updated-date (some-> dt-updated :content first)
         :wowi/web-created-date (some-> dt-created :content first (swallow "unknown"))
         :wowi/downloads (some-> num-downloads :content first (clojure.string/replace #"," "") utils/str-to-int)
         :wowi/favourites (some-> num-favourites :content first (clojure.string/replace #"," "") utils/str-to-int)
         :wowi/checksum (some-> md5 :content first :attrs :value)
         :wowi/category-set category-set
         :wowi/latest-release-versions version-strings
         :wowi/latest-release (->> (select html-snippet [:.infobox :div#download :a])
                                   (map :attrs)
                                   (mapv coerce-releases))
         :wowi/archived-files archived-files}
        struct (utils/drop-nils struct (keys struct))]
    {:parsed [struct]}))

(defn-spec parse-category-listing :result/map
  "returns a mixed list of urls and addon data from a page of a category's addon list."
  [downloaded-item :result/downloaded-item]
  (let [url (:url downloaded-item)
        html-snippet (to-html downloaded-item)

        ;; on the first page, make a list of all other pages to download
        first-page? (-> url utils/url-params :page (= "1"))
        listing-page-list (if first-page?
                            (rest ;; skip the first page, we're parsing it right here
                             (scrape-category-page-range html-snippet url (:label downloaded-item)))
                            [])

        addon-list-html (select html-snippet [:#filepage :div.file])
        extractor (fn [addon-html-snippet]
                    (let [category (:label downloaded-item)
                          addon-summary (extract-addon-summary addon-html-snippet)]
                      (when (and category
                                 (= (:source-id addon-summary) 17796))
                        (warn addon-summary)
                        (warn "with cats" category))
                      (if category
                        (merge addon-summary {:wowi/category-set #{category}
                                              :tag-set (tags/category-set-to-tag-set :wowinterface #{category})})
                        addon-summary)))
        addon-list (mapv extractor addon-list-html)
        addon-url-list (mapv :wowi/url addon-list)]
    {:download (into listing-page-list addon-url-list)
     :parsed addon-list}))

;;

(defn-spec parse-api-file-list :result/map
  [downloaded-item :result/downloaded-item]
  (let [process-addon (fn [addon]
                        (let [addon (utils/prefix-keys addon "wowi")]
                          (merge addon
                                 ;; todo: prefix these with 'sb'
                                 {:source-id (:wowi/id addon)
                                  :source :wowinterface
                                  :api-url (api-addon-url (:wowi/id addon))
                                  :web-url (web-addon-url (:wowi/id addon))})))

        addon-list (->> downloaded-item
                        :response
                        :body
                        utils/from-json
                        (mapv process-addon))]

    {:download (->> addon-list
                    (map (juxt :api-url :web-url))
                    flatten)
     :parsed addon-list}))

(defn-spec parse-api-addon-detail :result/map
  [downloaded-item :result/downloaded-item]
  (let [addon-list (-> downloaded-item :response :body utils/from-json)

        _ (when (> (count addon-list) 1)
            (warn "wowi, discovered api addon detail with more than one item:" (:url downloaded-item)))

        addon (first addon-list)
        addon (utils/prefix-keys addon "wowi")
        updates {:source :wowinterface
                 :source-id (:wowi/id addon)
                 :description (some-> addon :wowi/description clojure.string/split-lines first)
                 :latest-release-list #{{:version (:wowi/version addon)
                                         ;; nfi what 'd' is, it's not neccesary though.
                                         :download-url (utils/strip-url-param (:wowi/downloadUri addon) :d)}}}]
    {:parsed [(merge addon updates)]}))

;;

(defmethod core/parse-content "api.mmoui.com"
  [downloaded-item]
  (cond
    (file-list? (:url downloaded-item)) (parse-api-file-list downloaded-item)
    :else (parse-api-addon-detail downloaded-item)))

(defmethod core/parse-content "www.wowinterface.com"
  [downloaded-item]
  ;; figure out what sort of item we have to parse.
  ;; we can't rely on the url to know if we have a 'category' page, like the index page or the 'standalone addons' pages.
  ;; we also can't rely on a link in the category page going to a listing page - it may go to another category page.
  ;; ('index' -> 'standalone addons', 'index' -> 'class & role specific')
  ;; so we need to parse the content and look at the structure.
  (cond
    (category-group-page? (:url downloaded-item)) (parse-category-group-page (to-html downloaded-item))
    (category-listing-page? (:url downloaded-item)) (parse-category-listing downloaded-item)
    :else (parse-addon-detail-page downloaded-item)))

;; --- catalogue wrangling

(defn-spec -to-catalogue-addon (s/or :ok :addon/summary, :invalid nil?)
  [addon-data :addon/part]
  (let [addon-data
        (select-keys addon-data [:source
                                 :source-id
                                 :game-track-set
                                 :label
                                 :name
                                 :tag-set
                                 :updated-date
                                 :wowi/url
                                 :wowi/downloads])

        addon-data (utils/remove-key-ns addon-data)
        addon-data (clojure.set/rename-keys addon-data {:downloads :download-count
                                                        :game-track-set :game-track-list
                                                        :tag-set :tag-list})
        addon-data (update-in addon-data [:tag-list] (comp vec sort))
        addon-data (update-in addon-data [:game-track-list] (comp vec sort))]

    (if-not (s/valid? :addon/summary addon-data)
      (warn (format "%s (%s) failed to coerce addon data to a valid :addon/summary" (:source-id addon-data) (:source addon-data)))
      addon-data)))

(defmethod core/to-catalogue-addon :wowinterface
  [addon-data]
  (-to-catalogue-addon addon-data))
