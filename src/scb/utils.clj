(ns scb.utils
  (:require
   [slugify.core :as sluglib]
   [flatland.ordered.map :as omap]
   [taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [me.raynes.fs :as fs]
   [clojure.spec.alpha :as s]
   [clojure.data.json]
   [orchestra.spec.test :as st]
   [clj-http.client]
   [orchestra.core :refer [defn-spec]]
   [clojure.pprint]
   [clojure.string]
   [java-time :as jt]
   [java-time.format]
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

(defn-spec transform-keys map?
  "applies given `f` to the keys in `m`"
  [m map?, f fn?]
  (into {} (map (fn [[k v]] [(f k) v]) m)))

(defn remove-key-ns
  [m]
  (transform-keys m (fn [key] (-> key name keyword))))

(defn prefix-keys
  [m prefix]
  (transform-keys m (fn [key] (->> key name (str prefix "/") keyword))))

(defn keyword-name
  "like `(name :foo/bar)` but preserves namespaces.
  the reverse `(keyword \"foo/bar\")` will preserve the namespace."
  [kw]
  (str (.-sym ^clojure.lang.Keyword kw)))

(defn from-json
  [x & [opts]]
  (let [key-fn (get opts :key-fn keyword)
        val-fn (get opts :value-fn (fn [key val] val))]
    (some-> x (clojure.data.json/read-str :key-fn key-fn :value-fn val-fn))))

(defn json-slurp
  [path & [opts]]
  (when (fs/exists? path)
    (locking (.intern ^String path)
      (-> path slurp (from-json opts)))))

(defn-spec to-json string?
  [x any?]
  (with-out-str (clojure.data.json/pprint x :escape-slash false, :key-fn keyword-name)))

(defn json-spit
  [data path]
  (locking (.intern ^String path)
    (->> data to-json (spit path))))

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

(defn-spec dump-json-file ::sp/extant-file
  [path ::sp/file, data ::sp/anything]
  (spit path (to-json data))
  path)

(defn datestamp-now-ymd
  []
  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd") (java.util.Date.)))

(defn-spec drop-nils (s/or :ok map?, :empty nil?)
  "given a map `m` and a set of `fields`, if field is `nil`, `dissoc` it"
  [m map?, fields sequential?]
  (if (empty? fields)
    m
    (drop-nils
     (if (nil? (get m (first fields)))
       (dissoc m (first fields))
       m)
     (rest fields))))

(defn-spec slugify string?
  [string string?]
  (sluglib/slugify string))

;; https://gist.github.com/danielpcox/c70a8aa2c36766200a95#gistcomment-2759496-permalink
(defn deep-merge
  "merges `b` into `a` when `a` is a map, otherwise returns `b`.
  other collections are not considered."
  [a b]
  (cond
    (map? a) (into a (for [[k v] b]
                       [k (deep-merge (a k) v)]))

    (set? a) (into a b)

    :else b))

(defn-spec pure-non-alpha-numeric? boolean?
  "returns `true` if given string `s` contains no alpha-numeric characters (including underscores)."
  [s string?]
  (not (nil? (re-find (re-matcher #"^[\W_]*$" s)))))

(defn-spec todt ::sp/zoned-dt-obj
  "takes an ISO8901 string and returns a java.time.ZonedDateTime object. 
  these are needed to calculate durations"
  [dt ::sp/inst]
  (java-time/zoned-date-time (get java-time.format/predefined-formatters "iso-zoned-date-time") dt))

(defn-spec dt-before? boolean?
  "returns `true` if `date-1` happened before `date-2`"
  [date-1 ::sp/inst, date-2 ::sp/inst]
  (jt/before? (todt date-1) (todt date-2)))

(def release-of-wow-classic
  "the date wow classic beta was made available to the public.
  - https://wowpedia.fandom.com/wiki/World_of_Warcraft:_Classic#Notes_and_trivia
  - https://worldofwarcraft.com/en-us/news/22990080/mark-your-calendars-wow-classic-launch-and-testing-schedule"
  "2019-05-15T00:00:00Z")

(defn-spec before-classic? boolean?
  [dt ::sp/inst]
  (dt-before? dt release-of-wow-classic))

(defn-spec less-than-n-days-old? boolean?
  "returns `true` if given `dt` is *less* than `n` days old."
  [n pos-int?, dt ::sp/inst]
  (let [n-days-ago (str (jt/minus (jt/instant) (jt/days n)))]
    (jt/before? (todt n-days-ago) (todt dt))))

(defn-spec unix-time-to-dtstr ::sp/inst
  [unix-time pos-int?]
  (-> unix-time jt/instant str))

(defn-spec guess-game-track (s/nilable ::sp/game-track)
  "returns the first game track it finds in the given string, preferring `:classic-tbc`, then `:classic`, then `:retail` (most to least specific).
  returns `nil` if no game track found."
  [string (s/nilable string?)]
  (when string
    (let [;; matches 'classic-tbc', 'classic-bc', 'classic-bcc', 'classic_tbc', 'classic_bc', 'classic_bcc', 'tbc', 'tbcc', 'bc', 'bcc'
          ;; but not 'classictbc' or 'classicbc' or 'classicbcc'
          ;; see tests.
          classic-tbc-regex #"(?i)classic[\W_]t?bcc?|[\W_]t?bcc?\W?|t?bcc?$"
          classic-regex #"(?i)classic|vanilla"
          retail-regex #"(?i)retail|mainline"]
      (cond
        (re-find classic-tbc-regex string) :classic-tbc
        (re-find classic-regex string) :classic
        (re-find retail-regex string) :retail))))

(defn-spec safe-delete-file boolean?
  "deletes a *file* only, but only if it exists and is rooted in the given `rooted-in` directory."
  [path ::sp/extant-file, rooted-in ::sp/extant-dir]
  (if (and (clojure.string/starts-with? path rooted-in)
           (fs/file? path))
    (fs/delete path)
    false))

