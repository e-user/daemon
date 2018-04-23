(ns daemon.buffer
  (:refer-clojure :exclude [newline])
  (:require [daemon.event :as event]
            [daemon.socket :as socket]
            [data.rope :as rope]
            [daemon.keymap :as keymap]
            daemon.keymap.default
            [taoensso.timbre :as timbre]
            [daemon.util :refer [do1]]))

(timbre/refer-timbre)

;; Keep it super simple, for now

(def buffers (atom {}))

(defn buffer
  ([s keymap] (agent {:history [] :state (rope/rope s) :keymap keymap}
                :error-handler (fn [_ e] (error e))))
  ([s] (buffer s :default)))

(defn update! [f & args]
  (swap! buffers #(apply f % args)))

(defn create-buffer!
  ([name s]
   (update! assoc name (buffer s)))
  ([name] (create-buffer! name "")))

(create-buffer! "*Messages*")
(create-buffer! "*scratch*" "This is the scratch buffer\n")

(defn input [id pos input]
  (send (@buffers id)
    (fn [buffer]
      (let [{:keys [ops] :as result} (keymap/dispatch (assoc buffer :cursor pos :ops []) input)]
        (if result
          (do1 (update (select-keys result [:history :state :keymap]) :history concat ops)
            (socket/broadcast :edit-buffer {:id id :ops ops}))
          buffer)))))

(defn append!
  [id s]
  (send (@buffers id)
    (fn [{:keys [state] :as buffer}]
      (let [pos (rope/translate state (count state))]
        (do1 (update buffer :state #(rope/cat % s))
          (socket/broadcast :edit-buffer {:id id :ops [{:op :insert :data [pos s]}]}))))))

(intern 'daemon.log 'log! (fn log! [s] (append! "*Messages*" (str "\n" s))))

(defmethod event/handle "buffer-state" [{:keys [id]} _]
  (-> (@buffers id) deref :state str))

;;; Buffer operations

(defn insert [buffer pos s]
  (-> buffer
    (update :state #(rope/insert-2d % pos s))
    (update :ops #(conj % {:op :insert :data [pos s]}))))

(defn delete [buffer from to]
  (-> buffer
    (update :state #(rope/delete-2d % from to))
    (update :ops #(conj % {:op :delete :data [from to]}))))

(defn move-cursor [buffer pos]
  (-> buffer
    (assoc :cursor pos)
    (update :ops #(conj % {:op :move-cursor :data pos}))))

(defn forward-char
  [{:keys [state cursor] :as buffer}]
  (let [pos (apply rope/translate state cursor)]
    (if (< pos (count state))
      (move-cursor buffer (rope/translate state (inc pos)))
      buffer)))

(defn backward-char
  [{:keys [state cursor] :as buffer}]
  (let [pos (apply rope/translate state cursor)]
    (if (> pos 0)
      (move-cursor buffer (rope/translate state (dec pos)))
      buffer)))

(defn next-line
  [{:keys [state] [line column] :cursor :as buffer}]
  (if (< line (:lines (rope/weigh state)))
    (move-cursor buffer
      (let [s (rope/line state (inc line))]
        [(inc line) (min column (count s))]))
    buffer))

(defn previous-line
  [{:keys [state] [line column] :cursor :as buffer}]
  (if (> line 0)
    (move-cursor buffer
      (let [s (rope/line state (dec line))]
        [(dec line) (min column (count s))]))
    buffer))

(defn insert-char [{:keys [cursor] :as buffer} c]
  (-> buffer (insert cursor (str c)) forward-char))

(defn newline [buffer]
  (insert-char buffer \newline))

(defn delete-char
  [{:keys [state cursor] :as buffer}]
  (delete buffer cursor (rope/translate state (inc (apply rope/translate state cursor)))))

(defn delete-backward-char
  [{:keys [state cursor] :as buffer}]
  (let [pos (apply rope/translate state cursor)]
    (if (> pos 0)
      (-> buffer
        backward-char
        (delete (rope/translate state (dec pos)) cursor))
      buffer)))

(defn move-beginning-of-line
  [{[line _] :cursor :as buffer}]
  (move-cursor buffer [line 0]))

(defn move-end-of-line
  [{:keys [state] [line _] :cursor :as buffer}]
  (move-cursor buffer [line (count (rope/line state line))]))

(defn kill-line
  [{:keys [state] [line column] :cursor :as buffer}]
  (let [end (count (rope/line state line))]
    (delete buffer [line column]
      (if (> (- end column) 0) [line end] [(inc line) 0]))))
