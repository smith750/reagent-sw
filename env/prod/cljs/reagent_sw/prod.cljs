(ns reagent-sw.prod
  (:require [reagent-sw.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
