(ns daemon.buffer
  (:require [daemon.event :as event]))

;; Keep it super simple, for now

(def buffers (atom {}))

(defn update! [f & args]
  (swap! buffers #(apply f % args)))

(defn create-buffer!
  ([name s]
   (update! assoc name {:history [] :state s}))
  ([name] (create-buffer! name "")))

(defn buffer [name]
  (@buffers name))

(create-buffer! "Messages")

(defn append! [name s]
  (update! update-in [name :state] #(format "%s\n%s" %1 %2) s)
  )

(defn log! [s]
  (append! "Messages" s))

(defmethod event/handle "read-buffer" [{:keys [id]} _]
  {:buffer (get-in @buffers [id :state] "")})
