# clj-tree-layout

A library for laying out tree nodes in 2D space for Clojure and ClojureScript.

It features tidy tree representations as per [Tilford and Reingold](http://hci.stanford.edu/cs448b/f09/lectures/CS448B-20091021-GraphsAndTrees.pdf)

This library doesn't contain any functionality to draw a tree on any canvas, just
the calculations needed so you can draw it however you want.

For libraries that uses this for drawing check :

- [reagent-flowgraph](https://github.com/jpmonettas/reagent-flowgraph) A reagent component for laying out tree nodes in 2D space.

## Installation

[![Clojars Project](https://img.shields.io/clojars/v/clj-tree-layout.svg)](https://clojars.org/clj-tree-layout)

## Usage

First

```clojure
(require '[clj-tree-layout.core :refer [layout-tree]])
```

Given a tree structure

```clojure

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
              :id-fn     :id})

;;=>
;; {1  {:x 7.5, :y 0,  :width 10, :height 10},
;;  2  {:x 0,   :y 15, :width 10, :height 10},
;;  12 {:x 0,   :y 30, :width 10, :height 10},
;;  3  {:x 15,  :y 15, :width 10, :height 10}}

```

You can use the returning information to draw the tree however you want.

No problem if you have a different tree structure

```clojure
(def sexp-tree '(+ 1 2 (- 4 2) (/ 123 3) (inc 25)))

(layout-tree sexp-tree
             {:branch-fn #(when (seq? %) %)
              :childs-fn #(when (seq? %) %)
              :id-fn     str})

;;=>
;; {"3"                                {:x 45,    :y 30, :width 10, :height 10},
;;  "(inc 25)"                         {:x 127.5, :y 15, :width 10, :height 10},
;;  "4"                                {:x 60,    :y 30, :width 10, :height 10},
;;  "(+ 1 2 (- 3 4) (/ 5 6) (inc 25))" {:x 63.75, :y 0,  :width 10, :height 10}                              ,
;;  "(- 3 4)"                          {:x 45,    :y 15, :width 10, :height 10},
;;  "/"                                {:x 75,    :y 30, :width 10, :height 10},
;;  "-"                                {:x 30,    :y 30, :width 10, :height 10},
;;  "25"                               {:x 135,   :y 30, :width 10, :height 10},
;;  "5"                                {:x 90,    :y 30, :width 10, :height 10},
;;  "inc"                              {:x 120,   :y 30, :width 10, :height 10},
;;  "6"                                {:x 105,   :y 30, :width 10, :height 10},
;;  "1"                                {:x 15,    :y 15, :width 10, :height 10},
;;  "(/ 5 6)"                          {:x 90,    :y 15, :width 10, :height 10},
;;  "2"                                {:x 30,    :y 15, :width 10, :height 10},
;;  "+"                                {:x 0,     :y 15, :width 10, :height 10}}

```

You can use that information to draw something like

<img src="/doc/images/sexp-tree.png?raw=true"/>

## Options

#### :sizes

Is a map from node id to [width height]. Can be used to give `layout-tree` information about
node sizes.

#### :branch-fn

Is a fn that, given a node, returns true if can have
children, even if it currently doesn't.

#### :childs-fn

Is a fn that, given a branch node, returns a seq of its
children.

#### :id-fn

Is a fn that, given a node, returns anything that can be used as
a node uniq id. Is used to return the result.

#### :h-gap

An integer used as horizontal gap between nodes.

#### :v-gap

An integer used as vertical gap between nodes.
