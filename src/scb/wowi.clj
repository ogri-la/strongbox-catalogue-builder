(ns scb.wowi
  (:require
   [clojure.spec.alpha :as s]
   [orchestra.core :refer [defn-spec]]
   [me.raynes.fs :as fs]
   [clojure.set :refer [rename-keys]]
   [clojure.string :refer [trim lower-case upper-case]]
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [net.cgrand.enlive-html :as html :refer [select]]
   [scb
    [tags :as tags]
    [core :as core]
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

    (string? downloaded-item) (html/html-snippet downloaded-item)

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

        source-id (extract-source-id-2 (:url downloaded-item))

        ;; some addon pages have the string "Compatible with Retail, Classic & TBC" on it.
        ;; this correlates with the API file list's `gameVersions`, I think, but the inverse isn't true,
        ;; i.e., the html won't show this string if the api's `gameVersions` lists all versions.
        compatible-with-string
        (some-> html-snippet
                (html/select [:#multitoc html/content])
                first)

        ;; unused, for now
        ;;compatible-with-set
        ;;(some->> (clojure.string/split (or compatible-with-string "") #"\W")
        ;;         (map utils/guess-game-track)
        ;;         (remove nil?)
        ;;         set)

        coerce-releases
        (fn [row]
          {;; this could be guessed by splitting on the hyphen and then removing common elements between releases.
           ;; would only work on multiple downloads.
           ;; wouldn't work on single releases
           ;;:version nil 
           :download-url (let [;; "https://www.wowinterface.com/downloads/landing.php?s=64f6d79344812e1f152c1fcc54871e2a&fileid=5332"
                               url (str host (:href row))]
                           ;; "https://www.wowinterface.com/downloads/landing.php?fileid=5332"
                           (utils/strip-url-param url :s))
           ;; this can't be trusted, at least for single downloads. see:
           ;; - https://www.wowinterface.com/downloads/info25230
           :game-track (case (:title row)
                         "WoW Retail" :retail
                         "WoW Classic" :classic
                         "The Burning Crusade WoW Classic" :classic-tbc
                         "Wrath of the Lich King WoW Classic" :classic-wotlk)})

        latest-release-list (->> (select html-snippet [:.infobox :div#download :a])
                                 (map :attrs)
                                 (mapv coerce-releases))

        ;; 2022-04-23: disabled. I'd rather an addon be too lenient with false-retail game tracks than too strict.
        ;; for single releases we can't trust it. it will always be 'retail'
        ;; .. this introduces the new problem of addons without a game track :(
        ;;latest-release-list (if (= 1 (count latest-release-list))
        ;;                      (update-in latest-release-list [0] #(dissoc % :game-track))
        ;;                      latest-release-list)

        ;; the 'Version: 9.2.0.0, Classic: 9.1.5.0' string to the left of the downloads.
        ;; I haven't seen a 'TBC' version string yet ...
        version-strings
        (-> (select html-snippet [:#version html/text])
            first
            (clojure.string/split #", "))
        version-strings (mapv #(clojure.string/split % #": ") version-strings)

        ;; map the game version to th
        map-versions (fn [latest-release-list, latest-release-versions]
                       (case (count latest-release-versions)
                         ;; we have a 'version' and a 'classic' value.
                         ;; there may be 2 or more releases
                         2 (let [[[_ retail] [_ classic]] latest-release-versions]
                             (vec
                              (for [release latest-release-list
                                    :let [game-track (:game-track release)]]
                                (assoc release :version (if (= game-track :retail) retail classic)))))

                         ;; we have just one value for 'Version'. it doesn't matter whether it's classic or not, all releases are getting it.
                         ;; note: can't do this, I've seen mismatches between the latest version and the version embedded in the filename :(
                         ;;1 (mapv #(assoc % :version (get-in latest-release-versions [0 1])) latest-release-list)
                         1 (mapv (fn [latest-release]
                                   (let [version-string (get-in latest-release-versions [0 1])
                                         ;; "https://www.wowinterface.com/downloads/dlfile3121/03-22-22-AutoLoggerClassic-v2.1.1-tbcc-release-bcc.zip" =>
                                         ;; ["03-22-22-AutoLoggerClassic-v2.1.1-tbcc-release-bcc", ".zip"]
                                         ;; "https://www.wowinterface.com/downloads/landing.php?fileid=25314" =>
                                         ;; ["landing" ".php?fileid=25314"]
                                         [filename, ext] (fs/split-ext (fs/base-name (:download-url latest-release)))]
                                     (if (= ext ".zip")
                                       (assoc latest-release :version filename)
                                       (assoc latest-release :version version-string))))
                                 latest-release-list)

                         ;; we have nothing :(
                         0 (do (warn "no version for" source-id)
                               latest-release-list)))

        latest-release-set (map-versions latest-release-list version-strings) ;; not a set just yet ...

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

        updated-date (some-> dt-updated :content first format-wowinterface-dt)
        compatibility (html/select compatibility [:div html/text])

        game-version-from-compat-string
        (fn [compat-string]
          (last (re-find (re-matcher #"\(([\d\.]+)\)" compat-string))))

        ;; warning! compatibility is known to be incomplete/misleading, so more munging is required.
        game-track-set (->> compatibility
                            (map (comp utils/game-version-to-game-track game-version-from-compat-string))
                            set)

        ;; when there is just one release on the detail page, it's *always* 'retail', even if it's not.
        ;; if there is exactly one entry in the game-track-set, replace it with that.
        latest-release-set (if (and (= 1 (count latest-release-set))
                                    (= 1 (count game-track-set)))
                             (assoc-in latest-release-set [0 :game-track] (first game-track-set))
                             latest-release-set)

        ;; when there is just one release but more than one in the compatibility list, duplicate the release for each 
        latest-release-set (if (and (= 1 (count latest-release-set))
                                    (> (count game-track-set) 1))
                             (->> game-track-set
                                  (map #(assoc (first latest-release-set) :game-track %))
                                  set)
                             latest-release-set)

        latest-release-set (set latest-release-set) ;; now we're a set :)

        ;; makes sense when there are > 1 releases available, otherwise pointless.
        game-track-set (into game-track-set (remove nil? (map :game-track latest-release-set)))

        ;;game-track-set (into game-track-set compatible-with-set)

        ;; fills in most of the blanks for the huge number of dead and ancient addons
        game-track-set (if (utils/before-classic? updated-date)
                         (conj game-track-set :retail)
                         game-track-set)

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

        ;; archived files
        arc (html/at arc
                     ;; extract the filename and it's link from the filename column
                     [:tr [html/first-of-type :td]]
                     (fn [td]
                       (let [a (-> td :content  first :content second)]
                         {:wowi/name (-> a :content first)
                          :wowi/download-url (let [url (str host (-> a :attrs :href))]
                                               (utils/strip-url-param url :s))}))

                     ;; each transformation consumes a `:td`, so it's `first-of-type` each time
                     [:tr [html/first-of-type :td]] (kv :wowi/version)
                     [:tr [html/first-of-type :td]] (kv :wowi/size) ;; todo: convert to bytes?
                     [:tr [html/first-of-type :td]] (kv :wowi/author)
                     ;; comp'ing `kv` here seems to work as `html/text` returns text if given text :) 
                     [:tr [html/first-of-type :td]] (comp (kv :date) format-wowinterface-dt html/text))

        ;; we now have something like: [{:tag :tr, :content [{:wowi/name "..."}, {:wowi/size "..."}, ...]}, ...]
        ;; convert it into a single list of maps [{...}, {...}, ...]
        archived-files (mapv #(into {} (:content %)) arc)

        ;; the addon title pulled from the page metadata may differ from title pulled from the api.
        ;; leading underscores are removed leaving whitespace for one example (22503)
        title (select html-snippet [[:meta (html/pred #(-> % :attrs :property (= "og:title")))]])
        title (-> title first :attrs :content trim)

        category-set (set (select categories [:a html/text]))
        tag-set (tags/category-set-to-tag-set :wowinterface category-set)

        game-track-set (if (contains? tag-set :the-burning-crusade-classic)
                         (conj game-track-set :classic-tbc)
                         game-track-set)

        description  (-> html-snippet
                         (select [:div.postmessage])
                         first
                         (html/at [:*] html/text)
                         first
                         clojure.string/split-lines)
        description (->> description
                         (remove utils/pure-non-alpha-numeric?)
                         ;;(remove clojure.string/blank?) ;; covered by above test
                         vec)

        ;; todo: attach game tracks from game-track-set
        ;; ...

        struct
        {:source :wowinterface
         :source-id source-id
         :filename "web--detail.json"

         :name (utils/slugify title)
         :game-track-set game-track-set
         :updated-date updated-date
         :created-date (some-> dt-created :content first (swallow "unknown") format-wowinterface-dt)
         :tag-set tag-set
         :short-description (first description)
         :latest-release-set latest-release-set
         :wowi/description description
         :wowi/title title
         :wowi/url (:url downloaded-item)
         :wowi/compatibility compatibility
         ;; we don't really need these
         :wowi/updated-date (some-> dt-updated :content first)
         :wowi/created-date (some-> dt-created :content first (swallow "unknown"))
         :wowi/downloads (some-> num-downloads :content first (clojure.string/replace #"," "") utils/str-to-int)
         :wowi/favorites (some-> num-favourites :content first (clojure.string/replace #"," "") utils/str-to-int)
         :wowi/checksum (some-> md5 :content first :attrs :value)
         :wowi/category-set category-set
         :wowi/latest-release-versions version-strings
         :wowi/compatible-with compatible-with-string
         :wowi/latest-release-list latest-release-list
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
        extractor (fn [snippet]
                    (try
                      (let [category (:label downloaded-item)
                            category-set #{category}

                            tag-set (tags/category-set-to-tag-set :wowinterface category-set)
                            game-track-set (if (contains? tag-set :the-burning-crusade-classic) #{:classic-tbc} #{})

                            extract-updated-date #(-> % (subs 8) trim) ;; "Updated 09-07-18 01:27 PM " => "09-07-18 01:27 PM"
                            anchor (-> snippet (select [[:a (html/attr-contains :href "fileinfo")]]) first)
                            title (-> anchor :content first trim)
                            updated-date (-> snippet (select [:div.updated html/content]) first extract-updated-date)]

                        {:source :wowinterface
                         :source-id (extract-source-id anchor)
                         :filename (str "listing--" (utils/slugify category) ".json")

                         :title title
                         :name (-> title utils/slugify)
                         :updated-date (format-wowinterface-dt updated-date)
                         :tag-set tag-set
                         :game-track-set game-track-set
                         :wowi/url (web-addon-url (extract-source-id anchor))
                         :wowi/title title ;; I've seen underscores and truncation here, do not use if you can avoid it
                         ;; favourites? author? we can source these reliably from the API
                         :wowi/updated-date updated-date
                         :wowi/downloads (-> snippet (select [:div.downloads html/content]) first (clojure.string/replace #"\D*" "") utils/to-int)
                         :wowi/category-set category-set})

                      (catch RuntimeException re
                        (core/error* re (format "failed to scrape snippet, excluding from results: %s" (.getMessage re)) :payload snippet))))

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
                                  :filename "api--filelist.json"
                                  :updated-date (some-> addon :wowi/lastUpdate utils/unix-time-to-dtstr)

                                  :name (utils/slugify (:wowi/title addon))
                                  :api-url (api-addon-url (:wowi/id addon))
                                  :web-url (web-addon-url (:wowi/id addon))
                                  :game-track-set (->> addon :wowi/gameVersions (map utils/game-version-to-game-track) set)})))

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

        removals [:version ;; single value for the 'latest' version. misleading when there are multiple latest versions.
                  :pendingUpdate ;; I can guess it's purpose but it's not useful to us
                  :addons ;; always null
                  :categoryId ;; the '40' in 'https://www.wowinterface.com/downloads/index.php?cid=40'. addons may belong to many categories however...?
                  ]
        addon (apply dissoc addon removals)

        addon (utils/prefix-keys addon "wowi")
        updates {:source :wowinterface
                 :source-id (:wowi/id addon)
                 :filename "api--detail.json"
                 :name (utils/slugify (:wowi/title addon))
                 ;; disabled, prefer the html description with elements stripped to the description with bb-code preserved
                 ;;:short-description (some-> addon :wowi/description clojure.string/split-lines first)
                 ;; the api doesn't list the *other* latest downloads unfortunately.
                 ;; rely on scraping the html for a complete list of releases.
                 ;;:latest-release-set #{
                 ;;                      {:wowi/name (:wowi/fileName addon)
                 ;;                       ;; nfi what 'd' is, it's not neccesary though.
                 ;;                       :wowi/download-url (utils/strip-url-param (:wowi/downloadUri addon) :d)
                 ;;                       :wowi/version (:wowi/version addon)
                 ;;                       :wowi/author (:wowi/author addon)
                 ;;                       :date (some-> addon :wowi/lastUpdate utils/unix-time-to-dtstr)
                 ;;                       ;; :wowi/size ... ;; available from the html addon detail page, but not scraped yet. unused anyway
                 }]
    {:parsed [(merge addon updates)]}))

;;

(defmethod core/parse-content "api.mmoui.com"
  [downloaded-item]
  (cond
    (file-list? (:url downloaded-item)) (parse-api-file-list downloaded-item)
    :else (parse-api-addon-detail downloaded-item)))

(defn dead-page?
  "returns true if there is a section like 'Message: Removed per author's request'"
  [html-snippet]
  (boolean
   (some-> html-snippet (html/select [:td.tcat html/content]) first (= "Message"))))

(defmethod core/parse-content "www.wowinterface.com"
  [downloaded-item]
  ;; figure out what sort of item we have to parse.
  ;; we can't rely on the url to know if we have a 'category' page, like the index page or the 'standalone addons' pages.
  ;; we also can't rely on a link in the category page going to a listing page - it may go to another category page.
  ;; ('index' -> 'standalone addons', 'index' -> 'class & role specific')
  ;; so we need to parse the content and look at the structure.
  (try
    (cond
      (category-group-page? (:url downloaded-item)) (parse-category-group-page (to-html downloaded-item))
      (category-listing-page? (:url downloaded-item)) (parse-category-listing downloaded-item)
      :else (parse-addon-detail-page downloaded-item))
    (catch NullPointerException exc
      ;; there are maybe a dozen of these pages, no need to always check for it
      (if-not (dead-page? (to-html downloaded-item))
        (throw exc)))))

;; --- catalogue wrangling

(defn-spec addon-data-list-cmp int?
  "sort-by fn for `addon-data`.
  'listing--*' at the very bottom, 'web--*' next, 'api--*' last"
  [addon-data :addon/part]
  (let [key (some-> addon-data :filename (subs 0 3))]
    (case key
      "lis" -1
      "web" 0
      "api" 1)))

(defn-spec sort-filter-merge-addon-data-list map?
  "returns a map of addon data sorted and merged from the given `addon-data-list`"
  [addon-data-list (s/coll-of :addon/part)]
  (let [;; removes any files like `detail.json` without a `:filename` from being considered
        addon-data-list (remove (comp nil? :filename) addon-data-list)
        addon-data-list (sort-by addon-data-list-cmp addon-data-list)
        addon-data (reduce core/merge-addon-data {} addon-data-list)]
    addon-data))

(defn-spec -to-addon-summary map?
  [addon-data-list (s/coll-of :addon/part)]
  (let [addon-data (sort-filter-merge-addon-data-list addon-data-list)

        addon-data
        (select-keys addon-data [:source
                                 :source-id
                                 :game-track-set
                                 :name
                                 :short-description
                                 :tag-set
                                 :updated-date
                                 :created-date
                                 :wowi/title
                                 :wowi/url
                                 :wowi/downloads])

        rename-map {:downloads :download-count
                    :game-track-set :game-track-list
                    :tag-set :tag-list
                    :title :label
                    :short-description :description}

        addon-data (-> addon-data
                       utils/remove-key-ns
                       (rename-keys rename-map)
                       ;; see `catalogue/format-catalogue-data-for-output`
                       ;;(update :tag-list (comp vec sort))
                       ;;(update :game-track-list (comp vec sort))
                       )]
    addon-data))

(defmethod core/to-addon-summary :wowinterface
  [addon-data-list]
  (if-not (some #{"api--detail.json"} (mapv :filename addon-data-list))
    (warn (format "failed to find API detail, excluding: %s (%s)" (:source-id (first addon-data-list)) (:source (first addon-data-list))))
    (when-let [addon-data (-to-addon-summary addon-data-list)]
      (if (s/valid? :addon/summary addon-data)
        addon-data
        (warn (format "failed to coerce addon data to a valid :addon/summary, excluding: %s (%s)" (:source-id addon-data) (:source addon-data)))))))

;;

(defn -to-addon-detail
  "generates detailed data from the sum of known data about an addon."
  [addon-data-list]
  (let [addon-data (sort-filter-merge-addon-data-list addon-data-list)

        addon-data
        (select-keys addon-data [:latest-release-set
                                 :wowi/archived-files])

        addon-data (merge addon-data
                          (-to-addon-summary addon-data-list))

        rename-map {:latest-release-set :latest-release-list
                    :wowi/archived-files :previous-release-list}

        addon-data (-> addon-data
                       (rename-keys rename-map)
                       ;; todo: opportunity here to convert latest-release-set to a map keyed by game track
                       )

        release-rename-map {:wowi/name :release-label
                            :wowi/download-url :download-url
                            :wowi/version :version}
        coerce-release (fn [release-list]
                         (mapv #(rename-keys % release-rename-map) release-list))

        ;; adds game tracks to a previous release. a release is multiplied by the number of game tracks.
        ;; this sucks but we have no way of knowing what game track a previous release
        ;; supported unless we peek inside the zip file itself. next best thing is assume
        ;; all previous releases of the addon support the declared/detected game tracks.
        ;; obviously that isn't accurate but better too loose than too strict
        add-game-tracks (fn [release-list]
                          (vec
                           (flatten
                            (for [game-track (:game-track-list addon-data)]
                              (mapv (fn [release] (assoc release :game-track game-track)) release-list)))))

        addon-data (-> addon-data
                       (update :latest-release-list coerce-release)
                       (update :previous-release-list coerce-release)
                       (update :previous-release-list add-game-tracks))]

    addon-data))

(defmethod core/to-addon-detail :wowinterface
  [addon-data-list]
  (when-let [addon-data (-to-addon-detail addon-data-list)]
    (if (s/valid? :addon/detail addon-data)
      addon-data
      (do (warn (format "failed to coerce addon data to a valid :addon/detail, excluding: %s (%s)" (:source-id addon-data) (:source addon-data)))
          (s/explain :addon/detail addon-data)))))

(defn build-catalogue
  "reads addon data from the state directory, parses it, returns a list of wowinterface addons."
  []
  (let [file-list
        (->> (core/state-paths-matching "wowinterface/*/*.json")
             (group-by (comp str fs/base-name fs/parent))) ;; {"1234" [/path/to/state/1234/listing--combat-mods, ...], ...}

        parse-file (fn [[_ path-list]]
                     (try
                       (->> path-list
                            (map core/read-addon-path)
                            core/to-addon-summary)
                       (catch Exception e
                         (error (format "failed to convert addon data to a catalogue addon: %s" path-list))
                         (throw e))))
        addon-list (remove nil? (pmap parse-file file-list))]
    addon-list))

