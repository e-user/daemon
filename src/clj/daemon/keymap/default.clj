(ns daemon.keymap.default
  (:require [taoensso.timbre :as timbre]
            [clojure.set :as set]
            [daemon.keymap :as keymap]
            [daemon.buffer :as buffer]))

(timbre/refer-timbre)

(defmulti dispatch (fn [buffer keys] keys))
(defmethod keymap/dispatch :default [buffer keys] (dispatch buffer keys))

(defmethod dispatch :default [buffer keys]
  (let [mods (set/intersection keys keymap/modifiers)
        key (first (set/difference keys keymap/modifiers))]
    (when key
      (if (empty? (set/difference mods #{:shift}))
        (buffer/insert-char buffer key)
        (error (format "Undefined key combination: %s" keys))))))

(defmethod dispatch #{:ctrl \f} [buffer _] (buffer/forward-char buffer))
(defmethod dispatch #{:ctrl \b} [buffer _] (buffer/backward-char buffer))
(defmethod dispatch #{:ctrl \n} [buffer _] (buffer/next-line buffer))
(defmethod dispatch #{:ctrl \p} [buffer _] (buffer/previous-line buffer))
(defmethod dispatch #{:enter} [buffer _] (buffer/newline buffer))
(defmethod dispatch #{:ctrl \d} [buffer _] (buffer/delete-char buffer))
(defmethod dispatch #{:backspace} [buffer _] (buffer/delete-backward-char buffer))
(defmethod dispatch #{:ctrl \a} [buffer _] (buffer/move-beginning-of-line buffer))
(defmethod dispatch #{:ctrl \e} [buffer _] (buffer/move-end-of-line buffer))
(defmethod dispatch #{:ctrl \k} [buffer _] (buffer/kill-line buffer))
(defmethod dispatch #{:tab} [buffer _] (buffer/insert-char buffer \tab))
