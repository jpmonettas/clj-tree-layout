(ns clj-tree-layout.core
  (:require [clj-tree-layout.core-specs]
            [clojure.string :as str]))

(defn- contours
  "Given a normalized tree returns a map with its right and left contours.
  Contours are returned as a sequence of x coordinates from the root."
  [t]
  (->> (tree-seq :childs :childs t)
       (group-by :depth)
       (map (fn [[l nodes]]
              (let [fnode (apply (partial min-key :depth-order) nodes)
                    lnode (apply (partial max-key :depth-order) nodes)]
                [l (:x fnode) (+ (:x lnode) (:width lnode))])))
       (sort-by first)
       (reduce (fn [r [_ left right]]
                 (-> r
                     (update :left conj left)
                     (update :right conj right)))
               {:left [] :right []})))

(defn- max-conflict
  "Given two sequences representing paths, returns the biggest conflict.
  A conflict is how much you need to push the conflicting path right so conflictive path
  is 100% at the right of normal-path at every level."
  [normal-path conflictive-path]
  (let [conflicts (->> (map vector normal-path conflictive-path)
                       (filter (fn [[n c]] (< c n)))
                       (map (fn [[n c]] (- n c))))]
    (if-not (empty? conflicts)
      (apply max conflicts)
      0)))

(defn- push-tree-right
  "Given a normalized tree and a delta, push every node :x position delta
  to the right."
  [t delta]
  (-> t
      (update :x #(+ % delta))
      (update :childs (fn [chlds] (mapv #(push-tree-right % delta) chlds)))))

(defn- tilford-raingold
  "Given a normalized tree and a horizontal gap, add :x coordinates to nodes
  so the follow aesthetics rules as defined by Tilford and Raingold paper on
  tidy trees layouts."
  [{:keys [width depth childs] :as node} h-gap]
  (if (not-empty childs)
    (let [layout-childs (mapv #(tilford-raingold % h-gap) childs)
          pushed-childs (loop [pusheds [(first layout-childs)]
                               [c & r] (rest layout-childs)]
                          (if c
                            (let [right-contour (:right (contours (assoc node :x 0 :width 0 :childs pusheds)))
                                  left-contour (:left (contours (assoc node :x 0 :width 0 :childs [c])))
                                  delta (+ (max-conflict right-contour left-contour) h-gap)]
                              (recur (conj pusheds (push-tree-right c delta)) r))
                            pusheds))
          firstc (first pushed-childs)
          lastc (last pushed-childs)
          childs-width (- (+ (:x lastc) (:width lastc)) (:x firstc))]
      (assoc node
             :x (float (- (+ (:x firstc) (/ childs-width 2)) (/ width 2)))
             :childs pushed-childs))
    (assoc node :x 0.0)))

(defn- layers-heights
  "Given a normalized tree returns a map from depths to tree layer height.
  The layer height is the height of the tallest node for the layer."
  [t]
  (->> t
       (tree-seq :childs :childs)
       (group-by :depth)
       (map (fn [[d nodes]]
              [d (apply max (map :height nodes))]))
       (into {})))

(defn- add-ys
  "Given a normalized tree, add :y coordinates to every node so that
  nodes at the same layers have the same :y coordinate."
  ([t layer-height v-gap] (add-ys t layer-height v-gap 0))
  ([t layer-height v-gap y]
   (let [lh (layer-height (:depth t))]
    (-> t
        (assoc :y (float y))
        (update :childs (fn [childs]
                          (->> childs
                               (mapv #(add-ys % layer-height v-gap (+ y lh v-gap))))))))))

(defn- annotate
  "Given a normalized tree add :depth and :depth-order to every node."
  [t]
  (let [x-numbers (atom {})
        aux (fn aux [tr depth]
              (-> tr
                  (assoc :depth depth
                         :depth-order (-> (swap! x-numbers update depth (fnil inc 0))
                                          (get depth)))
                  (update :childs (fn [chlds]
                                    (mapv (fn [c]
                                             (aux c (inc depth)))
                                           chlds)))))]
    (aux t 0)))

(defn- normalize
  "Given any tree, a childs-fn, id-fn, branch-fn and sizes build a normalized tree.
  In the normalized tree every node is a map {:keys [:node-id :width :height :childs]}"
  [t sizes childs-fn id-fn branch-fn]
  (let [[width height] (get sizes (id-fn t) [10 10])]
    {:node-id (id-fn t)
     :width width
     :height height
     :childs (mapv #(normalize % sizes childs-fn id-fn branch-fn)
                   (childs-fn t))}))

(defn- ensure-all-positive
  "Given a normalized positioned tree pushes it to the right until all nodes
  :x coordinate is positive (> 0)."
  [t h-gap]
  (let [left-contour (:left (contours t))
        delta (+ (max-conflict (repeat 0) left-contour) h-gap)
        pushed (push-tree-right t delta)]
    pushed))

(defn- dimensions
  "Given a normalized positioned tree returns a map from :node-id
  to {:x :y :width :height} for every node."
  [t]
  (reduce (fn [r n]
            (assoc r (:node-id n) (select-keys n [:x :y :width :height])))
          {} (tree-seq :childs :childs t)))

(defn layout-tree
  "Given any tree and a map of directives, returns a map from node ids to
  {:x :y :width :height} for every node.
  Directives:

     :sizes a map from node ids to [width height]
     :childs-fn a fn that, given a branch node, returns a seq of its children.
     :id-fn a fn that, given a node, returns anything that can be used as a node uniq id.
     :branch-fn a fn that, given a node, returns true if can have children, even if it currently doesn't.
     :h-gap an integer used as horizontal gap between nodes.
     :v-gap an integer used as vertical gap between nodes."

  [t {:keys [sizes childs-fn id-fn branch-fn h-gap v-gap]
                      :or {h-gap 5 v-gap 5}}]
  (let [internal-tree (-> t
                          (normalize sizes childs-fn id-fn branch-fn)
                          annotate)
        layers-heights (layers-heights internal-tree)]
   (-> internal-tree
       (add-ys layers-heights v-gap)
       (tilford-raingold h-gap)
       (ensure-all-positive h-gap)
       dimensions)))

(comment


  (layout-tree {:id 1
                :lable "1"
                :childs [{:id 2
                          :label "2"
                          :childs [{:id 12
                                    :label "12"}]}
                         {:id 3
                          :label "3"}]}
               {:branch-fn :childs
                :childs-fn :childs
                :id-fn :id})

  (layout-tree '(1 2 3)
               {:branch-fn #(when (seq? %) %)
                :childs-fn #(when (seq? %) %)
                :id-fn str
                :sizes {"(1 2 3)" , [43 18]
                        "1" , [8 18]
                        "2" , [8 18]
                        "3" [8 18]}})
  )
