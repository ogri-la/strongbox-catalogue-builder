(ns scb.http
  (:require
   [clojure.spec.alpha :as s]
   [taoensso.timbre :as log :refer [debug info warn error spy]]
   [me.raynes.fs :as fs]
   [orchestra.core :refer [defn-spec]]
   [clj-http
    [core :as clj-http-core]
    ;;[conn-mgr :as conn]
    [client :as http]]
   ;;[clojure.core.cache :as cache]
   [taoensso.nippy :as nippy]
   [scb
    [utils :as utils]
    [specs :as sp]])
  (:import
   [java.io File FileInputStream FileOutputStream]
   [org.apache.commons.io IOUtils]))

(def expiry-offset-hours 999) ;; doesn't matter too much at this stage.
(def delay-between-requests 1000) ;; ms, 1sec

(defn-spec url-ext (s/or :ok string? :missing nil?)
  "returns the extension from a given `url` or `nil` if one can't be extracted."
  [url ::sp/url]
  (let [path (some-> url java.net.URL. .getPath utils/nilable)
        ext-post (when path (clojure.string/last-index-of path \.))]
    (when (and path ext-post)
      (subs path ext-post))))

(defn-spec -cache-key string?
  "safely encode a URI to something that can live cached on the filesystem."
  [url ::sp/url, request-opts map?]
  (let [;;ext ".nippy" ;;(-> url java.net.URL. .getPath (subs 1) fs/split-ext second (or ""))
        ext (str (url-ext url) ".nippy") ;; ".nippy", ".html.nippy", ".json.nippy"
        enc (java.util.Base64/getUrlEncoder)
        ;; for any query params, generates a predictable query param string
        query-params (-> request-opts
                         :query-params
                         (or {})
                         vec ;; {:foo "bar", :baz "bup"} => [[:foo "bar"] [:baz "bup"]]
                         sort ;; [[:foo "bar"] [:baz "bup"]] [[:baz "bup"] [:foo "bar"]]
                         http/generate-query-string) ;; "baz=bup&foo=bar"
        url (str url (when-not (empty? query-params)
                       "?" query-params))]
    (as-> url x
      (str x)
      (.getBytes x)
      (.encodeToString enc x)
      (str x ext))))

(defn-spec cache-key ::sp/path
  "safely encode a URI to something that can live cached on the filesystem."
  [url ::sp/url, request-opts map?, cache-root ::sp/extant-dir]
  (str (fs/file cache-root (-cache-key url request-opts))))

(defn-spec slurp-cache-file any?
  [cache-file ::sp/extant-file]
  (debug "Cache hit:" cache-file)
  (let [;; fml: https://stackoverflow.com/questions/26790881/clojure-file-to-byte-array
        ^File f (File. ^String cache-file)
        ary (byte-array (.length f))
        ^FileInputStream is (FileInputStream. f)
        _ (.read is ary)
        data (-> ary nippy/thaw)]
    (.close is)
    data))

(defn-spec spit-cache-file :http/response
  [response :http/response, cache-file ::sp/file]
  (locking cache-file
    (when (http/success? response)
      (let [^File f (java.io.File. ^String cache-file)
            ^FileOutputStream os (FileOutputStream. f)
            ^bytes data (-> response
                            (dissoc :http-client)
                            nippy/freeze)]
        (.write os data)
        (.close os)))))

(defn file-older-than
  [path offset-hours]
  (let [now-ms (inst-ms (java.time.Instant/now))
        modtime-ms (fs/mod-time path)
        seconds 1000
        minutes 60
        hours 60]
    (-> now-ms (- modtime-ms) (/ seconds) (/ minutes) (/ hours) int (> offset-hours))))

(defn cache-hit?
  [cache-key]
  (and (fs/exists? cache-key)
       (not (file-older-than cache-key expiry-offset-hours))))

(defn -download
  [url & [opts]]
  (let [;; options:
        ;; https://github.com/dakrone/clj-http/blob/1c751431a3a8d38a795a70609a60cee24ad62757/src/clj_http/core.clj#L208
        ;; http://hc.apache.org/httpcomponents-client-ga/httpclient-cache/apidocs/org/apache/http/impl/client/cache/CacheConfig.Builder.html#setMaxObjectSize(long)
        cache-config (clj-http-core/build-cache-config
                      {:cache-config {:max-object-size 4194304 ;; bytes
                                      :max-cache-entries 100

                                      :heuristic-caching-enabled true
                                      :heuristic-coefficient 0.1 ;; 10% https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html
                                      :heuristic-default-lifetime 86400 ;; seconds
                                      }})

        config {:trace-redirects true
                :cache true
                :cache-config cache-config}

        ;; clj-http options that can be passed through to the request, if they exist
        config (merge config (select-keys opts [:as :http-client :query-params]))]
    (info "downloading" url) ;; "with opts" config)
    (http/get url config)))

(defn download
  "downloads the given `url` and returns the http response.
  use `download-file` to download a large/non-textual file.
  accepts a map with the keys: `cache-root`, default is the system temp dir. pass `nil` or use `-download` directly to skip caching."
  [url & [{:keys [cache-root, delay], :or {cache-root :temp-dir, delay delay-between-requests}  :as opts}]]
  (let [;; lets us rebind utils/temp-dir during tests
        cache-root (if (= cache-root :temp-dir)
                     (utils/temp-dir)
                     cache-root)]
    (if-not cache-root
      ;; no place to store file, skip file based cache
      (-download url opts)
      (let [cache-file (cache-key url opts cache-root)]
        (if (cache-hit? cache-file)
          (slurp-cache-file cache-file)
          (let [_ (debug (format "cache miss \"%s\": %s" url cache-file))
                resp (-download url opts)]
            (future
              (spit-cache-file resp cache-file))

            ;; todo: this delay needs to become host-specific
            (warn "sleeping for" delay)
            (Thread/sleep delay)

            resp))))))

(defn download-file
  [url]
  nil)

(defn http-error?
  "returns `true` if the http response code is either a client error (4xx) or a server error (5xx)"
  [http-resp]
  (and (map? http-resp)
       (> (get http-resp :status -1) 399)))

(defn sink-error
  "given a http response, if response was unsuccessful, emit warning/error message and return nil,
  else return response."
  [http-resp]
  (if-not (http-error? http-resp)
    ;; no error, pass response through
    http-resp
    ;; otherwise, scream and yell and return `nil`:
    ;;  'HTTP 403 https://github.com/foo/bar "access denied"'
    (error (format "HTTP %s %s \"%s\"" (:status http-resp) (:host http-resp) (:reason-phrase http-resp)))))
