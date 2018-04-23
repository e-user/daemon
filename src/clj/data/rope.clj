(ns data.rope
  "Implementation of Ropes as per \"Ropes: an Alternative to Strings\"
by Boehm, Hans-J; Atkinson, Russ; and Plass, Michael (December 1995), doi:10.1002/spe.4380251203."
  (:refer-clojure :exclude [cat])
  (:require [clojure.zip :as zip]
            [clojure.string :as str])
  (:import [clojure.lang Counted Indexed IDeref ILookup IPersistentCollection]
           [java.lang String IndexOutOfBoundsException]
           [java.io File RandomAccessFile]))

(def fib-seq (take 92 (map first (iterate (fn [[a b]] [b (+ a b)]) [0 1]))))
(def ^:dynamic *leaf-cutoff-length* 64) ; This value has been determined optimal for split operations through benchmarking
(def ^:private cpus (.. Runtime getRuntime availableProcessors))

(defn split-lines
  "Like clojure.string/split-lines, but doesn't mess up trailing empty lines"
  [s]
  (seq (.split #"\n" s -1)))

(defprotocol Measurable
  "Trivial protocol for things measurable

Default implementations are provided for nil and string; nil measures as 0
characters, 0 lines. strings get analyzed accordingly."
  (measure [object] "Measure object"))

(defn measurable?
  "Does x satisfy Measurable?"
  [x]
  (satisfies? Measurable x))

(defrecord CharSequenceMeasurement [#^long length #^long lines])

(defmethod print-method CharSequenceMeasurement [weight writer]
  (print-simple (format "#<CharSequenceMeasurement %d/%d>" (:length weight) (:lines weight)) writer))

(defn measurement
  "CharSequenceMeasurement from length and lines

 (measurement) is 0/0"
  ([] (CharSequenceMeasurement. 0 0))
  ([length lines] (CharSequenceMeasurement. length lines)))

(defn measure+
  "Add up weights of measurements"
  [& xs]
  (->> xs (map vals) (reduce #(map + %1 %2)) (apply measurement)))

(defn count-matches
  "Number of character matches in string"
  [char string]
  (->> string (filter #(= char %)) count))

(defn measure-string
  "CharSequenceMeasurement of number of characters and newlines in string"
  [string]
  (measurement (count string) (count-matches \newline string)))

(extend-type nil
  Measurable
  (measure [object] (measurement 0 0)))

(extend-type String
  Measurable
  (measure [string] (measure-string string)))


(defprotocol Treeish-2-3
  "Protocol describing 2-3-Trees, such as ropes

  Default implementations for nil and string exist."
  (split [tree pred] "Split tree by pred")
  (conc [tree1 tree2] "Concatenate tree1 and tree2")
  (left [tree] "Left child of tree")
  (right [tree] "Right child of tree")
  (depth [tree] "Depth of tree")
  (leaf? [tree] "Is tree a leaf?"))

(defn- merge-2-3
  "Merge two objects from a 2-3-tree perspective"
  [obj1 obj2]
  (let [count1 (count obj1) count2 (count obj2)]
    (cond (zero? (+ count1 count2)) nil
          (zero? count1) obj2
          (zero? count2) obj1
          :default (conc obj1 obj2))))

(defn treeish-2-3?
  "Does x satisfy Treeish-2-3?"
  [x]
  (satisfies? Treeish-2-3 x))

(defn weigh
  "The accumulated measurement of all children to the right

  Provided that Treeish-2-3 is implemented for seq.
  In other words: What a new Treeish-2-3 node would weigh with seq as its left child."
  [seq]
  {:pre [(measurable? seq)]}
  (if (and (treeish-2-3? seq) (right seq)) ; no child on the right? not required to weigh again
    (->> seq (iterate right) (take-while identity) (map measure) (apply measure+))
    (measure seq)))

(defmulti cat
  "Concatenation of two ropes, including strings which are treated as leafes"
  (fn [rope1 rope2] [(type rope1) (type rope2)]))

(extend-type nil
  Treeish-2-3
  (split [_ _] nil)
  (conc [_ obj] obj)
  (left [_] nil)
  (right [_] nil)
  (depth [_] 0)
  (leaf? [_] true))

(extend-type String
  Treeish-2-3
  (split [_ _] nil)
  (conc [this coll] (cat this coll))
  (left [_] nil)
  (right [_] nil)
  (depth [_] 0)
  (leaf? [_] true))


(defprotocol Ropey
  "Rope-specific protocol for things that don't fit into general 2-3-Trees."
  (balanced? [rope] "Is rope balanced?"))

; necessary forward declaration
(deftype Rope [#^CharSequence left #^CharSequence right #^CharSequenceMeasurement weight #^int level])

(defn rope?
  "Is x a rope?"
  [x]
  (instance? Rope x))

(defn rope
  "New rope that concatenates the given arguments, if any"
  ([] (Rope. nil nil (measurement 0 0) 0))
  ([seq] (Rope. seq nil (weigh seq) (inc (depth seq))))
  ([seq1 seq2] (Rope. seq1 seq2 (weigh seq1) (inc (max (depth seq1) (depth seq2))))))

(defn- rooted
  "Evaluate to properly rooted rope without a right child, if necessary"
  [root]
  (if (and (rope? root) (nil? (right root)))
    root
    (rope root)))

(defn rope-zip
  "Creates Zipper structures for ropes, starting from root"
  [root]
  (zip/zipper rope?
              (fn [node] (filter identity [(left node) (right node)]))
              (fn [node children] (apply rope children))
              root))

(defn- rope-nth-node
  "Vector of zip node and rest index of rope at index"
  [root index]
  (loop [zipper (rope-zip root) l-index index]
    (if zipper
      (let [node (zip/node zipper)]
        (if (leaf? node)
          [zipper l-index]
          (let [weight (-> node measure :length) next (zip/down zipper)]
            (if (<= weight l-index)
              (recur (zip/right next) (- l-index weight))
              (recur next l-index)))))
      (throw (IndexOutOfBoundsException. (str "Rope index out of range: " index))))))

(defn rope-nth
  "Evaluates to character at position index in the rope at root in O(log n) time"
  [root index]
  (let [[zipper index] (rope-nth-node root index)]
    (nth (zip/node zipper) index)))

(defn rope-seq
  "Lazy sequence of all rope nodes at root, depth-first order"
  [root]
  (for [node (iterate zip/next (rope-zip root)) :while (not (zip/end? node))]
    (zip/node node)))

(defn rope-reverse-seq
  "Lazy reverse sequence of all rope nodes from the end of root, depth-first order"
  [root]
  (let [[end _] (rope-nth-node root (dec (count root)))]
    (for [node (iterate zip/prev end) :while (zip/up node)]
      (zip/node node))))

(defn index-of
  "The position of the first match in rope

  Or nil, if none or not applicable.
  f is passed each leaf node and should evaluate to either an integer, if
  matching should stop and the total position returned by taking the callback
  result into account, or boolean true if index-of should continue, and lastly
  nil, to abort matching and have index-of evaluate to nil altogether."
  [root f]
  (loop [leafes (filter leaf? (rope-seq root)) acc ""]
    (when-let [leaf (first leafes)]
      (let [next (next leafes) string (str acc leaf) index (f string (nil? next))]
        (cond (number? index) index
              (true? index) (recur next string)
              :default nil)))))

(defn index-of-char
  "The position of the first matching char in rope

  Resembles string's indexOf."
  [root char]
  (index-of root (fn [s _] (let [result (.indexOf s (str char))]
                             (or (= -1 result) result)))))

(defn last-index-of
  "The position of the last match in rope

  Or nil, if none or not applicable.
  f is passed each leaf node and should evaluate to either an integer, if
  matching should stop and the total position returned by taking the callback
  result into account, or boolean true if last-index-of should continue, and
  lastly nil, to abort matching and have index-of evaluate to nil altogether."
  [root f]
  (loop [leafes (filter leaf? (rope-reverse-seq root)) acc ""]
    (when-let [leaf (first leafes)]
      (let [next (next leafes) string (str leaf acc) index (f string (nil? next))]
        (cond (number? index) (+ index (- (count root) (count string)))
              (true? index) (recur next string)
              :default nil)))))

(defn last-index-of-char
  "The position of the last matching char in rope

  Resembles string's lastIndexOf."
  [root char]
  (last-index-of root (fn [s _] (let [result (.lastIndexOf s (str char))]
                                  (or (= -1 result) result)))))

(defn rope-split-at
  "Split rope at index

  The additional remaining string index will be the fourth vector element"
  [root index]
  (if (= (count root) index)
    [root "" (rope) 0]
    (let [[left-rope string right-rope _] (split root (fn [weight _] (>= index (:length weight))))
          position (- index (count left-rope))]
      [left-rope (or string "") right-rope position])))

(defn rope-partition
  "Partition for ropes

  Each list element is comprised of the left rope and rest string of a split operation."
  [n coll]
  (lazy-seq
   (cond (zero? (count coll)) nil
         (< (count coll) n) (cons [coll] nil)
         :default
         (let [[left-rope string right-rope _] (rope-split-at coll n)]
           (cons [left-rope string] (rope-partition n right-rope))))))

(defn rope->string
  "String from rope"
  [root]
  (let [build #(apply str (->> (rope-seq %) (filter leaf?)))
        step (-> (count root) (/ cpus) Math/ceil long (max 1))]
    (->> root (rope-partition step) (pmap (fn [[r s]] (str (build r) s))) (apply str))))

(defn rebalance
  "Rebalanced copy of the rope provided

  Using the algorithm described in \"Ropes: an Alternative to Strings\".
  The implementation does not resemble the one from the paper, but is instead
  idiomatic Clojure and optimized for functional languages in general."
  [root]
  (let [fold (fn [coll] (reduce rope (reverse coll)))
        fib-border (fn [limit] (->> fib-seq (take-while #(>= limit %)) last))]
    (loop [acc () coll (->> (rope-seq root) (filter string?)) max-length nil]
      (let [current (first coll) length (count current)]
        (if current
          (let [rest (next coll)]
            (if (and (seq acc) (>= length max-length))
              (let [[low high] (split-with #(>= length (fib-border (count %))) acc)]
                (recur high (conj rest (rope (fold low) current)) (fib-border (count (first high)))))
              (recur (cons current acc) rest (fib-border length))))
          (fold acc))))))

(defn- maybe-rebalance
  "Rebalance `r`, if necessary"
  [r]
  (if (balanced? r) r (rebalance r)))

(defmethod cat :default [coll1 coll2]
  (maybe-rebalance (rope coll1 coll2)))

(defn string->rope
  "Rope from string

  The resulting leafes will carry at most *leaf-cutoff-length* characters of the
  input string."
  [string]
  (letfn [(build [acc]
            (if (> (count acc) 1)
              (recur (->> (partition-all 2 acc) (map #(apply rope %))))
              (first acc)))]
    (->> (partition-all (-> (count string) (/ cpus) long (max 1)) string)
         (pmap (bound-fn [chunk]
                 (->> (partition-all *leaf-cutoff-length* chunk) (map #(apply str %)) build)))
         build rope)))

(defmethod cat [String String] [string1 string2]
  (let [string (str string1 string2)]
    (if (> (count string) *leaf-cutoff-length*)
     (string->rope string)
     string)))

(defn- reassemble [coll]
  (if (empty? coll)
    (rope)
    (->> coll (reduce conc) rope)))

(defn rope-split
  "Split rope at root by calling pred against the accumulated weights and nodes traversed so far

  Pred should evaluate to a truthy value whenever the weight or node should be
  traversed to the right, i.e. towards higher weights.
  The vector returned is composed of the re-evaluated tree to the left of the
  split, the actual split leaf, a reassembled tree of the nodes cut off to the
  right of the traversal and the measurement traversed, i.e. the weight of the
  left tree plus the split leaf."
  [root pred]
  (loop [acc () zipper (rope-zip (if (zero? (count root)) (rope "") root))
         total-weight (measurement)]
    (let [node (zip/node zipper) node-weight (measure node) new-weight (measure+ node-weight total-weight)]
      (if (leaf? node)
        (if (pred new-weight node)
          (throw (IndexOutOfBoundsException. (str "Node index out of range: " (pr-str new-weight))))
          [(-> zipper zip/remove zip/root) node (reassemble acc) new-weight])
        (let [left (zip/down zipper) right (zip/right left)]
          (cond (nil? right) (recur acc left total-weight)
                (pred new-weight node) (recur acc right new-weight)
                :default
                (recur (cons (zip/node right) acc)
                  (-> right (zip/replace nil) zip/left)
                  total-weight)))))))

;; Presumably faster than iterating till zip/end?
(defn conjoin
  "conj for ropes

  x is concatenated to the depth-first end, i.e. rightmost node of the rope.
  Additional xs will be concatenated first."
  ([root x]
   {:pre [(rope? root)]}
   (loop [zipper (rope-zip root)]
     (let [left-child (zip/down zipper)
           right-child (zip/right left-child)]
       (cond (and right-child (zip/node right-child)) (recur right-child)
             (and left-child (zip/node left-child)) (recur left-child)
             :default (-> zipper (zip/edit #(merge-2-3 %1 %2) x) zip/root)))))
  ([root x & xs]
   (conjoin root (reduce merge-2-3 x xs))))

(defn lconjoin
  "Left conjoin

  Merge x on the left of the very first leaf node."
  [root x]
  {:pre [(rope? root)]}
  (loop [zipper (rope-zip root)]
    (let [left-child (zip/down zipper)]
      (if (and left-child (zip/node left-child))
        (recur left-child)
        (-> zipper (zip/edit #(merge-2-3 %2 %1) x) zip/root)))))

(defn rope-split-at-line
  "Split rope at line

  The resulting vector consists of the left rope, lines left of the target line,
  then the same for everything right of the line."
  [root index]
  (let [[left-rope string right-rope _] (split root (fn [weight _] (> index (:lines weight))))
        position (- index (:lines (weigh left-rope)))
        [left-lines [line & right-lines]] (split-at position (split-lines (or string "")))]
    [left-rope left-lines line right-lines right-rope]))

(defn split-merge
  "Split rope at index and merge back node chunks"
  [root index]
  {:pre [(rope? root)]}
  (let [[l s r p] (rope-split-at root index)
        ls (subs s 0 p)
        rs (subs s p)]
    [(conjoin l ls) (lconjoin r rs)]))

(defn translate
  "Translate between rope measurements"
  ([root line column]
   {:pre [(>= line 0) (>= column 0)]}
   (if (> line (-> root weigh :lines))
     (throw (IndexOutOfBoundsException. (str "Line index out of range: " line)))
     (let [[left-rope left-lines line right-lines right-rope] (rope-split-at-line root line)
           offset (+ (count left-rope) (count (apply concat left-lines)) (count left-lines) column)
           delta (- column (count line))]
       (if (and (pos? delta) (empty? right-lines)
             (not (and (>= (count right-rope) delta)
                    (zero? (first (translate right-rope delta))))))
         (throw (IndexOutOfBoundsException. (str "String index out of range: " column)))
         offset))))
  ([root index]
   {:pre [(>= index 0)]}
   (if (> index (count root))
     (throw (IndexOutOfBoundsException. (str "String index out of range: " index)))
     (let [[left-rope leaf _ rest] (rope-split-at root index)
           lines (split-lines (subs leaf 0 rest))
           rope-weight (-> left-rope weigh :lines)
           weight (dec (count lines))]
       (if (zero? weight)
         (let [[_ _ line _ right-rope] (rope-split-at-line left-rope rope-weight)]
           [rope-weight (+ (count line) (count right-rope) (-> lines first count))])
         [(+ rope-weight weight) (-> lines last count)])))))

(defn insert
  "Insert string in rope at `index`"
  [root index string]
  (maybe-rebalance
    (rooted
      (let [[left-rope target right-rope position] (rope-split-at root index)]
        (merge-2-3 (conjoin left-rope (subs target 0 position) string (subs target position))
          right-rope)))))

(defn insert-2d
  "Insert string in rope at `[line column]`"
  [root pos string]
  (insert root (apply translate root pos) string))

(defn report
  "Report in O(log n) time for ropes, the equivalent of string subs"
  ([root start]
     (if (= start (count root))
       "" ; For consistency with "normal" subs
       (let [[_ string right-rope position] (rope-split-at root start)]
         (conc (subs string position) right-rope))))
  ([root start end]
     {:pre [(>= (- end start) 0)]}
     (let [length (count root)]
      (cond (= start length) ""
            (> end length) (throw (IndexOutOfBoundsException. (str "Rope index out of range: " end)))
            (= end length) (report root start)
            :default
            (let [[_ part1 right-rope position1] (rope-split-at root start)
                  length (- end start)
                  rest (- (count part1) position1)]
              (cond (= length (+ rest (count right-rope))) (conc (subs part1 position1) right-rope)
                    (> length rest)
                    (let [[middle-rope part2 _ position2] (rope-split-at right-rope (- length rest))]
                      (conc (subs part1 position1) (conjoin middle-rope (subs part2 0 position2))))
                    :default (subs part1 position1 (+ position1 length))))))))

(defn report-2d
  "Report based on two-dimensional positions"
  [root start end]
  (let [start' (apply translate root start)
        end' (apply translate root end)]
    (cond
      (> start' end') (throw (IndexOutOfBoundsException. "End must not be smaller than start"))
      (= start' end') ""
      :default (report root start' end'))))

(defn line
  "Line at `index` as string"
  [root index]
  (if (<= (-> root weigh :lines) index)
    (report root (translate root index 0))
    (report root (translate root index 0)
      (dec (translate root (inc index) 0)))))

(defn delete
  "Delete the characters at interval [start..end] in the rope"
  [root start end]
  {:pre [(>= (- end start) 0)]}
  (cond
    (= start end)
    root

    (= end (count root))
    (if (= start 0)
      (rope)
      (let [[left-rope part _ position] (rope-split-at root start)
            snippet (subs part 0 position)]
        (rooted (merge-2-3 left-rope snippet))))

    :default
    (let [[left-rope part1 _ position1] (rope-split-at root start)
          [_ part2 right-rope position2] (rope-split-at root end)
          new-left (conjoin left-rope (subs part1 0 position1) (subs part2 position2))]
      (rooted (merge-2-3 new-left right-rope)))))

(defn delete-2d
  "Delete the characters at 2D interval [start..end] in the rope"
  [root start end]
  (let [start' (apply translate root start)
        end' (apply translate root end)]
    (cond
      (> start' end') (throw (IndexOutOfBoundsException. "End must not be smaller than start"))
      (= start' end') root
      :default  (delete root start' end'))))

(defn delete-line
  "Delete the line at `index` in the rope"
  [root index]
  (delete root index 0 (inc index) 0))

(deftype Rope [#^CharSequence left #^CharSequence right #^CharSequenceMeasurement weight #^int level]
  Measurable
  (measure [_] weight)

  Treeish-2-3
  (split [this pred] (rope-split this pred))
  (conc [this tree] (cat this tree))
  (left [_] left)
  (right [_] right)
  (depth [_] level)
  (leaf? [_] false)

  Ropey
  (balanced? [this]
    (or (< level 3)
      (let [level (+ level 2)]
        (and (< level (count fib-seq))
          (>= (count this) (nth fib-seq level))))))

  Counted
  (count [this] (:length (weigh this)))

  Indexed
  (nth [this index] (rope-nth this index))
  (nth [this index not-found]
    (try (rope-nth this index)
         (catch Exception _ not-found)))

  CharSequence
  (charAt [this index] (nth this index))
  (length [this] (count this))
  (subSequence [this start end] (report this start end))
  (toString [this] (rope->string this))

  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key not-found]
    (if (number? key)
      (try (nth this key)
           (catch Exception _ not-found))
      not-found))

  IPersistentCollection
  (empty [_] (rope))
  (equiv [this o]
    (cond
     (string? o) (= (str this) o)
     (rope? o) (and (= left (.left o))
                    (= right (.right o)))
     :default false)))

(defmethod print-method Rope [rope writer]
  (print-simple (format "#<Rope %s>" (->> (measure rope) vals (str/join "/"))) writer))

; Must be defined here, else the dispatch on type/class will fail
(defmethod cat [Rope String] [coll string]
  (let [right-child (right coll)]
    (if (and (= String (type right-child))
             (<= (+ (count right-child) (count string)) *leaf-cutoff-length*))
      (rope (left coll) (str right-child string))
      (cat coll (rope string)))))

(deftype Bud [reader #^CharSequenceMeasurement weight]
  Measurable
  (measure [_] weight)

  IDeref
  (deref [_] (reader))

  Treeish-2-3
  (split [_ _] nil)
  (conc [this coll] (cat this coll))
  (left [_] nil)
  (right [_] nil)
  (depth [_] 0)
  (leaf? [_] true)

  Counted
  (count [this] (:length (weigh this)))

  Indexed
  (nth [this index] (nth (reader) index))
  (nth [this index not-found]
    (try (nth (reader) index)
         (catch Exception _ not-found)))

  CharSequence
  (charAt [this index] (nth (reader) index))
  (length [this] (count this))
  (subSequence [this start end] (subs (reader) start end))
  (toString [this] (reader))

  ILookup
  (valAt [this key] (.valAt this key nil))
  (valAt [this key not-found]
    (if (number? key)
      (try (nth this key)
           (catch Exception _ not-found))
      not-found)))

(defmethod print-method Bud [bud writer]
  (print-simple (format "#<Bud %s>" (->> (measure bud) vals (str/join "/"))) writer))

(defn file-chunk
  "Bud that reads from file"
  [file pos len]
  (let [readfn (fn []
                 (with-open [file (RandomAccessFile. file "r")]
                   (let [data (byte-array len)]
                     (doto file (.seek pos) (.read data 0 len))
                     (String. data))))]
    (Bud. readfn (measure (readfn)))))

(defn- span [start end step]
  (let [coll (->> (iterate (fn [[a b]] [(inc b) (+ (inc b) (- b a))]) [start (dec (+ start step))])
                  (take-while #(< (second %) end)))]
    (concat (butlast coll) [[(-> coll last first) (dec end)]])))

(defn file->rope
  "Rope from file"
  [path]
  (let [file (File. path)
        size (.length file)
        step (-> size (/ cpus) Math/ceil long (max 1))]
    (letfn [(build [acc]
              (if (> (count acc) 1)
                (recur (->> (partition-all 2 acc) (map #(apply rope %))))
                (first acc)))]
      (->> (span 0 size step)
           (pmap (bound-fn [[pos limit]]
                   (->> (span pos (inc limit) *leaf-cutoff-length*)
                        (map (fn [[start end]] (file-chunk file start (inc (- end start))))) build)))
           build rope))))
