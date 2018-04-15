(ns daemon.buffer
  (:require [daemon.event :as event]
            [daemon.socket :as socket]
            [data.rope :as rope]))

;; Keep it super simple, for now

(def buffers (atom {}))

(defn buffer [s] (agent {:history [] :state (rope/rope s)}))

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
  (let [agent (@buffers id)]
    (send agent
      (fn [buffer]
        (socket/broadcast :edit-buffer {:id id :op :insert :data {:pos [x y] :string s}})
        (update buffer :state #(rope/insert % x y s))))))

(def broken (atom nil))
(def exception (atom nil))

(defn append!
  [id s]
  (let [agent (@buffers id)]
    (send agent
      (fn [{:keys [state] :as buffer}]
        (try
          (let [[x y] (rope/translate state (count state))]
            (socket/broadcast :edit-buffer {:id id :op :insert :data {:pos [x y] :string s}})
            (update buffer :state #(rope/insert % x y s)))
          (catch Exception e
            (println "FUCK")
            (reset! exception e)
            (reset! broken buffer)))))))

(intern 'daemon.log 'log! (fn log! [s] (append! "*Messages*" (str "\n" s))))

(defmethod event/handle "buffer-state" [{:keys [id]} _]
  (-> (@buffers id) deref :state str))
