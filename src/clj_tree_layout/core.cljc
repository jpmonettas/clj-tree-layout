(ns clj-tree-layout.core
  (:require [clj-tree-layout.core-specs]
            [clojure.string :as str]))

(defn annotate [tree]
  (let [x-numbers (atom {})
        aux (fn aux [t depth]
              (-> t
                  (assoc :depth depth
                         :depth-order (-> (swap! x-numbers update depth (fnil inc 0))
                                          (get depth)))
                  (update :childs (fn [chlds]
                                    (mapv (fn [c]
                                             (aux c (inc depth)))
                                           chlds)))))]
    (aux tree 0)))

(defn tree-contours [t]
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

(defn max-conflict [normal-path conflictive-path]
  (let [conflicts (->> (map vector normal-path conflictive-path)
                       (filter (fn [[n c]] (< c n)))
                       (map (fn [[n c]] (- n c))))]
    (if-not (empty? conflicts)
      (apply max conflicts)
      0)))

(defn push-tree-left [t delta]
  (-> t
      (update :x #(+ % delta))
      (update :childs (fn [chlds] (mapv #(push-tree-left % delta) chlds)))))

(defn tilford-raingold [{:keys [width depth childs] :as node} h-gap]
  (if (not-empty childs)
    (let [layout-childs (mapv #(tilford-raingold % h-gap) childs)
          pushed-childs (loop [pusheds [(first layout-childs)]
                               [c & r] (rest layout-childs)]
                          (if c
                            (let [right-contour (:right (tree-contours (assoc node :x 0 :width 0 :childs pusheds)))
                                  left-contour (:left (tree-contours (assoc node :x 0 :width 0 :childs [c])))
                                  delta (+ (max-conflict right-contour left-contour) h-gap)]
                              (recur (conj pusheds (push-tree-left c delta)) r))
                            pusheds))
          firstc (first pushed-childs)
          lastc (last pushed-childs)
          childs-width (- (+ (:x lastc) (:width lastc)) (:x firstc))]
      (assoc node
             :x (float (- (+ (:x firstc) (/ childs-width 2)) (/ width 2)))
             :childs pushed-childs))
    (assoc node :x 0.0)))

(defn layers-heights [t]
  (->> t
       (tree-seq :childs :childs)
       (group-by :depth)
       (map (fn [[d nodes]]
              [d (apply max (map :height nodes))]))
       (into {})))

(defn add-ys
  ([t layer-height v-gap] (add-ys t layer-height v-gap 0))
  ([t layer-height v-gap y]
   (let [lh (layer-height (:depth t))]
    (-> t
        (assoc :y (float y))
        (update :childs (fn [childs]
                          (->> childs
                               (mapv #(add-ys % layer-height v-gap (+ y lh v-gap))))))))))

(defn build-internal-tree [t sizes childs-fn id-fn branch-fn]
  (let [[width height] (get sizes (id-fn t) [10 10])]
    {:node-id (id-fn t)
     :width width
     :height height
     :childs (mapv #(build-internal-tree % sizes childs-fn id-fn branch-fn)
                   (childs-fn t))}))

(defn ensure-all-positive [t h-gap]
  (let [left-contour (:left (tree-contours t))
        delta (+ (max-conflict (repeat 0) left-contour) h-gap)
        pushed (push-tree-left t delta)]
    pushed))

(defn dimensions [t]
  (reduce (fn [r n]
            (assoc r (:node-id n) (select-keys n [:x :y :width :height])))
          {} (tree-seq :childs :childs t)))

(defn layout-tree [t {:keys [sizes childs-fn id-fn branch-fn h-gap v-gap]
                      :or {h-gap 5 v-gap 5}}]
  (let [internal-tree (-> t
                          (build-internal-tree sizes childs-fn id-fn branch-fn)
                          annotate)
        layers-heights (layers-heights internal-tree)]
   (-> internal-tree
       (add-ys layers-heights v-gap)
       (tilford-raingold h-gap)
       (ensure-all-positive h-gap)
       dimensions)))

(comment

  (def tree {:id 1
             :lable "1"
             :childs [{:id 2
                       :label "2"
                       :childs [{:id 12
                                 :label "12"}]}
                      {:id 3
                       :label "3"}]})

  (layout-tree tree
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


  {"3"                                {:x 45, :y 30, :width 10, :height 10},
   "(inc 25)"                         {:x 127.5, :y 15, :width 10, :height 10},
   "4"                                {:x 60, :y 30, :width 10, :height 10},
   "(+ 1 2 (- 3 4) (/ 5 6) (inc 25))" {:x 63.75, :y 0, :width 10, :height 10}                              ,
   "(- 3 4)"                          {:x 45, :y 15, :width 10, :height 10},
   "/"                                {:x 75, :y 30, :width 10, :height 10},
   "-"                                {:x 30, :y 30, :width 10, :height 10},
   "25"                               {:x 135, :y 30, :width 10, :height 10},
   "5"                                {:x 90, :y 30, :width 10, :height 10},
   "inc"                              {:x 120, :y 30, :width 10, :height 10},
   "6"                                {:x 105, :y 30, :width 10, :height 10},
   "1"                                {:x 15, :y 15, :width 10, :height 10},
   "(/ 5 6)"                          {:x 90, :y 15, :width 10, :height 10},
   "2"                                {:x 30, :y 15, :width 10, :height 10},
   "+"                                {:x 0, :y 15, :width 10, :height 10}})
