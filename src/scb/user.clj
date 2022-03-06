(ns scb.user
  (:require
   [scb.core :as core]))

(comment "user interface to catalogue builder")

(defn download-url
  "adds a url to the queue to be downloaded."
  [url serialisation]
  (core/put-item (core/get-state :download-queue) [url serialisation])
  nil)
