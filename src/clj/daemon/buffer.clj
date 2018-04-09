(ns daemon.buffer
  (:require [daemon.event :as event]
            [daemon.socket :as socket]
            [data.rope :as rope]))

;; Keep it super simple, for now

(def buffers (atom {}))

(defn buffer [s] (ref {:history [] :state (rope/rope s)}))

(defn update! [f & args]
  (swap! buffers #(apply f % args)))

(defn create-buffer!
  ([name s]
   (update! assoc name (buffer s)))
  ([name] (create-buffer! name "")))

(create-buffer! "*Messages*")
(create-buffer! "*scratch*" "This is the scratch buffer\n")

(defn insert!
  [id [x y] s]
  (let [b (@buffers id)]
    (dosync (alter b update :state #(rope/insert % x y s)))
    (socket/broadcast :edit-buffer {:id id :op :insert :data {:pos [x y] :string s}})))

(def broken (atom nil))

(defn append!
  [id s]
  (dosync
    (let [b (-> (@buffers id) deref :state)]
      (insert! id (rope/translate b (count b)) s))))

(intern 'daemon.log 'log! (fn log! [s] (append! "*Messages*" (str "\n" s))))

(defmethod event/handle "buffer-state" [{:keys [id]} _]
  (str (get-in @buffers [id :state])))
