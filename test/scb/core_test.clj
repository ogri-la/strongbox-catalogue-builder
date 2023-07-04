(ns scb.core-test
  (:require
   ;;[taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [clojure.test :refer [deftest is testing use-fixtures]]
   ;;[clj-http.fake :refer [with-global-fake-routes-in-isolation]]
   [scb.helper :as helper]
   [scb
    [user :as user]
    [core :as core]]))

(use-fixtures :each helper/no-http)
(use-fixtures :each helper/temp-state-dir)

;; 2022-09-11: disabled, adding the landing page now finds all category landing pages and adds them too, blowing out the fake routes
#_(deftest download-queue
    (testing "general test of adding urls to be downloaded, parsed, stored"
      (let [url "https://www.wowinterface.com/addons.php"
            fixture (helper/fixture-path "wowinterface--landing.html")
            fake-routes {url {:get (fn [req] {:status 200 :body (slurp fixture)})}}]
        (with-global-fake-routes-in-isolation fake-routes
          (helper/with-running-app
            (core/cleanup) ;; stops any workers
            (core/run-worker core/download-worker)
            (core/put-item (core/get-state :download-queue) url)

            (Thread/sleep 250) ;; 100 is typically enough, but is slower on a first run

            (is (core/empty-queue? :download-queue))
            (is (not (core/empty-queue? :downloaded-content-queue)))

          ;; don't run a parser-worker, it will parse the content and add new work to download and parse.
          ;; the fake routes will fail, etc.
          ;; instead, parse content manually.

            (let [[item _] (core/take-item (core/get-state :downloaded-content-queue))
                  parsed-content (core/parse-content item)
                  to-download (:download parsed-content)

                  expected 47 ;; category pages to download
                  expected-first-url
                  {:label "The Burning Crusade Classic", :url "https://www.wowinterface.com/downloads/index.php?cid=161&sb=dec_date&so=desc&pt=f&page=1"}
                  expected-last-url
                  {:label "Discontinued and Outdated Mods", :url "https://www.wowinterface.com/downloads/index.php?cid=44&sb=dec_date&so=desc&pt=f&page=1"}]

              (is (= expected (count to-download)))
              (is (= expected-first-url (first to-download)))
              (is (= expected-last-url (last to-download)))))))))

(deftest download-queue--bad-item
  (testing "receiving a bad item from the download queue doesn't cause a crash"
    (helper/with-running-app
      (helper/with-instrumentation-off
        (core/put-item (core/get-state :download-queue) "foo!")
        (Thread/sleep 250)  ;; 100 is typically enough, but is slower on a first run
        (is (core/empty-queue? :download-queue))
        (is (core/empty-queue? :downloaded-content-queue))
        (is (core/empty-queue? :parsed-content-queue))

        ;;(is (= {:error "failed to download url 'foo!': no protocol: foo!"}
        ;;       (dissoc (.peek (core/get-state :error-queue)) :exc))))))
        ))))
