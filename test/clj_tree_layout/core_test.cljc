(ns clj-tree-layout.core-test
  (:require [clj-tree-layout.core :refer [layout-tree]]
            [clojure.test :refer [deftest is testing run-tests]]
            [clojure.spec.test.alpha :as stest]
            [expound.alpha :as ex]
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [clojure.spec.gen.alpha :as sgen]
            [clj-tree-layout.core-specs :refer :all]))

(stest/instrument ['clj-tree-layout.core/layout-tree])
(alter-var-root #'s/*explain-out* (constantly ex/printer))

(deftest layout-tree-generative-test
  (let [{:keys [total check-passed]} (stest/summarize-results (stest/check `layout-tree))]
    (is (= total check-passed))))
