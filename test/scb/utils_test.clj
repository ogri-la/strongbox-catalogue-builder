(ns scb.utils-test
  (:require
   ;;[taoensso.timbre :as timbre :refer [debug info warn error spy]]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [scb.helper :as helper :refer [fixture-path]]
   [scb
    [utils :as utils]]))

(deftest deep-merge
  (let [cases [[{} {} {}]
               [{} nil {}]
               [nil {} {}]

               ;; 'b' will replace 'a'
               [:foo :bar :bar]

               ;; maps are supported
               [{:foo :bar} {:foo :baz} {:foo :baz}]

               ;; sets are supported
               [#{:foo} #{:bar} #{:foo :bar}]
               [nil #{:foo} #{:foo}]
               [#{:foo} nil #{:foo}]

               ;; deeply nested structures are supported
               [{:foo {:bar {:baz #{:a}}}}
                {:foo {:bar {:baz #{:b}}}}
                {:foo {:bar {:baz #{:a :b}}}}]]]

    (doseq [[a b expected] cases]
      (is (= expected (utils/deep-merge a b))))))

(deftest pure-non-alpha-numeric?
  (let [cases [;; 'empty'
               ["" true]

               ;; 'blank'
               [" " true]
               ["   " true]

               ["-" true]
               ["-a" false]
               ["a" false]

               ;; actual cases
               ["-------------------------------------" true]
               ["#---------------------------------------#" true]
               ["~~~~~~~~~~~~~~~~~~~~" true]
               ["____________________________________" true]
               ["-------------------- Summary --------------------" false]]]

    (doseq [[given expected] cases]
      (is (= expected (utils/pure-non-alpha-numeric? given))))))
