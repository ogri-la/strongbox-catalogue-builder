(ns scb.wowi
  (:require
   [slugger.core :refer [->slug] :rename {->slug slugify}]
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   [me.raynes.fs :as fs]
   [clojure.string :refer [trim lower-case upper-case]]
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [net.cgrand.enlive-html :as html :refer [select]]
   [scb
    [core :as core]
    [utils :as utils]
    [specs :as sp]]
   [java-time]
   [java-time.format]))

(def host "https://www.wowinterface.com/downloads/")

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
(defn category-group-page?
  [url]
  (let [filename (-> url java.net.URL. .getPath)] ;; "https://wowinterface.com/foo/addons.php?foo=bar" => "/foo/addons.php"
    (boolean (some #{filename} category-group-page-list))))

(defn category-listing-page?
  [url]
  false)

(defn to-html
  [downloaded-item]
  (cond
    ;; path to file
    (and (string? downloaded-item)
         (fs/exists? downloaded-item)) (-> downloaded-item slurp html/html-snippet)

    ;; downloaded item
    (contains? downloaded-item :response) (-> downloaded-item :response :body html/html-snippet)))

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

(defn extract-addon-url
  [a]
  (str host "info" (extract-source-id a)))

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
                        (str host, cat-id, sort-by, another-sort-by, pt, page))))
        extractor (fn [cat]
                    {:label (-> cat :content first)
                     :url (-> cat :attrs :href final-url)})]
    (debug (format "%s categories found" (count cat-list)))
    {:download (mapv extractor cat-list)}))

(defn extract-addon-summary
  [snippet]
  (try
    (let [extract-updated-date #(format-wowinterface-dt
                                 (-> % (subs 8) trim)) ;; "Updated 09-07-18 01:27 PM " => "09-07-18 01:27 PM"
          anchor (-> snippet (select [[:a (html/attr-contains :href "fileinfo")]]) first)
          label (-> anchor :content first trim)]
      {:url (extract-addon-url anchor)
       :name (-> label slugify)
       :label label
       :source "wowinterface"
       :source-id (extract-source-id anchor)
       ;;:description nil ;; not available in summary
       ;;:category-list [] ;; not available in summary, added by caller
       ;;:created-date nil ;; not available in summary
       :updated-date (-> snippet (select [:div.updated html/content]) first extract-updated-date)
       :download-count (-> snippet (select [:div.downloads html/content]) first (clojure.string/replace #"\D*" "") utils/to-int)})
    (catch RuntimeException re
      (error re (format "failed to scrape snippet with, excluding from results: %s" (.getMessage re)) :payload snippet))))

(defn scrape-category-page-range
  "extract the number of results from the page navigation"
  [html-snippet url]
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
                {:url (clojure.string/replace url #"page=\d+" (str "page=" page-num))})]
    (mapv mkurl page-range)))

(defn-spec parse-category-listing :result/map
  "returns a mixed list of urls and addon data."
  [downloaded-item :result/downloaded-item]
  (let [url (:url downloaded-item)
        html-snippet (to-html downloaded-item)

        ;; on the first page, make a list of all other pages to download
        first-page? (-> url utils/url-params :page (= "1"))
        listing-page-list (if first-page?
                            (rest ;; skip the first page, we're parsing it right here
                             (scrape-category-page-range html-snippet url))
                            [])

        addon-list-html (select html-snippet [:#filepage :div.file])
        extractor (fn [addon-html-snippet]
                    (assoc (extract-addon-summary addon-html-snippet) :category-list #{(:label downloaded-item)}))
        addon-list (mapv extractor addon-list-html)]

    {:download listing-page-list
     :parsed addon-list}))

(defmethod core/parse-content "www.wowinterface.com"
  [downloaded-item]
  ;; figure out what sort of item we have to parse.
  ;; we can't rely on the url to know if we have a 'category' page, like the index page or the 'standalone addons' pages.
  ;; we also can't rely on a link in the category page going to a listing page - it may go to another category page.
  ;; ('index' -> 'standalone addons', 'index' -> 'class & role specific')
  ;; so we need to parse the content and look at the structure.
  (cond
    (category-group-page? (:url downloaded-item)) (parse-category-group-page (to-html downloaded-item))
    (category-listing-page? (:url downloaded-item)) (parse-category-listing downloaded-item)))
