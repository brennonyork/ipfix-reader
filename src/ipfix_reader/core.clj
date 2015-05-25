(ns ipfix-reader.core
  (:require [ipfix-reader parser])
  (:gen-class))

(defn -main
  "Takes an ipfix-formatted file and stream out the necessary items"
  [ipfix-file & rest]
  ;; work around dangerous default behaviour in Clojure
  (alter-var-root #'*read-eval* (constantly false))
  (ipfix-reader.parser/parse-ipfix ipfix-file))
