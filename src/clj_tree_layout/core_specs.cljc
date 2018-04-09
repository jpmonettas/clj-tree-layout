(ns clj-tree-layout.core-specs
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.test.check.generators :as gen]
            [clojure.set :as set]))

(def simple-tree-gen (gen/recursive-gen
                      #(gen/hash-map :id gen/uuid :childs (gen/vector %))
                      (gen/hash-map :id gen/uuid)))

(s/def ::tree any?)

(s/def ::width (s/with-gen pos-int?
                 #(s/gen (s/int-in 0 10e5))))
(s/def ::height (s/with-gen pos-int?
                  #(s/gen (s/int-in 0 10e5))))
(s/def ::x (s/and float? #(<= 0 %)))
(s/def ::y (s/and float? #(<= 0 %)))

(s/def ::sizes (s/map-of any? (s/tuple ::width ::height)))
(s/def ::childs-fn ifn?)
(s/def ::id-fn ifn?)
(s/def ::branch-fn ifn?)
(s/def ::h-gap (s/with-gen (s/and pos-int? #(<= 2 %))
                  #(s/gen (s/int-in 2 100))))
(s/def ::v-gap (s/with-gen (s/and pos-int? #(<= 2 %))
                  #(s/gen (s/int-in 2 100))))

(s/def ::layout-tree-config (s/keys :req-un [::sizes
                                             ::childs-fn
                                             ::id-fn
                                             ::branch-fn
                                             ::h-gap
                                             ::v-gap]))


(defn collisions?
  "Returns true if there are any collisions in a seq of boxes maps with
  keys :x :y :width :height"
  [boxes-seq]
  (let [colliding? (fn [a b]
                     (and (< (:x a) (+ (:x b) (:width b)))
                          (> (+ (:x a) (:width a))  (:x b))
                          (< (:y a) (+ (:y b) (:height b)))
                          (> (+ (:y a) (:height a)) (:y b))))
        boxes-set (into #{} boxes-seq)]
    (->> (for [b boxes-set
               other (disj boxes-set b)]
           (let [c? (colliding? b other)]
             (when c? (println b other))
             c?))
         (reduce #(or %1 %2) false))))


(s/def ::positions-map (s/and (s/map-of any? (s/keys :req-un [::x ::y ::width ::height]))
                              #(not (collisions? (vals %)))))

(s/def ::layout-tree-args (s/with-gen
                            (s/cat :tree ::tree
                                   :config ::layout-tree-config)

                            ;; generate arguments with simple trees for testing
                            #(sgen/fmap (fn [[tree w-and-hs hgap vgap]]
                                          (let [tree-ids (map :id (tree-seq :childs :childs tree))]
                                            [tree {:sizes (->> (map vector (shuffle tree-ids) w-and-hs)
                                                               (into {}))
                                                   :childs-fn :childs
                                                   :id-fn :id
                                                   :branch-fn :childs
                                                   :h-gap hgap
                                                   :v-gap vgap}]))
                                        (sgen/tuple
                                         simple-tree-gen
                                         (s/gen (s/coll-of (s/tuple ::width ::height)))
                                         (s/gen ::h-gap)
                                         (s/gen ::v-gap)))))


(s/fdef clj-tree-layout.core/layout-tree
        :args ::layout-tree-args
        :ret ::positions-map)
