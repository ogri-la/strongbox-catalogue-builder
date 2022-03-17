(ns scb.core-test
  (:require
   ;;[taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [clj-http.fake :refer [with-global-fake-routes-in-isolation]]
   [scb.helper :as helper]
   [scb.core :as core]))

(use-fixtures :once helper/no-http)
(use-fixtures :each helper/temp-state-dir)

(deftest download-queue
  (testing ""
    (let [url "https://wowinterface.com/addons.php"
          fixture (helper/fixture-path "wowinterface--landing.html")
          fake-routes {url {:get (fn [req] {:status 200 :body (slurp fixture)})}}]
      (with-global-fake-routes-in-isolation fake-routes
        (helper/with-running-app
          (core/put-item (core/get-state :download-queue) url)
          (Thread/sleep 200) ;; 100 is typically enough, but is slower on a first run
          (is (nil? (.peek (core/get-state :error-queue))))
          (is (nil? (.peek (core/get-state :download-queue))))
          (is (nil? (.peek (core/get-state :downloaded-content-queue))))
          (is (not (nil? (.peek (core/get-state :parsed-content-queue))))))))))

(deftest download-queue--bad-item
  (testing "receiving a bad item from the download queue doesn't cause a crash"
    (helper/with-running-app
      (core/put-item (core/get-state :download-queue) "foo!")
      (Thread/sleep 150)
      (is (nil? (.peek (core/get-state :download-queue))))
      (is (nil? (.peek (core/get-state :downloaded-content-queue))))
      (is (nil? (.peek (core/get-state :parsed-content-queue))))

      (is (= {:error "failed to download url 'foo!': no protocol: foo!"}
             (dissoc (.peek (core/get-state :error-queue)) :exc))))))
