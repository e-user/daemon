(ns daemon.keymap
  (:refer-clojure :exclude [newline])
  (:require [taoensso.timbre :as timbre]
            [clojure.set :as set]
            [data.rope :as rope]))

(timbre/refer-timbre)

(def modifiers #{:ctrl :alt :shift :meta})

(defn insert [buffer [x y] s]
  (-> buffer
    (update :state #(rope/insert % x y s))
    (update :ops #(conj % {:op :insert :data [[x y] s]}))))

(defn delete [buffer [x1 y1] [x2 y2]]
  (-> buffer
    (update :state #(rope/delete % x1 y1 x2 y2))
    (update :ops #(conj % {:op :delete :data [[x1 y1] [x2 y2]]}))))

(defn move-cursor [buffer [x y]]
  (-> buffer
    (assoc :cursor [x y])
    (update :ops #(conj % {:op :move-cursor :data [x y]}))))

(defn forward-char
  [{:keys [state] [x y] :cursor :as buffer}]
  (let [pos (rope/translate state x y)]
    (if (< pos (count state))
      (move-cursor buffer (rope/translate state (inc pos)))
      buffer)))

(defn backward-char
  [{:keys [state] [x y] :cursor :as buffer}]
  (let [pos (rope/translate state x y)]
    (if (> pos 0)
      (move-cursor buffer (rope/translate state (dec pos)))
      buffer)))

(defn next-line
  [{:keys [state] [x y] :cursor :as buffer}]
  (if (< x (:lines (rope/weigh state)))
    (move-cursor buffer
      (let [s (rope/line state (inc x))]
        [(inc x) (min y (count s))]))
    buffer))

(defn previous-line
  [{:keys [state] [x y] :cursor :as buffer}]
  (if (> x 0)
    (move-cursor buffer
      (let [s (rope/line state (dec x))]
        [(dec x) (min y (count s))]))
    buffer))

(defn insert-char [{:keys [cursor] :as buffer} c]
  (-> buffer (insert cursor (str c)) forward-char))

(defn newline [buffer]
  (insert-char buffer \newline))

(defn delete-char
  [{:keys [state] [x y] :cursor :as buffer}]  
  (delete buffer [x y] (rope/translate state (inc (rope/translate state x y)))))

(defn delete-backward-char
  [{:keys [state] [x y] :cursor :as buffer}]  
  (let [pos (rope/translate state x y)]
    (if (> pos 0)
      (-> buffer
        backward-char
        (delete (rope/translate state (dec pos)) [x y]))
      buffer)))

(defn move-beginning-of-line
  [{[x _] :cursor :as buffer}]
  (move-cursor buffer [x 0]))

(defn move-end-of-line
  [{:keys [state] [x _] :cursor :as buffer}]
  (move-cursor buffer [x (count (rope/line state x))]))

(defn kill-line
  [{:keys [state] [x y] :cursor :as buffer}]
  (let [end (count (rope/line state x))]
    (delete buffer [x y]
      (if (> (- end y) 0) [x end] [(inc x) 0]))))

(defmulti dispatch (fn [buffer keys] keys))

(defmethod dispatch :default [buffer keys]
  (let [mods (set/intersection keys modifiers)
        key (first (set/difference keys modifiers))]
    (when key
      (if (empty? (set/difference mods #{:shift}))
        (insert-char buffer key)
        (error (format "Undefined key combination: %s" keys))))))

(defmethod dispatch #{:ctrl \f} [buffer _] (forward-char buffer))
(defmethod dispatch #{:ctrl \b} [buffer _] (backward-char buffer))
(defmethod dispatch #{:ctrl \n} [buffer _] (next-line buffer))
(defmethod dispatch #{:ctrl \p} [buffer _] (previous-line buffer))
(defmethod dispatch #{:enter} [buffer _] (newline buffer))
(defmethod dispatch #{:ctrl \d} [buffer _] (delete-char buffer))
(defmethod dispatch #{:backspace} [buffer _] (delete-backward-char buffer))
(defmethod dispatch #{:ctrl \a} [buffer _] (move-beginning-of-line buffer))
(defmethod dispatch #{:ctrl \e} [buffer _] (move-end-of-line buffer))
(defmethod dispatch #{:ctrl \k} [buffer _] (kill-line buffer))
(defmethod dispatch #{:tab} [buffer _] (insert-char buffer \tab))
