(ns daemon.util)

(defmacro do1 [expr & body]
  `(let [val# ~expr]
     (do ~@body)
     val#))
