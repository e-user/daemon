(ns daemon.buffer
  (:require [daemon.event :as event]
            [daemon.socket :as socket]
            [data.rope :as rope]))

;; Keep it super simple, for now

(def buffers (atom {}))

(defn update! [f & args]
  (swap! buffers #(apply f % args)))

(defn create-buffer!
  ([name s]
   (update! assoc name {:history [] :state (rope/rope s)}))
  ([name] (create-buffer! name "")))

(create-buffer! "*Messages*")
(create-buffer! "*scratch*" "This is the scratch buffer\n")

(defn insert!
  [id [x y] s]
  (update! update-in [id :state] #(rope/insert % x y s))
  (socket/broadcast :edit-buffer {:id id :op :insert :data {:pos [x y] :string s}}))

(defn append!
  [id s]
  (let [buffer (get-in @buffers [id :state])]
    (insert! id (rope/translate buffer (count buffer)) s)))

(intern 'daemon.log 'log! (fn log! [s] (append! "*Messages*" (str "\n" s))))

(defmethod event/handle "buffer-state" [{:keys [id]} _]
  (str (get-in @buffers [id :state])))

(daemon.log/log! "foo")
