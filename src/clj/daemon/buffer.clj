(ns daemon.buffer
  (:require [daemon.event :as event]
            [daemon.socket :as socket]
            [data.rope :as rope]
            [daemon.keymap :as keymap]
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
    (fn [buffer] ; TODO keymap
      (let [{:keys [ops] :as result} (keymap/dispatch (assoc buffer :cursor pos :ops []) input)]
        (if result
          (do1 (update (select-keys result [:history :state :keymap]) :history concat ops)
            (socket/broadcast :edit-buffer {:id id :ops ops}))
          buffer)))))

(defn append!
  [id s]
  (let [agent (@buffers id)]
    (send agent
      (fn [{:keys [state] :as buffer}]
        (let [pos (rope/translate state (count state))]
          (do1 (update buffer :state #(rope/cat % s))
            (socket/broadcast :edit-buffer {:id id :ops [{:op :insert :data [pos s]}]})))))))

(intern 'daemon.log 'log! (fn log! [s] (append! "*Messages*" (str "\n" s))))

(defmethod event/handle "buffer-state" [{:keys [id]} _]
  (-> (@buffers id) deref :state str))
